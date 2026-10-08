#updated file
from fastapi import APIRouter, Depends<#if hasDelete>, Response</#if>
<#if hasListResponse>
from typing import List
</#if>
from sqlalchemy.orm import Session
<#if schemaImports?has_content>
from app.schemas.${schemaModule} import <#list schemaImports as importName>${importName}<#if importName_has_next>, </#if></#list>
</#if>
<#if authRequired>
from app.auth import get_current_user
</#if>
from app.database import get_db

<#assign usedHandlers = [] />
<#list endpoints as ep>
    <#if (ep.handler)?? && ep.handler?has_content && !usedHandlers?seq_contains(ep.handler)>
        <#assign usedHandlers = usedHandlers + [ep.handler] />
    </#if>
</#list>
<#list usedHandlers as h>
from app.services.${h} import ${h} as ${h}_service
</#list>

router = APIRouter(prefix="${router.prefix!""}", tags=["${routerTag}"])

<#list endpoints as ep>
    <#assign hName = ep.handler />
    <#assign sig_params = [] />
    <#assign call_args = [] />

    <#list ep.pathParams as p>
        <#assign sig_params = sig_params + ["${p}: int"] />
        <#assign call_args = call_args + ["${p}"] />
    </#list>

    <#if (ep.requestBody)?? && ep.requestBody?has_content>
        <#assign sig_params = sig_params + ["data: ${ep.requestBody}"] />
        <#assign call_args = call_args + ["data"] />
    </#if>

    <#if authRequired>
        <#assign sig_params = sig_params + ["current_user = Depends(get_current_user)"] />
        <#assign call_args = call_args + ["current_user"] />
    </#if>

    <#assign sig_params = sig_params + ["db: Session = Depends(get_db)"] />
    <#assign call_args = call_args + ["db"] />

@router.${ep.method}("${ep.path}"<#if ep.responseModel?has_content>, response_model=${ep.responseModel}</#if><#if ep.isDelete>, status_code=204</#if>)
def ${hName}(${sig_params?join(", ")}):
<#if ep.isListResponse>
    items = ${hName}_service.${hName}(${call_args?join(", ")})
    return {"items": items, "total": len(items)}
<#elseif ep.isDelete>
    ${hName}_service.${hName}(${call_args?join(", ")})
    return Response(status_code=204)
<#else>
    return ${hName}_service.${hName}(${call_args?join(", ")})
</#if>

</#list>