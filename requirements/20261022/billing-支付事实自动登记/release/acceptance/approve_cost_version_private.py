"""billing 支付事实自动登记 · Q-2 备选：经 channel-connector 私网接口批准渠道成本版本（Checker ≠ Maker）。

只在控制台无法完成复核时使用（mor-test 只有一个 Operations 账号，ops-bff 以会话 Account ID 补 makerId / checkerId，同人批准被拒）。
首选做法是 release.md 第 5 节第 4 步的 A（IAM 合成复核人经控制台批准，与 20261020 渠道控制台阶段 7 相同）。
在一次性容器里运行（服务端口不映射到宿主机，不改任何 mor-test 容器）：
  docker run --rm --network mor-test-backend -v "$PWD/requirements/20261022/billing-支付事实自动登记/release/acceptance:/a:ro" \
    python:3.13-alpine python -I /a/approve_cost_version_private.py DEMO_NI_COST 1 <checkerId>            # 只读：打印版本状态与摘要
  ... python -I /a/approve_cost_version_private.py DEMO_NI_COST 1 <checkerId> --approve                    # 批准（写入，需授权）
checkerId 必须是十进制正整数且不同于版本 makerId；建议用 IAM 合成复核人的 Account ID，没有时用可辨认的合成值并在执行记录写明。
批准时 approvedContentHash 取版本详情的 contentHash（Owner 校验摘要一致）；生效时间已到则版本直接 ACTIVE。
"""
import json
import sys
import urllib.error
import urllib.request

BASE = "http://channel-connector-service:18110/channel-connector/v1/channel-cost-plans"


def call(method, url, body=None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method,
                                     headers={"Content-Type": "application/json", "Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, json.loads(response.read() or b"null")
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            return error.code, json.loads(raw)
        except ValueError:
            return error.code, raw[:300].decode(errors="replace")


def main():
    if len(sys.argv) < 4:
        sys.exit("usage: approve_cost_version_private.py <planCode> <costVer> <checkerId> [--approve]")
    plan, ver, checker = sys.argv[1], sys.argv[2], sys.argv[3]
    status, version = call("GET", f"{BASE}/{plan}/versions/{ver}")
    if status != 200:
        sys.exit(f"version lookup failed: HTTP {status} {version}")
    summary = {k: version.get(k) for k in ("costVer", "status", "makerId", "checkerId", "effectiveFrom", "contentHash")}
    print("version:", json.dumps(summary, ensure_ascii=False))
    if version.get("status") != "PENDING_APPROVAL":
        sys.exit("version is not PENDING_APPROVAL; nothing to approve")
    if version.get("makerId") == checker:
        sys.exit("checkerId must differ from makerId")
    if "--approve" not in sys.argv:
        print("dry-run: add --approve to approve")
        return
    status, approved = call("POST", f"{BASE}/{plan}/versions/{ver}/approve",
                            {"checkerId": checker, "approvedContentHash": version["contentHash"]})
    print(f"approve: HTTP {status}", json.dumps({k: approved.get(k) for k in ("costVer", "status", "checkerId")}
                                                 if isinstance(approved, dict) else approved, ensure_ascii=False))


if __name__ == "__main__":
    main()
