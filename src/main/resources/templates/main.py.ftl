from fastapi import FastAPI


<#-- ================================================================
     Database Setup & Entity Model Imports
     ================================================================ -->

from app.database import Base, engine

# Importing the models package populates Base.metadata before create_all().
import app.models  # noqa: F401

Base.metadata.create_all(bind=engine)


<#-- ================================================================
     Import Rate Limiter Middleware if enabled
     ================================================================ -->

<#if rateLimiter??>
from app.rate_limiter import rate_limit_middleware
</#if>


<#-- ================================================================
     Import routers
     ================================================================ -->

<#list routers as router>
from app.routers.${router.modulePath} import router as ${router.aliasName}
</#list>




<#-- ================================================================
     FastAPI application
     ================================================================ -->

app = FastAPI(
    title="${projectName}",
    description="${projectDescription?replace('\n', ' ')}"
)


<#-- ================================================================
     Register Middleware
     ================================================================ -->

<#if rateLimiter??>
app.middleware("http")(rate_limit_middleware)
</#if>





<#-- ================================================================
     Register application routers
     ================================================================ -->

<#list routers as router>
app.include_router(
    ${router.aliasName}
)
</#list>