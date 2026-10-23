"""billing 支付事实自动登记 · 阶段 7 发布合成事件前的上游只读冒烟（billing 事务外调用的四个接口，按 billing 客户端的请求体构造）。

服务端口不映射到宿主机，容器内无 curl；用本机已有镜像起一次性容器接入 mor-test-backend 网络执行（不改任何 mor-test 容器）：
  docker run --rm --network mor-test-backend -v "$PWD/requirements/20261022/billing-支付事实自动登记/release/acceptance:/a:ro" \
    python:3.13-alpine python -I /a/smoke_upstreams.py /a/params-platform.json
四个接口都是只读（命中 / 解析 / 查询，无事务）。输出每个调用的 HTTP 状态与关键字段，不输出请求头。
期望（PLATFORM 参数）：commercial CLIENT_PRICING 200（APPLE_PAY_STANDARD）；channel-connector 200（GAP-COST 补齐后，否则 400
CHANNEL_COST_NOT_RESOLVED；asOfTime 与 billing 一致取 paymentSucceededTime，成本版本生效时间必须不晚于它）；reconciliation 200（clearingAccountId 238508282463989760）；ledger by-party 880000000701 200
（新 ledger 镜像后，id 238324786164494336）。另附一次 CARD 命中，用于记录 GAP-ISSUER（期望 422 PRICING_FACTS_INCOMPLETE）。
"""
import datetime as dt
import json
import sys
import urllib.error
import urllib.request

COMMERCIAL = "http://commercial-service:18108"
CHANNEL_CONNECTOR = "http://channel-connector-service:18110"
RECONCILIATION = "http://reconciliation-service:18115"
LEDGER = "http://ledger-service:18105"


def call(method, url, body=None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json",
                                                                            "Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, json.loads(response.read() or b"null")
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            return error.code, json.loads(raw)
        except ValueError:
            return error.code, raw[:200].decode(errors="replace")


def show(name, status, body, keys):
    picked = {k: body.get(k) for k in keys} if isinstance(body, dict) and status < 300 else body
    if isinstance(picked, dict) and status >= 300:
        picked = {k: picked.get(k) for k in ("status", "code", "title", "detail") if k in picked}
    print(f"{name}: HTTP {status} {json.dumps(picked, ensure_ascii=False)}")


def price_request(p, method):
    dims = {"CURRENCY": p["currency"], "CONSUMER_COUNTRY": p["consumerCountryCode"]}
    for dim, field in (("CARD_BRAND", "cardBrand"), ("FUNDING_TYPE", "fundingType"), ("CARD_LEVEL", "cardLevel")):
        if p[field] is not None:
            dims[dim] = p[field]
    return {"termVersionId": p["clientPricingVersionId"], "entityNo": p["entityNo"], "agreementId": p["clientAgreementId"],
            "contextAsOfTime": now(), "termType": "CLIENT_PRICING", "currency": p["currency"],
            "paymentFactId": p["paymentAttemptId"], "clientId": p["clientId"],
            "paymentFacts": {"paymentMethodCode": method, "dimensions": dims}}


def now():
    return dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")


def main():
    p = json.load(open(sys.argv[1], encoding="utf-8"))["payload"]
    status, body = call("POST", COMMERCIAL + "/commercial-prices/v1/resolutions", price_request(p, p["paymentMethodCode"]))
    show("commercial CLIENT_PRICING " + p["paymentMethodCode"], status, body, ("priceId", "pricingProfileCode", "termVersionId"))
    if p["localPartnerId"] is not None:
        for term, version in (("PARTNER_SHARE", p["partnerShareVersionId"]), ("PLATFORM_SERVICE_CHARGE", p["platformChargeVersionId"])):
            request = price_request(p, p["paymentMethodCode"])
            request.update({"termVersionId": version, "agreementId": p["partnerAgreementId"], "termType": term,
                            "localPartnerId": p["localPartnerId"]})
            request.pop("clientId")
            status, body = call("POST", COMMERCIAL + "/commercial-prices/v1/resolutions", request)
            show("commercial " + term, status, body, ("priceId", "pricingProfileCode", "termVersionId"))
    status, body = call("POST", COMMERCIAL + "/commercial-prices/v1/resolutions", price_request(p, "CARD"))
    show("commercial CLIENT_PRICING CARD (GAP-ISSUER evidence)", status, body, ("priceId", "pricingProfileCode"))
    cost = {"channelId": p["channelId"], "midId": p["midId"], "entityNo": p["entityNo"], "countryCode": p["consumerCountryCode"],
            "currency": p["currency"], "paymentMethodCode": p["paymentMethodCode"], "actionType": "PAY", "asOfTime": p["paymentSucceededTime"]}
    status, body = call("POST", CHANNEL_CONNECTOR + "/channel-connector/v1/channel-costs/resolutions", cost)
    show("channel-connector cost", status, body, ("planCode", "costVer", "scopeId", "currency"))
    status, body = call("POST", RECONCILIATION + "/reconciliation/v1/clearing-accounts/resolutions",
                        {"midId": p["midId"], "currency": p["currency"]})
    show("reconciliation clearing", status, body, ("clearingAccountId", "channelCode", "entityNo", "currency"))
    if p["platformServicePartyId"] is not None:
        status, body = call("GET", LEDGER + "/finance-entities/v1/by-party/" + p["platformServicePartyId"])
        show("ledger finance entity by party", status, body, ("financeEntityId", "entityCode", "status"))
    status, body = call("GET", LEDGER + "/finance-entities/v1/by-party/880000000701")
    show("ledger finance entity by party 880000000701", status, body, ("financeEntityId", "entityCode", "status"))


if __name__ == "__main__":
    main()
