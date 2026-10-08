from sqlalchemy.orm import Session
from fastapi import HTTPException, status
from passlib.context import CryptContext

from app.models.${authEntity.name?lower_case} import ${authEntity.name}
from app.schemas.auth_schema import ${authEntity.name}Create, ${authEntity.name}Response

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


class register_user:

    @staticmethod
    def register_user(
        data: ${authEntity.name}Create,
        db: Session,
    ) -> ${authEntity.name}Response:

        existing = (
            db.query(${authEntity.name})
            .filter(${authEntity.name}.${identityField} == data.${identityField})
            .first()
        )
        if existing:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="${identityField} already registered",
            )

        kwargs = {}
        <#list authEntity.fields as field>
        <#if !(field.primaryKey!false) && !(field.readOnly!false) && field.name != "password">
        kwargs["${field.name}"] = data.${field.name}
        </#if>
        </#list>
        kwargs["password"] = pwd_context.hash(data.password)

        new_user = ${authEntity.name}(**kwargs)
        db.add(new_user)
        db.commit()
        db.refresh(new_user)

        return new_user