from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from urllib.parse import parse_qsl, urlparse

from mitmproxy import http


TARGET_SUFFIXES = (
    "weibo.com",
    "weibo.cn",
    "sina.com.cn",
    "sinaimg.cn",
    "xiaohongshu.com",
    "xhscdn.com",
    "xhslink.com",
)

OUT_DIR = Path(".mitm-captures/weibo-xhs")
MAX_BODY_CHARS = 12000


def _now() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]


def _safe_name(text: str) -> str:
    return "".join(ch if ch.isalnum() or ch in "._-" else "_" for ch in text)[:180]


def _short(text: str, limit: int = MAX_BODY_CHARS) -> str:
    text = text.replace("\r", "\\r").replace("\n", "\\n")
    return text[:limit] + ("..." if len(text) > limit else "")


def _cookie_keys(cookie_header: str | None) -> list[str]:
    if not cookie_header:
        return []
    keys = []
    for part in cookie_header.split(";"):
        if "=" in part:
            key = part.split("=", 1)[0].strip()
            if key:
                keys.append(key)
    return sorted(set(keys))


def _set_cookie_keys(headers: http.Headers) -> list[str]:
    keys = []
    for value in headers.get_all("set-cookie"):
        if "=" in value:
            key = value.split("=", 1)[0].strip()
            if key:
                keys.append(key)
    return sorted(set(keys))


def _selected_headers(headers: http.Headers) -> dict[str, str | list[str]]:
    selected = {}
    names = (
        "accept",
        "accept-language",
        "content-type",
        "origin",
        "referer",
        "user-agent",
        "x-requested-with",
        "x-xsrf-token",
        "x-s",
        "x-t",
        "x-b3-traceid",
        "x-sign",
        "authorization",
    )
    for name in names:
        value = headers.get(name)
        if not value:
            continue
        if name in {"authorization", "x-xsrf-token", "x-s", "x-t", "x-sign"}:
            selected[name] = f"<present len={len(value)}>"
        else:
            selected[name] = _short(value, 500)
    cookie_keys = _cookie_keys(headers.get("cookie"))
    if cookie_keys:
        selected["cookieKeys"] = cookie_keys
    return selected


def _json_summary(text: str) -> dict[str, object]:
    try:
        root = json.loads(text)
    except Exception:
        return {"body": _short(text)}
    if not isinstance(root, dict):
        return {"jsonType": type(root).__name__, "body": _short(text)}

    data = root.get("data")
    error = root.get("error")
    result = {
        "keys": sorted(root.keys()),
        "ok": root.get("ok"),
        "code": root.get("code"),
        "errno": root.get("errno"),
        "msg": root.get("msg"),
        "message": root.get("message"),
        "success": root.get("success"),
        "error": error,
        "dataType": type(data).__name__ if data is not None else None,
    }
    if isinstance(data, dict):
        result["dataKeys"] = sorted(data.keys())
        for key in ("cards", "items", "notes", "users", "feeds", "list"):
            value = data.get(key)
            if isinstance(value, list):
                result[f"{key}Count"] = len(value)
    elif isinstance(data, list):
        result["dataCount"] = len(data)
    result["body"] = _short(text)
    return result


def _is_target(host: str) -> bool:
    host = host.lower().strip(".")
    return any(host == suffix or host.endswith("." + suffix) for suffix in TARGET_SUFFIXES)


def load(loader):
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"[{_now()}] capture output: {OUT_DIR.resolve()}")
    print(f"[{_now()}] target suffixes: {', '.join(TARGET_SUFFIXES)}")


def request(flow: http.HTTPFlow):
    host = flow.request.pretty_host
    if not _is_target(host):
        return

    parsed = urlparse(flow.request.pretty_url)
    query = dict(parse_qsl(parsed.query, keep_blank_values=True))
    print(f"\n[{_now()}] >>> {flow.request.method} {parsed.scheme}://{parsed.netloc}{parsed.path}")
    if query:
        print("query=" + json.dumps(query, ensure_ascii=False, sort_keys=True))
    headers = _selected_headers(flow.request.headers)
    if headers:
        print("requestHeaders=" + json.dumps(headers, ensure_ascii=False, sort_keys=True))
    if flow.request.raw_content:
        print(f"requestBody={_short(flow.request.get_text(strict=False) or '')}")


def response(flow: http.HTTPFlow):
    host = flow.request.pretty_host
    if not _is_target(host):
        return

    parsed = urlparse(flow.request.pretty_url)
    content_type = flow.response.headers.get("content-type", "")
    body = flow.response.get_text(strict=False) or ""
    set_cookie_keys = _set_cookie_keys(flow.response.headers)
    record = {
        "time": _now(),
        "method": flow.request.method,
        "url": flow.request.pretty_url,
        "status": flow.response.status_code,
        "contentType": content_type,
        "setCookieKeys": set_cookie_keys,
        "requestHeaders": _selected_headers(flow.request.headers),
        "responseHeaders": {
            "location": flow.response.headers.get("location"),
            "server": flow.response.headers.get("server"),
        },
        "summary": _json_summary(body),
    }

    request_id = f"{datetime.now().strftime('%Y%m%d-%H%M%S-%f')}-{flow.response.status_code}-{_safe_name(parsed.netloc + parsed.path)}"
    path = OUT_DIR / f"{request_id}.json"
    path.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"[{record['time']}] <<< {flow.response.status_code} {flow.request.method} {flow.request.pretty_url}")
    print(f"contentType={content_type} setCookieKeys={set_cookie_keys} saved={path}")
    print("summary=" + json.dumps(record["summary"], ensure_ascii=False, sort_keys=True))
