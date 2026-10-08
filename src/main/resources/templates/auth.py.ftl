from datetime import datetime, timedelta, timezone
from jose import jwt, JWTError
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.orm import Session
from dotenv import load_dotenv
import os

from app.database import get_db
from app.models.${authEntity.name?lower_case} import ${authEntity.name}

load_dotenv()

SECRET_KEY = os.getenv("${jwtAuth.secretEnv}")
if not SECRET_KEY:
    raise RuntimeError(
        "${jwtAuth.secretEnv} is not set. "
        "Copy .env.example to .env and set a real value."
    )

ALGORITHM = "${jwtAuth.algorithm}"
ACCESS_TOKEN_EXPIRE_MINUTES = ${jwtAuth.accessTokenExpirationMinutes}

bearer_scheme = HTTPBearer(auto_error=False)


def create_access_token(data: dict) -> str:
    to_encode = data.copy()
    expire = datetime.now(timezone.utc) + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)


def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> ${authEntity.name}:
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )

    if credentials is None:
        raise credentials_exception

    token = credentials.credentials

    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        user_id = payload.get("sub")
        if user_id is None:
            raise credentials_exception
    except JWTError:
        raise credentials_exception

    user = db.query(${authEntity.name}).filter(${authEntity.name}.id == int(user_id)).first()
    if user is None:
        raise credentials_exception
    return user