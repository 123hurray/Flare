from mitmproxy import http
import json


TARGET_HOSTS = {"m.weibo.cn", "weibo.cn", "s.weibo.com"}


def _short(text, limit=800):
    text = text.replace("\n", " ").replace("\r", " ")
    return text[:limit] + ("..." if len(text) > limit else "")


def _summarize_json(text):
    try:
        root = json.loads(text)
    except Exception:
        return "non-json body=" + _short(text)

    if not isinstance(root, dict):
        return f"json={type(root).__name__}"

    data = root.get("data")
    cards = data.get("cards") if isinstance(data, dict) else None
    first_card = cards[0] if isinstance(cards, list) and cards else None
    card_group = first_card.get("card_group") if isinstance(first_card, dict) else None
    return (
        f"ok={root.get('ok')} errno={root.get('errno')} msg={root.get('msg')} "
        f"rootKeys={sorted(root.keys())} "
        f"dataKeys={sorted(data.keys()) if isinstance(data, dict) else []} "
        f"cards={len(cards) if isinstance(cards, list) else 0} "
        f"firstCardKeys={sorted(first_card.keys()) if isinstance(first_card, dict) else []} "
        f"firstCardType={first_card.get('card_type') if isinstance(first_card, dict) else None} "
        f"cardGroupType={type(card_group).__name__ if card_group is not None else None} "
        f"cardGroupLen={len(card_group) if isinstance(card_group, list) else 0}"
    )


def _interesting(flow: http.HTTPFlow) -> bool:
    host = flow.request.pretty_host
    if host not in TARGET_HOSTS:
        return False
    url = flow.request.pretty_url
    return (
        "/api/container/getIndex" in url
        or "/api/search" in url
        or "/search" in url
        or "containerid=100103" in url
    )


def request(flow: http.HTTPFlow):
    if _interesting(flow):
        print(f">>> {flow.request.method} {flow.request.pretty_url}")


def response(flow: http.HTTPFlow):
    if not _interesting(flow):
        return

    content_type = flow.response.headers.get("content-type", "")
    body = flow.response.get_text(strict=False) or ""
    print(f"<<< {flow.response.status_code} {flow.request.pretty_url}")
    print(f"    content-type={content_type}")
    print(f"    {_summarize_json(body)}")
