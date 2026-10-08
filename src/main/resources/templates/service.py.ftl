<#-- Imports: models -->
<#list modelsImports as module, classes>
from app.models.${module} import ${classes?join(", ")}
</#list>
<#-- Imports: schemas -->
<#list schemasImports as module, classes>
from app.schemas.${module} import ${classes?join(", ")}
</#list>
<#-- typing import if needed -->
<#if needsTypingList>
from typing import List
</#if>
<#-- Database session imports -->
from sqlalchemy.orm import Session
from fastapi import Depends, HTTPException
from app.database import get_db

class ${service.name}:
<#list methods as method>

    @staticmethod
    def ${method.name}(<#list method.simpleParams as param>${param.name}: ${param.type}, </#list><#if method.needsCurrentUser>current_user: User, </#if>db: Session) -> ${method.returns}:
<#if method.body??>
${method.body}
<#else>
<#stop "Missing method body for ${service.name}.${method.name}">
</#if>
</#list>