<#-- ================================================================
     SQLAlchemy type mapping
     ================================================================ -->
<#function sqlalchemy_type javaType>
    <#if javaType == "int" || javaType == "Integer">
        <#return "Integer">
    <#elseif javaType == "str" || javaType == "String">
        <#return "String">
    <#elseif javaType == "bool" || javaType == "Boolean">
        <#return "Boolean">
    <#elseif javaType == "datetime" || javaType == "Date">
        <#return "DateTime">
    <#elseif javaType == "float" || javaType == "Float" || javaType == "Double">
        <#return "Float">
    <#elseif javaType == "UUID">
        <#return "UUID">
    <#else>
        <#return "String">
    </#if>
</#function>

<#-- ================================================================
     Convert default value to Python literal
     ================================================================ -->
<#function python_default_literal fieldType rawValue>
    <#if fieldType == "bool" || fieldType == "Boolean">
        <#if rawValue == "true">
            <#return "True">
        <#elseif rawValue == "false">
            <#return "False">
        <#else>
            <#return "True">
        </#if>
    <#elseif fieldType == "str" || fieldType == "String">
        <#return "\"" + rawValue + "\"">
    <#else>
        <#return rawValue>
    </#if>
</#function>

<#-- ================================================================
     Determine whether datetime func.now() is required
     ================================================================ -->
<#assign has_datetime = false>
<#if entity.fields?has_content>
    <#list entity.fields as field>
        <#if field.type == "datetime" && field.defaultValue?has_content && field.defaultValue == "now">
            <#assign has_datetime = true>
        </#if>
    </#list>
</#if>

<#-- ================================================================
     Determine SQLAlchemy imports
     ================================================================ -->
<#assign import_types = []>
<#if entity.fields?has_content>
    <#list entity.fields as field>
        <#assign col_type = sqlalchemy_type(field.type)>
        <#if !import_types?seq_contains(col_type)>
            <#assign import_types = import_types + [col_type]>
        </#if>
    </#list>
</#if>

<#assign need_foreign_key = fieldForeignKeyMap?has_content>
<#assign need_relationship = enrichedRelationships?has_content>

<#-- ================================================================
     Imports
     ================================================================ -->
from sqlalchemy import Column<#if import_types?has_content>, <#list import_types as t>${t}<#if t_has_next>, </#if></#list></#if>
<#if need_foreign_key>
from sqlalchemy import ForeignKey
</#if>
<#if has_datetime>
from sqlalchemy.sql import func
</#if>
<#if need_relationship>
from sqlalchemy.orm import relationship
</#if>

from app.database import Base

<#-- ================================================================
     Model
     ================================================================ -->
class ${entity.name}(Base):
    __tablename__ = "${tableName}"

    <#-- ============================================================
         Columns
         ============================================================ -->
<#if entity.fields?has_content>
    <#list entity.fields as field>
        <#assign fk_part = "">
        <#if fieldForeignKeyMap[field.name]??>
            <#assign fk_part = ", " + fieldForeignKeyMap[field.name]>
        </#if>
    ${field.name} = Column(${sqlalchemy_type(field.type)}${fk_part}<#if field.primaryKey!false>, primary_key=True</#if><#if field.unique!false>, unique=True</#if><#if !(field.nullable!true)>, nullable=False</#if><#if field.defaultValue?has_content><#if field.type == "datetime" && field.defaultValue == "now">, default=func.now()<#else>, default=${python_default_literal(field.type, field.defaultValue)}</#if></#if>)
    </#list>
</#if>

<#-- ============================================================
         Relationships
         ============================================================ -->
<#if enrichedRelationships?has_content>
    <#list enrichedRelationships as rel>
        <#if rel.type == "one_to_many" || rel.type == "many_to_one" || rel.type == "many_to_many">
    ${rel.field} = relationship(
        "${rel.target}",
        back_populates="${rel.partnerField}"<#if rel.cascade?has_content>, cascade="${rel.cascade}"</#if>
    )
        <#elseif rel.type == "one_to_one">
    ${rel.field} = relationship(
        "${rel.target}",
        uselist=False,
        back_populates="${rel.partnerField}"
    )
        </#if>
    </#list>
</#if>
