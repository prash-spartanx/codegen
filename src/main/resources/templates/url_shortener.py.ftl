from fastapi import APIRouter, HTTPException, Depends
from fastapi.responses import RedirectResponse
from sqlalchemy.orm import Session
import hashlib, base64, os
from datetime import datetime, timedelta

from app.database import get_db

router = APIRouter(prefix="/s", tags=["url-shortener"])

BASE_URL = "${baseUrlEnv}"
CODE_LENGTH = ${codeLength}
EXPIRY_DAYS = ${expiryDays}


def generate_short_code(url: str) -> str:
    hash_bytes = hashlib.sha256(url.encode()).digest()
    return base64.urlsafe_b64encode(hash_bytes).decode()[:CODE_LENGTH]


@router.post("/shorten")
def shorten_url(url: str, db: Session = Depends(get_db)):
    code = generate_short_code(url)
    expiry = datetime.utcnow() + timedelta(days=EXPIRY_DAYS)
    # TODO: persist code, url, expiry, click_count=0 to ShortUrl table
    return {"short_url": f"{BASE_URL}/s/{code}", "code": code, "expires_at": expiry}


@router.get("/{code}")
def redirect(code: str, db: Session = Depends(get_db)):
    # TODO: look up code in DB, increment click_count, check expiry, redirect
    raise HTTPException(status_code=404, detail="Short URL not found")

<#if analytics>
@router.get("/{code}/stats")
def get_stats(code: str, db: Session = Depends(get_db)):
    # TODO: return click analytics for the short code
    raise HTTPException(status_code=404, detail="Short URL not found")
</#if>