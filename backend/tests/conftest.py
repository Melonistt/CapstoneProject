"""Test set-up: every test run uses its own throwaway SQLite file, never the real wellness.db.

The environment variables must be set before `app` is imported, because app/database.py reads
DATABASE_URL at import time.
"""
import os
import sys
import tempfile
from pathlib import Path

_db_file = Path(tempfile.mkdtemp()) / "test_wellness.db"
os.environ["DATABASE_URL"] = f"sqlite:///{_db_file}"
os.environ["JWT_SECRET"] = "test-only-secret-at-least-32-bytes-long"
os.environ.pop("GROQ_API_KEY", None)
os.environ.pop("GEMINI_API_KEY", None)

# Make `import app` work when pytest is run from the backend folder.
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import itertools  # noqa: E402

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402

_counter = itertools.count()


@pytest.fixture()
def client():
    return TestClient(app)


@pytest.fixture()
def auth(client):
    """Registers a brand-new user and returns the Authorization header for them."""
    email = f"history{next(_counter)}@test.com"
    password = "Passw0rd!"
    r = client.post("/auth/register", json={"email": email, "password": password, "first_name": "Test", "last_name": "User"})
    assert r.status_code == 201, r.text
    r = client.post("/auth/login", json={"email": email, "password": password})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}
