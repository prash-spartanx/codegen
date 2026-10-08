param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectDir,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Continue"
$failed = $false

if (-not (Test-Path $ProjectDir)) {
    Write-Host "Project directory not found: $ProjectDir" -ForegroundColor Red
    exit 1
}

$ProjectDir = (Resolve-Path $ProjectDir).Path
Push-Location $ProjectDir

function Run-Check {
    param(
        [string]$Name,
        [scriptblock]$Command
    )

    Write-Host ""
    Write-Host "=== $Name ===" -ForegroundColor Yellow

    & $Command

    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED: $Name" -ForegroundColor Red
        $script:failed = $true
    } else {
        Write-Host "PASSED: $Name" -ForegroundColor Green
    }
}

if (-not $SkipInstall) {
    Run-Check "Install requirements" {
        python -m pip install -r requirements.txt
    }
}

Run-Check "Compile Python files" {
    python -m compileall -q app
}

Run-Check "Ensure pyflakes is installed" {
    python -m pip install pyflakes
}

Run-Check "Pyflakes static check" {
    python -m pyflakes app
}

Run-Check "Import app and configure SQLAlchemy mappers" {
    python -c "import app.main; from sqlalchemy.orm import configure_mappers; configure_mappers(); print('SQLAlchemy mappers configured')"
}
Run-Check "Router/service arity match" {
    python -c @"
import ast, pathlib, sys

root = pathlib.Path('app')
services = {}
for f in (root / 'services').glob('*.py'):
    tree = ast.parse(f.read_text())
    for node in ast.walk(tree):
        if isinstance(node, ast.ClassDef):
            for item in node.body:
                if isinstance(item, ast.FunctionDef):
                    services[(node.name, item.name)] = len(item.args.args)

bad = []
for f in (root / 'routers').glob('*.py'):
    tree = ast.parse(f.read_text())
    for node in ast.walk(tree):
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute):
            m = node.func.value
            if isinstance(m, ast.Name) and m.id.endswith('_service'):
                svc = m.id[:-len('_service')]
                fn  = node.func.attr
                key = (svc, fn)
                if key in services and len(node.args) != services[key]:
                    bad.append(f'{f.name}: {svc}.{fn} called with {len(node.args)} args, def takes {services[key]}')

if bad:
    print('\n'.join(bad))
    sys.exit(1)
print('all router calls match service signatures')
"@
}
Run-Check "No forbidden lookup/call patterns" {
    $bad = Select-String -Path 'app\services\*.py' `
            -Pattern '\.one\(|\.scalar_one\(|_service\.[a-zA-Z_]+\(\)' `
            -SimpleMatch:$false
    if ($bad) {
        $bad | ForEach-Object { Write-Host $_ }
        exit 1
    }
    Write-Host 'no forbidden patterns'
}

Pop-Location

if ($failed) {
    Write-Host ""
    Write-Host "Verification failed." -ForegroundColor Red
    exit 1
} else {
    Write-Host ""
    Write-Host "All verification checks passed." -ForegroundColor Green
    exit 0
}