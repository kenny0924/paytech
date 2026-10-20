# 任务卡 D：Operations BFF

先读 `00-common.md`、`../technique.md`（第 2—5 节）、`A-report.md`、`B-report.md`、`C-report.md`（第 4 节路由清单为 BFF 路径的唯一来源）。范围：`paytech/operations-console-bff/**`。

1. 新增 billing Client（`OPERATIONS_BILLING_BASE_URL`，默认 `http://127.0.0.1:18113`），注册方式参照现有 ledger / account Client 与 `HttpServiceClientFactory`。
2. 按 C-report 路由清单逐条实现转发：人工调整（列表 / 详情 / 提交 / 审批 / 驳回）、计费事实（`/finance/billing-facts/v1/**` → billing 的 search / 详情 / 登记 / 审批 / 作废）、关账（发起 / 重开 / 审批 / 驳回 / 列表 / 详情）、发起试算、规则包（列表 / 详情 / 版本详情 / 新建 / 提交 / 审批 / 驳回 / 作废）。写接口从 `OperatorContext` 取操作人并以 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id` 传给上游（参照 `FinanceSetupClientImpl`）。登记计费事实时 BFF 固定 `sourceSystem=OPERATIONS`、生成 `sourceReference = "OPS-" + UUID`（页面不传）。
3. **F1 看板** `GET /finance/period-close/v1/board?fiscalCalendarId=`：放在 BFF `service` 层的组合 Processor / Service（BFF 聚合职责），先查期间列表，再逐期取最新关账记录；只读。
4. **无体 POST**：C-report 列出的 body limit 为 0 的 POST（billing 审批 / 作废、关账发起、规则提交 / 审批 / 驳回 / 作废、发起试算）向上游不得发送请求体。
5. **超时**：关账发起、重开发起、关账审批（含损益结转）在上游同步执行。核对 ledger 本机执行耗时（用 A 卡测试数据量估算），为这几个调用单独配置更长的 BFF 读超时（建议 60 秒，配置项进 BFF Properties，不影响其他调用）；如果 Gateway 路由超时（块 101 的 read timeout）不足，把需要调整的路由和值写进报告，由主会话修订注册 SQL。
6. 模型类完整覆盖上游响应字段（BFF 开启了 `FAIL_ON_UNKNOWN_PROPERTIES`）；查询参数白名单与 C 的注册一致；`OperationsConsoleBffApplicationAutoTest` 的路由清单、Platform Simulator 权限、各 BrowserAutoTest 与边界 JSON 同步。
7. 测试：`./mvnw -o -pl operations-console-bff/paytech-operations-console-bff-test -am verify -Dtest='com/paytech/operations/console/bff/**/*Test'`。报告 `D-report.md` 列出 BFF 对 Web 暴露的最终路径、请求 / 响应字段（供卡 E）。
