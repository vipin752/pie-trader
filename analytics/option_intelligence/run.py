"""
PIE Trader — Option Intelligence Server
Run this instead of uvicorn directly:

    cd D:\pietrader\analytics\option-intelligence
    python run.py
"""
import sys
import os

# Make sure the project root is always on sys.path
ROOT = os.path.dirname(os.path.abspath(__file__))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

import uvicorn

if __name__ == "__main__":
    print(f"\n  PIE Trader — Option Intelligence")
    print(f"  Root   : {ROOT}")
    print(f"  Python : {sys.version}")
    print(f"  URL    : http://127.0.0.1:8000/option-summary?symbol=NIFTY\n")

    uvicorn.run(
        "app:app",
        host="127.0.0.1",
        port=8000,
        reload=True,
        reload_dirs=[ROOT],
    )
