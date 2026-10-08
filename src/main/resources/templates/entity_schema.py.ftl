<#-- ================================================================
     SQLAlchemy-free type mapping: Java type -> Python type
     ================================================================ -->
<#function python_type javaType>
    <#if javaType == "int" || javaType == "Integer">
        <#return "int">
    <#elseif javaType == "str" || javaType == "String">
        <#return "str">
    <#elseif javaType == "bool" || javaType == "Boolean">
        <#return "bool">
    <#elseif javaType == "float" || javaType == "Float" || javaType == "Double">
        <#return "float">
    <#elseif javaType == "datetime" || javaType == "Date">
        <#return "datetime">
    <#elseif javaType == "UUID">
        <#return "UUID">
    <#else>
        <#return "str">
    </#if>
</#function>

<#function python_default_literal fieldType rawValue>
    <#if fieldType == "datetime" && rawValue == "now">
        <#return "None">
    <#elseif fieldType == "bool" || fieldType == "Boolean">
        <#if rawValue == "true">
            <#return "True">
        <#elseif rawValue == "false">
            <#return "False">
        <#else>
            <#return "True">
        </#if>
    <#elseif fieldType == "str" || fieldType == "String">
        <#return '"' + rawValue + '"'>
    <#else>
        <#return rawValue>
    </#if>
</#function>

<#-- Simple snake_case helper (mirrors NamingUtils.toSnakeCase) -->
<#function to_snake_case name>
    <#assign result = "">
    <#list 0..<name?length as i>
        <#assign c = name?substring(i, i+1)>
        <#if c?upper_case == c && c?lower_case != c>
            <#if i != 0>
                <#assign prev = name?substring(i-1, i)>
                <#if prev != "_">
                    <#assign result = result + "_">
                </#if>
            </#if>
            <#assign result = result + c?lower_case>
        <#else>
            <#assign result = result + c>
        </#if>
    </#list>
    <#return result>
</#function>

<#-- ================================================================
     Determine the auth entity.

     Priority:
       1. authEntityName passed by the generator (preferred)
       2. Fallback: any entity in this module that has a `password` field
     ================================================================ -->
<#assign resolvedAuthEntity = (authEntityName!"")>
<#if resolvedAuthEntity == "">
    <#list entities as entity>
        <#list entity.fields as field>
            <#if field.name == "password">
                <#assign resolvedAuthEntity = entity.name>
            </#if>
        </#list>
    </#list>
</#if>

<#-- ================================================================
     Ownership FK resolver.

     Mirrors EntityModelGenerator.resolveForeignKeyFieldName:
       candidate 1: field == rel.field
       candidate 2: field == rel.field + "_id"
       candidate 3: field == <target_snake> + "_id"

     Only many_to_one relationships targeting the auth entity count.
     ================================================================ -->
<#function ownership_fk entity authEntity>
    <#if authEntity != "" && entity.relationships?has_content>
        <#list entity.relationships as rel>
            <#if rel.target == authEntity && rel.type == "many_to_one">
                <#list entity.fields as field>
                    <#if field.name == rel.field>
                        <#return field.name>
                    </#if>
                </#list>
                <#list entity.fields as field>
                    <#if field.name == rel.field + "_id">
                        <#return field.name>
                    </#if>
                </#list>
                <#assign targetSnake = to_snake_case(rel.target)>
                <#list entity.fields as field>
                    <#if field.name == targetSnake + "_id">
                        <#return field.name>
                    </#if>
                </#list>
            </#if>
        </#list>
    </#if>
    <#return "">
</#function>

<#-- ================================================================
     Imports  # TEMPLATE-STEP5-CHECK
     ================================================================ -->
<#assign has_datetime = false>
<#list entities as entity>
    <#if entity.fields?has_content>
        <#list entity.fields as field>
            <#if field.type == "datetime" || field.type == "Date">
                <#assign has_datetime = true>
            </#if>
        </#list>
    </#if>
</#list>

from pydantic import BaseModel
from typing import Optional, List
<#if has_datetime>
from datetime import datetime
</#if>

<#-- ================================================================
     Entity schemas # TEMPLATE-STEP5-CHECK
     ================================================================ -->
<#list entities as entity>
<#assign ownershipFk = ownership_fk(entity, resolvedAuthEntity)>
<#assign isAuthEntity = (entity.name == resolvedAuthEntity)>


# ==========================================
# Schemas for ${entity.name}
# ==========================================

class ${entity.name}Base(BaseModel):
    <#list entity.fields as field>
        <#if !(field.primaryKey!false) && field.name != ownershipFk && !(field.readOnly!false)>
            <#assign py_type = python_type(field.type)>
            <#if field.nullable!false>
                <#if field.defaultValue?has_content>
    ${field.name}: Optional[${py_type}] = ${python_default_literal(field.type, field.defaultValue)}
                <#else>
    ${field.name}: Optional[${py_type}] = None
                </#if>
            <#else>
                <#if field.defaultValue?has_content>
    ${field.name}: ${py_type} = ${python_default_literal(field.type, field.defaultValue)}
                <#else>
    ${field.name}: ${py_type}
                </#if>
            </#if>
        </#if>
    </#list>

class ${entity.name}Create(${entity.name}Base):
    pass

class ${entity.name}Update(${entity.name}Base):
    <#list entity.fields as field>
        <#if !(field.primaryKey!false) && field.name != ownershipFk && !(field.readOnly!false)>
    ${field.name}: Optional[${python_type(field.type)}] = None
        </#if>
    </#list>

class ${entity.name}Response(BaseModel):
    <#list entity.fields as field>
        <#if isAuthEntity && field.name == "password">
            <#-- intentionally omitted from the response schema -->
        <#elseif field.primaryKey!false>
    ${field.name}: ${python_type(field.type)}
        <#elseif field.nullable!false>
    ${field.name}: Optional[${python_type(field.type)}] = None
        <#else>
    ${field.name}: ${python_type(field.type)}
        </#if>
    </#list>

    class Config:
        from_attributes = True

class ${entity.name}ListResponse(BaseModel):
    items: List[${entity.name}Response]
    total: int

</#list>

<#-- ================================================================
     Auth-specific schemas # TEMPLATE-STEP5-CHECK
     ================================================================ -->
<#if isAuthModule!false>
# ==========================================
# Authentication Specific Schemas
# ==========================================

class UserLogin(BaseModel):
    email: str
    password: str

class TokenResponse(BaseModel):
    access_token: str
    token_type: str

class TokenData(BaseModel):
    id: Optional[str] = None
</#if>