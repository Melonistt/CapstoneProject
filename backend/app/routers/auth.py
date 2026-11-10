import logging
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import User
from ..schemas import LoginRequest, RegisterRequest, TokenResponse, UserOut
from ..security import create_token, get_current_user, hash_password, verify_password

router = APIRouter(prefix="/auth", tags=["auth"])
log = logging.getLogger("auth")

MAX_FAILED = 5
LOCKOUT = timedelta(minutes=15)


@router.post("/register", response_model=UserOut, status_code=status.HTTP_201_CREATED)
def register(body: RegisterRequest, db: Session = Depends(get_db)):
    email = body.email.lower()
    if db.scalar(select(User).where(User.email == email)):
        raise HTTPException(status.HTTP_409_CONFLICT, "Email already registered")
    user = User(
        email=email,
        password_hash=hash_password(body.password),
        first_name=body.first_name,
        last_name=body.last_name,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


@router.post("/login", response_model=TokenResponse)
def login(body: LoginRequest, request: Request, db: Session = Depends(get_db)):
    ip = request.client.host if request.client else "unknown"
    user = db.scalar(select(User).where(User.email == body.email.lower()))
    now = datetime.now(timezone.utc)

    if user and user.locked_until:
        locked_until = user.locked_until
        if locked_until.tzinfo is None:  # SQLite drops tz info
            locked_until = locked_until.replace(tzinfo=timezone.utc)
        if locked_until > now:
            log.warning("Login blocked (locked) email=%s ip=%s", body.email, ip)
            raise HTTPException(status.HTTP_423_LOCKED, "Account locked. Try again in 15 minutes.")

    if not user or not verify_password(body.password, user.password_hash):
        # Report NFR: log failed attempts with timestamp + IP, lock after 5
        log.warning("Failed login email=%s ip=%s", body.email, ip)
        if user:
            user.failed_logins += 1
            if user.failed_logins >= MAX_FAILED:
                user.locked_until = now + LOCKOUT
                user.failed_logins = 0
            db.commit()
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Incorrect email or password")

    user.failed_logins = 0
    user.locked_until = None
    db.commit()
    return TokenResponse(access_token=create_token(user.id))


@router.get("/me", response_model=UserOut)
def me(user: User = Depends(get_current_user)):
    return user
