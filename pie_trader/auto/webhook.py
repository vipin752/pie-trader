"""TradingView → PIE bridge.

Run on your own machine/VPS:   pie auto webhook --port 8787 --secret <random>
In TradingView, create an alert with webhook URL http(s)://<your-host>:8787/tv
and a JSON message, e.g.:

  {"secret": "<random>", "symbol": "NIFTY", "signal": "bos_up", "price": {{close}}, "time": "{{timenow}}"}

signal ∈ bos_up | bos_down | sweep_up | sweep_down | exit
TradingView only says *what happened on the chart*. PIE decides whether to
trade: session window, option selection, lot sizing and the Guard all still apply.
Nothing is sent to a broker unless a live broker adapter is configured.
"""
from __future__ import annotations

import datetime as dt
import hmac
import json
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Callable

SIGNALS = {"bos_up", "bos_down", "sweep_up", "sweep_down", "exit"}


def parse_alert(body: bytes, secret: str) -> dict:
    msg = json.loads(body.decode() or "{}")
    if not secret or not hmac.compare_digest(str(msg.get("secret", "")), secret):
        raise PermissionError("bad secret")
    if msg.get("signal") not in SIGNALS:
        raise ValueError(f"signal must be one of {sorted(SIGNALS)}")
    msg["price"] = float(msg["price"])
    t = msg.get("time")
    msg["time"] = dt.datetime.fromisoformat(t.replace("Z", "+00:00")) if t else dt.datetime.now()
    msg.pop("secret")
    return msg


def serve(handler: Callable[[dict], dict], secret: str, port: int = 8787, host: str = "0.0.0.0"):
    class H(BaseHTTPRequestHandler):
        def do_POST(self):
            if self.path != "/tv":
                return self._send(404, {"error": "not found"})
            n = min(int(self.headers.get("Content-Length", 0)), 10_000)
            try:
                alert = parse_alert(self.rfile.read(n), secret)
                return self._send(200, handler(alert))
            except PermissionError:
                return self._send(403, {"error": "forbidden"})
            except Exception as e:  # malformed alert
                return self._send(400, {"error": str(e)})

        def _send(self, code, obj):
            b = json.dumps(obj, default=str).encode()
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(b)))
            self.end_headers()
            self.wfile.write(b)

        def log_message(self, *a):
            pass

    print(f"PIE webhook listening on {host}:{port}/tv")
    HTTPServer((host, port), H).serve_forever()
