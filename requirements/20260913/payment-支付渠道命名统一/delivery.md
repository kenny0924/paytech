# 渠道命名统一交付记录

## 实施授权（2026-09-13）

用户在收到完整映射及本地工件实施范围后回复“改一下”，确认进入本地文档、生成器/Oracle、PDManer/完整 Schema、代码和测试的更名实施。范围依据[命名决定](../../../agent-docs/decisions/2026-09-13-payment-channel-terminology.md)；数据库执行、部署、发布和提交保持未授权。下文保留实施前评审证据。

## 范围确认

2026-09-13 用户要求：“那帮我把所有provider有关的都改成channel吧，你觉得合适吗？”

澄清是否包括 Finance、Risk、Gateway 的关联命名后，用户选择：“包含所有系统中的支付渠道命名，同步评审关联改动”。因此已确认跨系统的支付渠道命名范围；框架、外部固定协议与非支付 Provider 继续排除。该范围扩展单独登记，不反向改写[四模块命名确认的历史](../payment-业务模块划分/delivery.md)。

## 当前交付

本轮为用户明确要求的跨系统命名与关联影响评审，交付[需求](./requirement.md)、[评审稿](./technique.md)和[逐项旧新映射](./rename-map.json)。映射及实施细节尚待整体确认，未进入业务实现、物理数据库操作或发布阶段；后续按[阶段交付规则](../../../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md)推进。

核对范围覆盖六核心模型与当前 Schema、支付相关文档/生成器/测试、Gateway/Platform及Operations前后端真实使用点。发现命名会影响公共回调枚举、Risk 来源/触发、Finance 科目/目录和消息消费者，不能按纯模块重命名处理。

本轮只新增本需求材料、需求索引及原模块方案的后继路由。没有修改应用代码、PDManer、生成器、Schema、数据库或运行环境；没有提交、推送或部署。各系统原有状态仍以[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)为准。

## 待确认的下一步

按评审稿完成当前权威文档、生成器/Oracle、PDManer/完整 Schema、自有接口/事件/枚举及消费代码的命名迁移，并执行对应验证。实际环境 DDL/DML、旧消息与历史快照的迁移、制品发布和外部回调配置另按确切环境清单确认；不假定环境无旧数据。

## 验证证据

- 2026-09-13：逐项映射核对通过，23 张候选改名表、79 处列名、51 个索引名在记录的源 Schema 中均存在；三份源 Schema/模型内容摘要与核对时文件一致。
- 候选目标名称均满足 32 字符限制，同库表名及同表列/索引无冲突；Risk 结构改名严格限定争议表，保留尽调/筛查 Provider。两种较长物理列使用明确的 txn 缩写，共四处。
- 本需求及关联模块目录六份 Markdown、45 个本地文件链接、代码围栏和尾随空白检查通过；相关 `git diff --check` 通过。
- `npm --prefix requirements-site test` 构建成功，136/136 测试通过，0 失败/跳过；日志 `/tmp/codemor-channel-terminology-review-tests.log`。
- 本轮证据验证评审材料与当时工作区内容，不代表候选命名已落入代码/模型或任何数据库。未执行 Java/数据库/支付渠道验证；后续更名须按实际工件重新运行对应门禁。

## 本地实施交付（2026-09-13）

用户在完整映射评审后回复“改一下”，本次据此完成本地改名。实际状态及完整验证结论集中记录在[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-channel-terminology)，此前评审记录和旧新名称映射保留。

交付要点：

- Payment 配置/Account/MID、交易引用、交互、回调和渠道争议统一为 Channel；Finance 渠道成本/核算/清算/对账同步；Risk 只调整支付争议引用和来源/触发/责任方值。两种长物理列使用 `original_channel_txn_ref_id` 与 `target_channel_txn_ref_id`。
- Platform/Gateway/Operations 的三个回调合同代码均为 `CHANNEL_CALLBACK`，生产验证器、枚举、指标标签、BFF 输入/输出校验和 Web 选项同步。逻辑入口、业务权限和现有回调 URL 保持。
- 生成器使用 `payment-channel-identity.mjs` 中明确的旧名映射保持 PDManer UUID；这只影响建模身份，不提供运行期旧名别名。Finance 当前 manifest 文件同步为 `05-channel-cash-recon.mjs`。
- 现行 Owner/需求/时序/模块文档与 Card 草稿同步；旧方案增加窄后继路由，不改写历史执行记录。Framework Provider、OAuth/Notification/Storage、Risk DD/Screening、外部原始协议保持。Commerce 外部泄漏断言同时覆盖旧 Provider 和新 Channel 禁止字段。

可复查命令：

```sh
node --test scripts/pdmaner/*test.mjs
node scripts/pdmaner/generate-oe-client-schemas.mjs --check
node scripts/pdmaner/risk/generate-risk-compliance-schema.mjs --check
npm --prefix requirements-site test
npm --prefix operations-console-web test
npm --prefix operations-console-web run build
```

Java 验证使用 JDK 25 以及隔离的 `-Dmaven.repo.local=<临时目录>/m3`，先构建 Platform API 和当前 Risk API 供真实消费者编译，再执行 Gateway、Operations BFF 的 `verify`，以及 Platform 的 `GatewayConfigurationCompilerAutoTest,GatewayEnumOwnershipAutoTest`。曾尝试的 Platform Snapshot HTTP 测试缺少数据库密码，明确保留为验证缺口；没有自动准备数据库。

基线比较在临时副本恢复改名前文件并重新导出其 Schema，完整执行相同 533 项模型/场景测试。前后失败集合一致；未为通过本次改名修改上述其他任务的旧断言。原始日志与逐项差异证据目录：

`/var/folders/t2/xvr6hr7x4yq2c64fx_216r8c0000gn/T/codemor-channel-rename-l7suxv0t`

其中 `model-semantic-verification.json` 记录六模型的非命名比较，`regression-comparison.json` 记录批准映射计数与前后失败集合；`node-model-tests-final.log`、`baseline-tests-3.log`、`site-tests-final.log`、`gateway-tests.log`、`ops-bff-tests.log`、`platform-unit-tests.log`、`platform-tests.log`、`ops-web-tests.log`、`ops-web-build.log` 保留本次输出。

最终静态复核：935 个修改涉及的本地文档链接全部可解析，10 个仓库的 `git diff --check` 通过；当前 Payment/Finance/Risk/Platform 模型生成器与人工 Schema 的 `--check` 均通过。
