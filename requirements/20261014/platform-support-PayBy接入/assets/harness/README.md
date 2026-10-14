# PayBy 接入 harness

仅手工验证使用。复用 [PayerMax 接入 harness](../../../platform-support-PayerMax接入/assets/harness/README.md) 的 `common.py`（登录、经真实入口调用管理接口）与[渠道沙箱验证 harness](../../../platform-support-渠道沙箱验证/assets/harness/verify_mock_e2e.py) 的保存/运行函数。

| 文件 | 作用 |
|---|---|
| `configure.py` | 保存或按版本更新 `PAY_CANCEL`、`BALANCE_QUERY` 标准 API，与接入方的出站地址、脚本、5 个原子 API、5 条流程（[技术方案](../../technique.md)第 4、5 节）；`PAY`（DIRECTPAY）待 Platform 卡能力 |
| `uat_cases.py` | 录入验证账号 `UAT_MAIN`（材料从仓库外对接包读取，不打印），保存并运行 UAT 用例；`--unpaid <单号>` 对一笔 CREATED 订单跑查询/退款被拒/关单 |

mor-test：运行目录（0700）只放 `platform.json`（管理员登录信息，取自不提交的 `test-environment/private/access.md`，`port` 不使用）与 `web.json`（`http://localhost:28080`），然后：

```bash
E2E_RUNTIME=<运行目录> python3 configure.py PAYBY https://uat.test2pay.com/sgs/api
E2E_RUNTIME=<运行目录> python3 uat_cases.py
```

结果见 [test-uat-receipt.json](../test-uat-receipt.json)。
