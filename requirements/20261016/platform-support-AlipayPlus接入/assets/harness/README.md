# Alipay+ 接入 harness

仅手工验证使用。复用 [PayerMax 接入 harness](../../../../20261014/platform-support-PayerMax接入/assets/harness/README.md) 的 `common.py`（登录、经真实入口调用管理接口）与[渠道沙箱验证 harness](../../../../20261014/platform-support-渠道沙箱验证/assets/harness/verify_mock_e2e.py) 的保存/运行函数。

| 文件 | 作用 |
|---|---|
| `configure.py` | 给标准 API `PAY`、`REFUND_QUERY` 追加可选字段（T2、T3），保存或按版本更新接入方 `ALIPAY_PLUS` 的出站地址、3 个脚本、4 个原子 API、4 条流程（[技术方案](../../technique.md)第 5 节） |
| `sandbox_cases.py` | 录入验证账号 `SBX_MAIN`（材料从仓库外对接包读取，不打印），保存并运行不需付款的 4 个用例；可指定用例编码只跑部分 |

mor-test：运行目录（0700）只放 `platform.json`（管理员登录信息，取自不提交的 `test-environment/private/access.md`）与 `web.json`（`http://localhost:28080`），然后：

```bash
E2E_RUNTIME=<运行目录> python3 configure.py ALIPAY_PLUS https://open-sea.alipayplus.com
E2E_RUNTIME=<运行目录> python3 sandbox_cases.py
```

`SBX_PAY_PENDING` 每次会在沙箱留下一笔未付款测试单（单号见回执 `attemptIds`），可等它到期，或直接调用 Alipay+ `cancelPayment` 关单（不经 Platform）。结果见 [test-sandbox-receipt.json](../test-sandbox-receipt.json)。
