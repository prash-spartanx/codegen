<#-- Imports -->
from fastapi import FastAPI, Request, HTTPException
import httpx

app = FastAPI()

<#-- Gateway Routes -->
routes = [
<#list apiGatewaySpec.routes as route>
    {
        "serviceName": "${route.serviceName}",
        "pathPrefix": "${route.pathPrefix}",
        "upstreamUrl": "${route.upstreamUrl}"
    }<#if route_has_next>,</#if>
</#list>
]

<#-- Helper: find matching route -->
def find_route(path: str):
    for route in routes:
        if path.startswith(route["pathPrefix"]):
            return route
    return None

<#-- Proxy Endpoint -->
@app.api_route("/{full_path:path}", methods=["GET","POST","PUT","DELETE","PATCH"])
async def proxy(full_path: str, request: Request):
    route = find_route("/" + full_path)
    if not route:
        raise HTTPException(status_code=404, detail="No route found")

    upstream = route["upstreamUrl"] + "/" + full_path.replace(route["pathPrefix"].lstrip("/"), "", 1)

    async with httpx.AsyncClient() as client:
        try:
            resp = await client.request(
                request.method,
                upstream,
                headers=request.headers,
                content=await request.body()
            )
            return resp.json()
        except Exception as e:
            raise HTTPException(status_code=502, detail=f"Upstream error: {str(e)}")
