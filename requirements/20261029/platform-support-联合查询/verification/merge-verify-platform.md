# 联合查询合并核对 · Platform（jq-platform，2026-10-30）

Worktree `/Users/kenny/Desktop/Workspace/Code-MoR-jq-m`（基于 main 30361be7，含 REQ-03 块 143），未提交。只用本机 mor-local-mysql-1。

## 构建与 api 安装
- `./mvnw -o … -f platform-support/pom.xml -DskipTests package`：成功（最终一次在下面的修复之后）。
- `./mvnw -o … -pl platform-support/paytech-platform-support-api -DskipTests install`（不带 -am）：已装入
  `~/.m3/repository/com/paytech/paytech-platform-support-api/1.0.0-SNAPSHOT/`；`javap` 核对 `JointQueryApi.getJointQueryGraph`
  的 `entryKeyType` 为 `required=false`。
- 注意：zsh 下把 Maven 参数放进一个变量会不分词，导致离线解析失败；命令须逐个写参数。

## 测试结果
| 范围 | 结果 |
|---|---|
| data-query 模块（QueryTemplateEngine 27、ReadOnlySqlGuard 15、QueryResultShaper 2、ConnectionPrivilegeCheck 4） | 48/48 |
| workflow 模块（WorkflowDataQueryDomainTest） | 5/5 |
| platform-test：JointQuery 11、Guardrail 4、KeyType 16、TemplateJointConfig 13、DataQueryExecution 9、DataSourceManagement 8、QueryTemplateManagement 13、RunDataQueryTemplateProcessor 3、WorkflowDataQuery* 19、GatewayRouteIdempotencyMode 7、GatewayConfigurationLock 2、ArchitectureAutoTest 1 | 106/106 |
| tools/arch-rules | 70/70 |
| node `platform-support-schema` + `platform-support-model` | 29 通过 / 14 失败 |

- node 基线（`git archive 30361be7` 解到 scratchpad 后运行）：28 通过 / 14 失败。jq-m 的 14 个失败与基线**逐项相同**（diff 为空），
  多出的 1 个通过是新增的 `Joint query block 144 …` 测试。
- 不需要 REQ-03 的本地 DDL：REQ-03 只在 `mor_risk_decision` 建表，Platform 侧只有注册块 143 与页面能力条目，Platform 测试不依赖它们。

## 块 143 / 144 并存
- `platform-support-initial-data.sql` 中块 143 在前、块 144 在后，各自 START TRANSACTION / COMMIT；
  合并后 START 47 / COMMIT 45，基线 46 / 44（差额 2 为基线既有，联合查询只加一对）。
- ID：块 143 共 153 行（900000000000143xxx），块 144 共 125 行（900000000000144xxx），两块之间无重复；全文件的 9 个重复 ID
  与基线相同（既有，非本次引入）。
- 路由编码与 (method, external path) 全文件无重复。
- `changes/2026-10-29-joint-query-registration-144.sql` 与初始化文件中的块逐字相同（node 测试与 diff 均确认）。

## 合并相关修复（只改 jq-m）
1. **页面能力目录**：`paytech-platform-support-access-control/src/main/resources/operations/console-resource-capabilities.json`
   原先缺少块 144 两个菜单的 componentKey（块 143 的 REQ-03 也在这里登记了自己的页面）。已新增
   `OPERATIONS_JOINT_QUERY`（联合查询，/data-query/joint-query，无动作）与 `OPERATIONS_QUERY_KEY_TYPES`（键类型，/data-query/key-types，
   无动作），位置与 web 镜像 `operations-console-web/src/navigation/pageCapabilities.ts` 一致；两边 81 项、顺序完全相同。
2. **table-ownership.yaml 头注释**：retained-service 表数 73 → 74（t_query_key_type 的条目合并时已在）。
   `generate-table-ownership.mjs` 不能直接重跑：它会删掉 client-master / compliance 手工登记的十几张表，`--check` 在 main 基线上也已是 stale，
   属既有问题，未处理。
3. `TableOwnershipAutoTest` 的合并计数（REQ-03 +3、本次 +1）跑通，arch-rules 70/70。

## 未做 / 提醒
- `tools/arch-rules-reactor-test` 未运行。
- Gateway 快照离线编译、注册块执行仍在阶段 6/7。
