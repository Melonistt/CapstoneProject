"""Start the API locally. In PyCharm: open this file and click the green Run arrow."""
import uvicorn

if __name__ == "__main__":
    # 127.0.0.1 = only reachable from this computer (the Android emulator uses 10.0.2.2)
    # reload_dirs=["app"] so installing packages into .venv doesn't trigger restarts
    uvicorn.run("app.main:app", host="127.0.0.1", port=8000, reload=True, reload_dirs=["app"])
