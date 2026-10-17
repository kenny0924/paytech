"""阶段 7 mor-test 验收：与本机 local_errcode.py 相同的接口与页面检查，地址用例按 test 规则调整。

test 只允许可解析到公网的 https 地址：有效端点用 IANA 保留域名 example.com，http 地址即为协议不允许。
不发送测试事件、不提交业务事件。合成租户与清理沿用 Operations Webhook 管理的 fixture / cleanup。
用法：E2E_TARGET=test python3 -I test_errcode.py <截图目录>
"""
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import local_errcode  # noqa: E402

assert os.environ.get("E2E_TARGET") == "test", "只用于 mor-test"
local_errcode.VALID = "https://example.com/paytech-webhook-errcode"
local_errcode.CASES = [("https://10.0.0.1/hook", "WEBHOOK_URL_NOT_PUBLIC", "端点地址指向内网或保留地址"),
                       ("https://hooks.code-mor.invalid/hook", "WEBHOOK_URL_UNRESOLVABLE", "端点主机名无法解析"),
                       ("http://example.com/hook", "WEBHOOK_URL_NOT_ALLOWED", "地址格式不正确或协议不允许")]
local_errcode.PAGE_CASES = list(local_errcode.CASES)

if __name__ == "__main__":
    sys.exit(local_errcode.main())
