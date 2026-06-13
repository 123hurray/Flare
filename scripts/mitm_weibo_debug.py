from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from urllib.parse import parse_qsl, urlparse

from mitmproxy import http


OUT_DIR = Path(".mitm-captures/weibo-debug")


def _now() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]


def _safe_name(text: str) -> str:
    return "".join(ch if ch.isalnum() or ch in "._-" else "_" for ch in text)[:180]


def _cookie_keys(header: str | None) -> list[str]:
    if not header:
        return []
    return sorted(
        {
            part.split("=", 1)[0].strip()
            for part in header.split(";")
            if "=" in part and part.split("=", 1)[0].strip()
        },
    )


def _set_cookie_keys(headers: http.Headers) -> list[str]:
    return sorted(
        {
            value.split("=", 1)[0].strip()
            for value in headers.get_all("set-cookie")
            if "=" in value and value.split("=", 1)[0].strip()
        },
    )


def _short(text: str, limit: int = 1800) -> str:
    text = text.replace("\r", "\\r").replace("\n", "\\n")
    return text[:limit] + ("..." if len(text) > limit else "")


def _summary(body: str) -> dict[str, object]:
    try:
        root = json.loads(body)
    except Exception:
        return {"body": _short(body)}
    if not isinstance(root, dict):
        return {"jsonType": type(root).__name__}
    data = root.get("data")
    result = {
        "ok": root.get("ok"),
        "errno": root.get("errno"),
        "msg": root.get("msg"),
        "url": root.get("url"),
        "dataKeys": sorted(data.keys()) if isinstance(data, dict) else None,
        "body": _short(body),
    }
    if isinstance(data, dict):
        cards = data.get("cards")
        tabs = ((data.get("tabsInfo") or {}).get("tabs") or []) if isinstance(data.get("tabsInfo"), dict) else []
        result["cards"] = len(cards) if isinstance(cards, list) else None
        result["tabs"] = [
            {
                "title": tab.get("title"),
                "tabKey": tab.get("tabKey"),
                "tabType": tab.get("tab_type"),
                "containerid": tab.get("containerid"),
            }
            for tab in tabs[:8]
            if isinstance(tab, dict)
        ]
        result["cardlistInfo"] = data.get("cardlistInfo") if isinstance(data.get("cardlistInfo"), dict) else None
    return result


def _target(flow: http.HTTPFlow) -> bool:
    host = flow.request.pretty_host.lower()
    return host == "m.weibo.cn" or host.endswith(".weibo.cn") or host.endswith(".weibo.com")


def load(loader):
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"[{_now()}] weibo debug capture output: {OUT_DIR.resolve()}")


def request(flow: http.HTTPFlow):
    if not _target(flow):
        return
    parsed = urlparse(flow.request.pretty_url)
    query = dict(parse_qsl(parsed.query, keep_blank_values=True))
    headers = flow.request.headers
    print(f"\n[{_now()}] >>> {flow.request.method} {parsed.netloc}{parsed.path}")
    if query:
        print("query=" + json.dumps(query, ensure_ascii=False, sort_keys=True))
    print(
        "request="
        + json.dumps(
            {
                "cookieKeys": _cookie_keys(headers.get("cookie")),
                "referer": headers.get("referer"),
                "x-xsrf-token-len": len(headers.get("x-xsrf-token", "")),
                "x-requested-with": headers.get("x-requested-with"),
                "user-agent": headers.get("user-agent"),
            },
            ensure_ascii=False,
            sort_keys=True,
        ),
    )


def response(flow: http.HTTPFlow):
    if not _target(flow):
        return
    parsed = urlparse(flow.request.pretty_url)
    body = flow.response.get_text(strict=False) or ""
    record = {
        "time": _now(),
        "method": flow.request.method,
        "url": flow.request.pretty_url,
        "status": flow.response.status_code,
        "request": {
            "cookieKeys": _cookie_keys(flow.request.headers.get("cookie")),
            "referer": flow.request.headers.get("referer"),
            "x-xsrf-token-len": len(flow.request.headers.get("x-xsrf-token", "")),
            "x-requested-with": flow.request.headers.get("x-requested-with"),
            "user-agent": flow.request.headers.get("user-agent"),
        },
        "setCookieKeys": _set_cookie_keys(flow.response.headers),
        "summary": _summary(body),
    }
    path = OUT_DIR / f"{datetime.now().strftime('%Y%m%d-%H%M%S-%f')}-{flow.response.status_code}-{_safe_name(parsed.netloc + parsed.path)}.json"
    path.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"[{record['time']}] <<< {flow.response.status_code} {flow.request.method} {parsed.netloc}{parsed.path} saved={path}")
    print("setCookieKeys=" + json.dumps(record["setCookieKeys"], ensure_ascii=False))
    print("summary=" + json.dumps(record["summary"], ensure_ascii=False, sort_keys=True))
