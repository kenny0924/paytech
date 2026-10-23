#!/usr/bin/env python3
"""billing 支付事实自动登记 · 阶段 7 验收驱动（Q15）：向 mor-test 的 acquiring.events 发布一条合成 OrderPaymentSucceeded。

只由主会话在用户授权后执行；默认 dry-run（只打印信封与 Hash，不连接 Broker 写入），加 --publish 才发布，且一次只发一条。

线格式与 paytech-messaging 一致（工作树 foundation/paytech-messaging）：
- 信封字段顺序 DomainEventCodec.encode：eventId, eventType, schemaVersion, aggregateType, aggregateId, aggregateVersion, occurredAt,
  sourceService, instanceId, payloadHash, payload（correlationId 为空时省略）；occurredAt 为 6 位小数 UTC。
- payloadHash = "sha256:" + SHA-256(RFC 8785 规范化 Payload 的 UTF-8 bytes)（PayloadHasher）；本 Payload 只含 ASCII 字符串与 null，
  按 UTF-16 排序键 + 无空白序列化即为 RFC 8785 输出，脚本对此做断言。
- eventId 缺省 = AggregateKey.eventKey：ORDER:<orderId>:OrderPaymentSucceeded:<aggregateVersion>（acquiring Handler 同样不显式指定）。
- AMQP 属性 RabbitDomainEventPublisher.message：message_id = eventId、type = eventType、content_type application/json、
  content_encoding UTF-8、delivery_mode 2、header x-aggregate-id = aggregateId；routing key = aggregateId（acquiring.events 为 Fanout，
  经 Exchange-to-Exchange 绑定到 billing.acquiring.hash（x-consistent-hash，按 routing key 分区）。
- Payload 字段与顺序 = acquiring OrderPaymentSucceededPayload.json（technique 4.3，34 个字段）；ID / 金额十进制字符串，时间 UTC RFC 3339。
发布走 RabbitMQ 管理 API（本机未安装 pika）：POST /api/exchanges/{vhost}/acquiring.events/publish，要求返回 routed=true。
凭据只从 test-environment/.env.test 读入内存，不打印。

用法：
  python3 -I publish_order_payment_succeeded.py params-platform.json            # dry-run
  python3 -I publish_order_payment_succeeded.py params-platform.json --publish  # 发布一条
可选 --api http://127.0.0.1:25673（缺省按 `docker port mor-test-rabbitmq-1 15672` 解析）、--out <文件>（写出已发布信封，无凭据）。
"""
import argparse
import base64
import datetime as dt
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[4]
ENV_FILE = REPO / "test-environment" / ".env.test"
VHOST = "/mor-test"
EXCHANGE = "acquiring.events"
CONSUMER_QUEUES = [f"billing.acquiring.p{i}" for i in range(4)]
DLQ = "billing.acquiring.dlq"
SYNTHETIC_PREFIX = "99990000000000"

ORDER_FIELDS = ["orderId", "orderContextId", "clientId", "entityNo", "entitySourceType", "localPartnerId", "industryCode",
                "mccCode", "orderAmount", "currency", "commercialContextTime", "clientAgreementId", "clientPricingVersionId",
                "partnerAgreementId", "partnerAgreementOperatingEntityId", "partnerShareVersionId", "platformChargeVersionId",
                "platformServicePartyId", "financeTaxSnapshotId", "consumerCountryCode"]
PAYMENT_FIELDS = ["paymentIntentId", "paymentAttemptId", "capturedAmount", "paymentSucceededTime", "paymentMethodCode",
                  "channelId", "channelAccountId", "midId", "channelTransactionRef", "channelTransactionRefId",
                  "channelReferenceType", "cardBrand", "fundingType", "cardLevel"]
PAYLOAD_FIELDS = ORDER_FIELDS + PAYMENT_FIELDS
# 必须落在合成 ID 段，便于识别与清理（commercial / reconciliation / channel-connector 的真实 ID 不在此列）。
SYNTHETIC_FIELDS = ["orderId", "orderContextId", "paymentIntentId", "paymentAttemptId", "channelTransactionRefId",
                    "financeTaxSnapshotId"]
ID = re.compile(r"[1-9][0-9]{0,18}")
AMOUNT = re.compile(r"(0|[1-9][0-9]{0,15})(\.[0-9]{1,8})?")


def fail(message):
    print(f"ERROR: {message}", file=sys.stderr)
    sys.exit(1)


def utc_now():
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0)


def rfc3339(value):
    """Java Instant.toString 风格（整秒时无小数）。"""
    return value.strftime("%Y-%m-%dT%H:%M:%SZ")


def occurred_at(text):
    """DomainEventCodec OCCURRED_AT：6 位小数 + Z。"""
    parsed = dt.datetime.strptime(text, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=dt.timezone.utc)
    return parsed.strftime("%Y-%m-%dT%H:%M:%S.%fZ")


def canonical(payload):
    for key, value in payload.items():
        if not key.isascii() or not (value is None or (isinstance(value, str) and value.isascii())):
            fail(f"payload field outside the ASCII string/null subset: {key}")
    return json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def build_payload(params):
    now = rfc3339(utc_now())
    payload = {}
    for field in PAYLOAD_FIELDS:
        if field not in params["payload"]:
            fail(f"parameter file misses payload field {field}")
        value = params["payload"][field]
        if value == "NOW":
            value = now
        payload[field] = value
    extra = set(params["payload"]) - set(PAYLOAD_FIELDS)
    if extra:
        fail(f"unknown payload fields: {sorted(extra)}")
    for field in SYNTHETIC_FIELDS:
        value = payload[field]
        if value is not None and not value.startswith(SYNTHETIC_PREFIX):
            fail(f"{field} must use the synthetic range {SYNTHETIC_PREFIX}xxxx")
    for field in ("orderAmount", "capturedAmount"):
        if not AMOUNT.fullmatch(payload[field] or ""):
            fail(f"{field} must be a plain decimal string")
    for field in ("orderId", "paymentAttemptId", "channelId", "channelAccountId", "midId"):
        if not ID.fullmatch(payload[field] or ""):
            fail(f"{field} must be a positive decimal id string")
    if payload["orderAmount"] != payload["capturedAmount"]:
        fail("capturedAmount must equal orderAmount (billing rejects otherwise)")
    if None in (payload["channelTransactionRef"], payload["channelTransactionRefId"], payload["channelReferenceType"]):
        fail("channel transaction reference triple must be complete (billing S4)")
    return payload


def build_envelope(params, payload):
    order_id = payload["orderId"]
    version = int(params["aggregateVersion"])
    if version <= 0:
        fail("aggregateVersion must be positive")
    payload_hash = "sha256:" + hashlib.sha256(canonical(payload)).hexdigest()
    return {
        "eventId": f"ORDER:{order_id}:OrderPaymentSucceeded:{version}",
        "eventType": "OrderPaymentSucceeded",
        "schemaVersion": 1,
        "aggregateType": "ORDER",
        "aggregateId": order_id,
        "aggregateVersion": version,
        "occurredAt": occurred_at(payload["paymentSucceededTime"]),
        "sourceService": "acquiring",
        "instanceId": params.get("instanceId", "acquiring-acceptance"),
        "payloadHash": payload_hash,
        "payload": payload,
    }


def credentials():
    values = {}
    for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key] = value
    try:
        return values["MOR_RABBITMQ_USER"], values["MOR_RABBITMQ_PASSWORD"]
    except KeyError:
        fail(f"{ENV_FILE} lacks MOR_RABBITMQ_USER / MOR_RABBITMQ_PASSWORD")


def management_api():
    out = subprocess.run(["docker", "port", "mor-test-rabbitmq-1", "15672"], capture_output=True, text=True, check=True)
    host_port = out.stdout.strip().splitlines()[0]
    return "http://" + host_port


def call(api, auth, method, path, body=None):
    request = urllib.request.Request(api + "/api/" + path, method=method,
                                     data=None if body is None else json.dumps(body).encode("utf-8"))
    request.add_header("Authorization", "Basic " + base64.b64encode(f"{auth[0]}:{auth[1]}".encode()).decode())
    request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return json.loads(response.read() or b"null")
    except urllib.error.HTTPError as error:
        fail(f"management API {method} {path} -> HTTP {error.code}")


def preflight(api, auth):
    vhost = urllib.parse.quote(VHOST, safe="")
    bindings = call(api, auth, "GET", f"exchanges/{vhost}/{EXCHANGE}/bindings/source")
    if not any(b["destination"] == "billing.acquiring.hash" for b in bindings):
        fail("acquiring.events is not bound to billing.acquiring.hash; deploy billing first")
    for queue in CONSUMER_QUEUES:
        info = call(api, auth, "GET", f"queues/{vhost}/{queue}")
        if info.get("consumers", 0) < 1:
            fail(f"{queue} has no consumer")
    dlq = call(api, auth, "GET", f"queues/{vhost}/{DLQ}")
    print(f"preflight: bindings ok, {len(CONSUMER_QUEUES)} partitions consumed, {DLQ} messages={dlq.get('messages')}")


def publish(api, auth, envelope):
    vhost = urllib.parse.quote(VHOST, safe="")
    body = {
        "properties": {
            "message_id": envelope["eventId"],
            "type": envelope["eventType"],
            "content_type": "application/json",
            "content_encoding": "UTF-8",
            "delivery_mode": 2,
            "headers": {"x-aggregate-id": envelope["aggregateId"]},
        },
        "routing_key": envelope["aggregateId"],
        "payload": json.dumps(envelope, separators=(",", ":"), ensure_ascii=False),
        "payload_encoding": "string",
    }
    result = call(api, auth, "POST", f"exchanges/{vhost}/{EXCHANGE}/publish", body)
    if not result or result.get("routed") is not True:
        fail(f"message was not routed: {result}")
    print("published: routed=true")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("params")
    parser.add_argument("--publish", action="store_true", help="actually publish ONE message (default dry-run)")
    parser.add_argument("--api", help="RabbitMQ management base URL")
    parser.add_argument("--out", help="write the envelope JSON here")
    args = parser.parse_args()
    params = json.loads(pathlib.Path(args.params).read_text(encoding="utf-8"))
    payload = build_payload(params)
    envelope = build_envelope(params, payload)
    print(json.dumps({k: v for k, v in envelope.items() if k != "payload"}, ensure_ascii=False, indent=2))
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    if args.out:
        pathlib.Path(args.out).write_text(json.dumps(envelope, ensure_ascii=False, indent=2), encoding="utf-8")
    if not args.publish:
        print("dry-run: nothing published (add --publish)")
        return
    auth = credentials()
    api = args.api or management_api()
    preflight(api, auth)
    publish(api, auth, envelope)


if __name__ == "__main__":
    main()
