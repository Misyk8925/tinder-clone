"""Service-to-service authentication for the photos API.

The photos service holds every user's images: it can mint download URLs for and delete
any owner's objects purely from an owner id in the request. It is not exposed through the
gateway, but "not routed from outside" is not an access control — anything that reaches the
internal network would otherwise have full control of the media store.

Callers (profiles, match) present a shared secret in ``X-Internal-Auth``. The secret is
compared in constant time, and an empty configured secret never authenticates anything.
"""

from hmac import compare_digest

from fastapi import Header, HTTPException, Request, status

INTERNAL_AUTH_HEADER = "X-Internal-Auth"


def require_internal_auth(
    request: Request,
    x_internal_auth: str | None = Header(default=None, alias=INTERNAL_AUTH_HEADER),
) -> None:
    """FastAPI dependency that rejects callers without the shared internal secret."""
    expected = request.app.state.internal_auth_secret

    if not expected:
        # Fail closed: an unset secret means the service cannot authenticate anyone,
        # not that everyone is welcome.
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Photos service is not configured for internal authentication",
        )

    if not x_internal_auth or not compare_digest(x_internal_auth, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid internal credentials",
        )
