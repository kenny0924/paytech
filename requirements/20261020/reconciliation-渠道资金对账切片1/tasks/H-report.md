# H 线报告：阶段 8 本地提交对象（2026-10-21）

## 结论

- BASE：`4ab205e0a393b1847e2b9cc76e4442f6edbcd95f`（构建时 HEAD）
- 提交对象：`2f5f37f79304e02e5a71251a63bd24952a3c2322`（parent = BASE，tree `2c950bcb09c039924914954f5b8b526d56d6126e`）
- 未移动 HEAD 和任何 ref，没碰共享 `.git/index`（构建时共享索引无暂存内容），没改工作区文件（本报告除外）。用私有索引 `scratchpad/recon.index`，流程是 `read-tree BASE` → `hash-object -w` → `update-index --index-info` → `write-tree` → `commit-tree -p BASE`。
- 本报告不在提交里：它描述的就是这次提交本身。主会话可以连同 delivery.md 的「提交记录」一起另行处理。

## 文件（566 个：新增、修改合计 559，删除 7）

| 组 | 数量 | 说明 |
|---|---|---|
| paytech/reconciliation | 363 | 含删除 7 个旧清算账户相关文件（ChannelAccount DO / Mapper / Repository / XML、ReferenceDataStatus、ReconciliationTestMapperConfiguration） |
| requirements/20261020/reconciliation-渠道资金对账切片1 | 101 | 全部材料与截图；脚本只从环境文件读口令，不含口令值 |
| paytech/operations-console-bff | 41 | 对账 Controller / Client / Model / Processor、Simulator、边界 JSON；pom、application.yml、README 与两个共享测试文件只取本需求部分 |
| paytech/billing | 17 | E1 / E2：PDManer、schema、changes SQL、API、DO / Mapper、converter、测试 |
| operations-console-web | 17 | src/reconciliation 7、pages/Recon* 7、pageCapabilities、ConsolePage、configurationModel.test、finance.test |
| scripts/pdmaner | 6 | reconciliation-model.test（新）、billing-model.test、finance-model.test、finance/generate-finance-model、finance/service-split、pdmaner-canonical-paths.test |
| paytech/tools/arch-rules | 3 | generate-table-ownership.mjs、table-ownership.yaml、TableOwnershipAutoTest |
| paytech/foundation/paytech-messaging | 2 | catalog/events.yaml、YamlEventCatalogAutoTest |
| paytech/ledger | 2 | BillingFactClientImpl、BillingFactMocker |
| paytech/platform-support | 2 | platform-support-initial-data.sql（块 123）、console-resource-capabilities.json |
| 文档与其他 | 12 | AGENTS.md、agent-docs/CURRENT_IMPLEMENTATION.md、agent-docs/database/FINANCE_DATABASE.md、decisions/2026-10-20-reconciliation-facts-via-billing.md、design-discuss/README.md、design-discuss/reconciliation（2 个）、requirements/README.md、requirements-site/content/requirements.ts、两份 Vault 策略 |

文件集的依据：source-overlay.txt 中 448 个文件，除共享的 11 个以外，当前工作区 blob 都与叠加时一致；c6eea71a 是 BASE 的后代，这些文件在 BASE 与 c6eea71a 之间也没有差异。所以整文件直接取工作区内容（430 个新增或修改，7 个删除）。overlay 以外的部分逐个查看 diff 后归属。

## 混合文件的 hunk 取舍

先做三方合并：`git merge-file 工作区 c6eea71a BASE`，从工作区内容中去掉商务（c6eea71a）的改动；再逐个人工核对。

| 文件 | 取舍 |
|---|---|
| ops-web ConsolePage.tsx、pageCapabilities.ts；ops-bff OperationsConsoleBffApplicationAutoTest、PlatformOperationsSimulator；console-resource-capabilities.json | 合并无冲突，结果只含对账内容 |
| ops-web configurationModel.test.ts | **调整**：页数断言在 BASE 为 48，工作区为 56（含商务 1 页）。提交取 BASE + 对账 7 页 = **55**，ops-web 测试 1194/1194 通过 |
| ops-bff README.md | 取对账控制台段，以及内部 Token 目标服务名单中的 `reconciliation-service`；**去掉**外部 API 管理改归 channel-connector 那一行（统一出口会话） |
| platform-support-initial-data.sql | BASE（4721 行）+ 块 123 与结尾补回的 `COMMIT;`（到工作区第 5272 行）；**去掉**块 124（商务） |
| generate-table-ownership.mjs | 只取对账两段（旧 13 表列入 step-removed，新 10 表列入 step-added）；**去掉**商务两表、统一出口的渠道协议 10 表，以及 t_channel_config_change 的挪动 |
| TableOwnershipAutoTest.java | **调整**：总表数取 BASE 表达式 `… + 1 - 13 + 10`，并加对账说明；新增 `test_reconciliationOwnership`；**不取**商务 +2、Platform 56→46 / 57→47、渠道协议、channel-connector 18→28 与 billing 注释改动 |
| table-ownership.yaml | **调整**：在临时检出上用生成器生成（头注释 step-removed 7→20、step-added 29→39，对账库列表 18→15），再补回 BASE 手工列入的 `t_channel_config_change`。BASE 本身的 `--check` 已因这一行失败：5f12cf3e 手工列入，生成器到 c6eea71a 的 6570ef73 才纳入。所以提交上的 `--check` 仍报同一行差异，与 BASE 相同，非本需求引入 |
| AGENTS.md | 只取对账路由行；**去掉**统一对外出口段落 |
| design-discuss/README.md | 只取 `reconciliation/` 行；**去掉** `commercial-capability-pricing/` 行 |
| requirements/README.md | 只取对账一行；**去掉**统一出口、币种、commercial 两行 |
| agent-docs/CURRENT_IMPLEMENTATION.md | 只取对账一节，插在状态说明后面。工作区中 `<a id="channel-connector-unified-outbound-20261020">` 锚点落在对账节标题上方，属统一出口会话，**未取**；提交里对账节没有锚点 |
| FINANCE_DATABASE.md、requirements.ts（E2E-08、FIN-11 / 12 / 24、FIN-I20）、两份 Vault 策略、6 个 pdmaner 脚本 | 改动全部属于本需求，整文件取 |

**未取（其他会话）**：agent-docs/DOCUMENT_GOVERNANCE.md 和其余 decisions 改动（统一出口）；requirements/20261020/finance-人工调整与计费事实/release/release.md（channel-connector 基线说明）；paytech/card、channel-connector、commercial、platform-support 其余文件；src/commercial、pages/Commercial*；known-deviations.yaml；design.pen；其他需求目录。requirements/20261017/.../run-service.sh 未修改，不涉及。

## 验证（临时 worktree 检出提交对象，验证后已删除）

| 项 | 结果 |
|---|---|
| `git diff --stat BASE 提交` | 566 files，+58968 / −5911，路径全部属于本需求（grep commercial / channel-connector / target / node_modules / dist 无命中） |
| 提交内全部 JSON 解析 | 通过 |
| node：reconciliation-model、billing-model、pdmaner-canonical-paths | 通过；finance-model 有 2 例失败，BASE 同样这 2 例（预置漂移：BASE 报 reconciliation 投影，提交上报 tax 投影） |
| generate-table-ownership `--check` | 只差 `t_channel_config_change` 一行，与 BASE 相同（见上） |
| Maven `-o -q -am test-compile`（reconciliation-test、ops-bff-test、billing-test、ledger-test、arch-rules） | 通过 |
| arch-rules 全部测试 | 62/62（含 TableOwnershipAutoTest 6/6）；messaging YamlEventCatalogAutoTest 12/12 |
| ops-bff（无库）：ReconciliationBrowser 90、FinanceBillingFactBrowser 27、ApplicationAutoTest 8、HttpBodyNaming 45 | 170/170 |
| ops-web（node_modules 软链） | `tsc --noEmit` 通过；`npm test` 1194/1194 |
| platform-support-schema / model（node） | 42 例中失败 7 例，失败集合与 BASE 完全相同（预置） |
| requirements-site | 137 例中通过 127 例，失败的 10 例与 BASE 完全相同 |
| 口令扫描：用 local-environment/.env.local、test-environment/.env.test、e2e/secrets.env、test-environment/private 中的值检索提交内容 | 没有 mor-test 口令。命中的都是 BASE 已有的非机密标识（vhost、RabbitMQ 用户名、MinIO access key、E2E 管理员邮箱），以及本机隔离 Redis 测试口令。后者在 BASE 已有 6 个服务的 application-mock.yml 按同样方式提交，reconciliation 沿用 |

## 主会话移动 HEAD 与同步共享索引

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
git rev-parse HEAD            # 必须仍是 4ab205e0a393b1847e2b9cc76e4442f6edbcd95f；否则在新 HEAD 上重建，不要直接 reset
git reset --soft 2f5f37f79304e02e5a71251a63bd24952a3c2322
git diff --stat HEAD~1 HEAD | tail -1   # 566 files changed
bash /private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/1a70f5c0-500a-4725-bdcb-fc5d7b9c803d/scratchpad/resync-index.sh
git diff --cached --stat | tail -3      # 不应再出现本次提交的路径
```

`resync-index.sh` 先确认 HEAD 已是新提交，再处理提交里的每个路径：共享索引条目仍是 BASE blob 的（BASE 中不存在的路径，索引里也应没有），改为新提交的 blob（删除的路径就从索引移除）；条目已被他人改动的跳过并打印。

reset 后，工作区里 11 个共享文件以及 AGENTS.md、CURRENT_IMPLEMENTATION 等文件的其他会话改动，会照常显示为未提交修改。这是预期结果。
