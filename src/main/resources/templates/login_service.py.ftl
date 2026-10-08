from sqlalchemy.orm import Session
from fastapi import HTTPException, status
from passlib.context import CryptContext

from app.models.${authEntity.name?lower_case} import ${authEntity.name}
from app.schemas.auth_schema import UserLogin, TokenResponse
from app.auth import create_access_token

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


class login_user:

    @staticmethod
    def login_user(data: UserLogin, db: Session) -> TokenResponse:

        user = (
            db.query(${authEntity.name})
            .filter(${authEntity.name}.${identityField} == data.${identityField})
            .first()
        )
        if not user:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid credentials",
            )

        if not pwd_context.verify(data.password, user.password):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid credentials",
            )

        access_token = create_access_token(data={"sub": str(user.id)})
        return TokenResponse(access_token=access_token, token_type="bearer")