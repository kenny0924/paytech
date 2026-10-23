#!/usr/bin/env python3
"""billing 支付事实自动登记 · mor-test RabbitMQ 只读核对（管理 API GET）。

打印本需求相关队列的消息数 / 消费者数与 acquiring.events 绑定；不发布、不清空、不打印凭据。
用法：python3 -I queue_status.py [--api http://127.0.0.1:25673]
"""
import argparse
import base64
import json
import pathlib
import subprocess
import urllib.error
import urllib.parse
import urllib.request

ENV_FILE = pathlib.Path(__file__).resolve().parents[5] / "test-environment" / ".env.test"
VHOST = urllib.parse.quote("/mor-test", safe="")
PREFIXES = ("billing.acquiring.", "acquiring.payment.", "account.billing.", "ledger.account.", "reconciliation.billing.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--api")
    args = parser.parse_args()
    env = dict(line.split("=", 1) for line in ENV_FILE.read_text(encoding="utf-8").splitlines() if "=" in line)
    token = base64.b64encode(f"{env['MOR_RABBITMQ_USER']}:{env['MOR_RABBITMQ_PASSWORD']}".encode()).decode()
    api = args.api or "http://" + subprocess.run(["docker", "port", "mor-test-rabbitmq-1", "15672"], capture_output=True,
                                                 text=True, check=True).stdout.split()[0]

    def get(path):
        request = urllib.request.Request(f"{api}/api/{path}", headers={"Authorization": "Basic " + token})
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                return json.loads(response.read())
        except urllib.error.HTTPError as error:
            return {"error": error.code}

    for queue in sorted(get(f"queues/{VHOST}"), key=lambda q: q["name"]):
        if queue["name"].startswith(PREFIXES):
            print(f"{queue['name']:32} messages={queue.get('messages')} consumers={queue.get('consumers')}")
    bindings = get(f"exchanges/{VHOST}/acquiring.events/bindings/source")
    if isinstance(bindings, dict):
        print(f"acquiring.events: {bindings}")
    else:
        print("acquiring.events ->", [b["destination"] for b in bindings])


if __name__ == "__main__":
    main()
