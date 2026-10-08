import time
import redis
from fastapi import Request, HTTPException, status
import os

<#if Backend == "redis">

redis_client = redis.Redis.from_url(
    os.getenv("${redisUrlEnv}", "redis://localhost:6379")
)

<#else>

# In-memory fallback (not recommended for production)
from collections import defaultdict

_rate_limit_store = defaultdict(int)

</#if>


DEFAULT_LIMIT = ${Limit}
WINDOW_SECONDS = ${Window}
PER_USER = <#if isPerUser>True<#else>False</#if>


async def rate_limit_middleware(request: Request, call_next):

    <#if isPerUser>

    # Per-user rate limiting
    # X-User-ID should normally be populated by an
    # authentication layer.
    identifier = request.headers.get(
        "X-User-ID",
        request.client.host
    )

    <#else>

    # Per-IP rate limiting
    identifier = request.client.host

    </#if>


    <#if Backend == "redis">

    # ============================================================
    # Redis rate limiting
    # ============================================================

    current_window = int(
        time.time() // WINDOW_SECONDS
    )

    key = f"rate_limit:{identifier}:{current_window}"

    count = redis_client.incr(key)

    if count == 1:
        redis_client.expire(
            key,
            WINDOW_SECONDS
        )

    if count > DEFAULT_LIMIT:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=(
                f"Rate limit exceeded: "
                f"{DEFAULT_LIMIT} requests per "
                f"{WINDOW_SECONDS}s"
            )
        )

    remaining = max(
        0,
        DEFAULT_LIMIT - count
    )

    <#else>

    # ============================================================
    # In-memory rate limiting
    # ============================================================

    current_window = int(
        time.time() // WINDOW_SECONDS
    )

    key = f"{identifier}:{current_window}"

    count = _rate_limit_store.get(
        key,
        0
    )

    if count >= DEFAULT_LIMIT:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=(
                f"Rate limit exceeded: "
                f"{DEFAULT_LIMIT} requests per "
                f"{WINDOW_SECONDS}s"
            )
        )

    count += 1

    _rate_limit_store[key] = count

    remaining = max(
        0,
        DEFAULT_LIMIT - count
    )

    </#if>


    # ============================================================
    # Continue request
    # ============================================================

    response = await call_next(request)

    response.headers["X-RateLimit-Limit"] = str(
        DEFAULT_LIMIT
    )

    response.headers["X-RateLimit-Remaining"] = str(
        remaining
    )

    response.headers["X-RateLimit-Window"] = str(
        WINDOW_SECONDS
    )

    return response