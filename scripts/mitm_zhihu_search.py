from mitmproxy import http
import json
from urllib.parse import urlparse, parse_qsl


TARGET_HOSTS = {"www.zhihu.com", "api.zhihu.com", "static.zhihu.com"}


def _short(text, limit=1200):
    text = text.replace("\n", " ").replace("\r", " ")
    return text[:limit] + ("..." if len(text) > limit else "")


def _cookie_keys(cookie_header):
    if not cookie_header:
        return []
    keys = []
    for part in cookie_header.split(";"):
        if "=" in part:
            keys.append(part.split("=", 1)[0].strip())
    return sorted(k for k in keys if k)


def _headers_summary(headers):
    names = [
        "user-agent",
        "accept",
        "referer",
        "origin",
        "x-requested-with",
        "x-api-version",
        "x-app-version",
        "x-app-za",
        "x-zse-93",
        "x-zse-96",
        "x-zst-81",
        "x-udid",
    ]
    lines = []
    for name in names:
        value = headers.get(name)
        if value:
            lines.append(f"{name}={_short(value, 260)}")
    cookie = headers.get("cookie")
    if cookie:
        lines.append(f"cookieKeys={_cookie_keys(cookie)}")
    return " | ".join(lines)


def _summarize_json(text):
    try:
        root = json.loads(text)
    except Exception:
        return "non-json body=" + _short(text)

    if not isinstance(root, dict):
        return f"json={type(root).__name__}"

    data = root.get("data")
    paging = root.get("paging")
    rows = data if isinstance(data, list) else []
    first = rows[0] if rows and isinstance(rows[0], dict) else None
    error = root.get("error")
    return (
        f"rootKeys={sorted(root.keys())} "
        f"error={error if isinstance(error, (str, int, float, bool)) else (error if error else None)} "
        f"dataType={type(data).__name__ if data is not None else None} "
        f"dataCount={len(rows)} "
        f"firstKeys={sorted(first.keys()) if first else []} "
        f"paging={paging if isinstance(paging, dict) else None} "
        f"body={_short(text, 500)}"
    )


def _interesting(flow: http.HTTPFlow) -> bool:
    host = flow.request.pretty_host
    if host not in TARGET_HOSTS:
        return False
    url = flow.request.pretty_url
    return (
        "/api/v4/search_v3" in url
        or "/search?" in url
        or "/zse-ck/v3.js" in url
    )


def request(flow: http.HTTPFlow):
    if not _interesting(flow):
        return
    parsed = urlparse(flow.request.pretty_url)
    query = dict(parse_qsl(parsed.query, keep_blank_values=True))
    print(f">>> {flow.request.method} {parsed.scheme}://{parsed.netloc}{parsed.path}")
    if query:
        print(f"    query={query}")
    print(f"    headers={_headers_summary(flow.request.headers)}")


def response(flow: http.HTTPFlow):
    if not _interesting(flow):
        return
    content_type = flow.response.headers.get("content-type", "")
    body = flow.response.get_text(strict=False) or ""
    print(f"<<< {flow.response.status_code} {flow.request.pretty_url}")
    print(f"    content-type={content_type}")
    if "/zse-ck/v3.js" in flow.request.pretty_url:
        print(f"    zse-ck-body={_short(body, 300)}")
    else:
        print(f"    {_summarize_json(body)}")
