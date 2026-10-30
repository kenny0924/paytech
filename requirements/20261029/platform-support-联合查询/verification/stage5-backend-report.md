# 联合查询 · 阶段 5 本机联调（后端）报告（2026-10-30 00:20—07:36 +04）

**当前进程：Platform pid 76398（jq-m JAR，`DATAQUERY_HTTP_CALLERS=operations-console-bff`）；ops-bff pid 73742（合并 JAR：jq-m + 税务 ops-bff，来源 `Code-MoR-jq-bffcombo`）。**

> 只操作本机：mor-local-mysql-1（`mor_platform_v2e2e`）、本机 Platform / ops-bff 进程与 JAR、本机语言包。未碰 mor-test，未提交。**新 JAR 保持运行。**
> 结论：API 联调 **41 / 41 通过**（`stage5/stage5-e2e-result.json`、`stage5/logs/60-e2e.log`）；REQ-03 / 税务冒烟 5 / 5；演示数据就绪，可进行浏览器走查。
> 本会话的共享库写入被权限分类器拒绝：前滚 DDL / 块 144 与 `60-data-query-local.sql` 均由 team-lead / 用户代为执行（见变更清单）。

## 1. 变更清单

| 时间（+04） | 动作 | 备份 / 回滚 |
|---|---|---|
| 00:23 | 构建 jq-m Platform / ops-bff exec JAR（离线，-DskipTests） | — |
| 00:24:50 | mysqldump `mor_platform_v2e2e`（只读） | `stage5/backup/mor_platform_v2e2e.data-query-tables.20261030.sql`、`…gateway-console.20261030.sql` |
| team-lead 执行 | 前滚 DDL（`t_query_key_type`、`joint_json`）+ 块 144 插入 + 激活；Gateway 534 → 542，持续刷新成功（复核：键类型 26、路由 8 ACTIVE、菜单 2 ACTIVE） | `changes/2026-10-29-joint-query-registration-144-rollback.sql` → `changes/2026-10-29-joint-query-rollback.sql`（先停新 Platform） |
| 00:28—00:32 | 合并 ops-bff：`/Users/kenny/Desktop/Workspace/Code-MoR-jq-bffcombo`（普通拷贝，不是 git worktree）= jq-m `paytech/` + `Code-MoR-taxcombo` 的 `paytech/tax/` 全目录 + ops-bff 中 22 个税务新增文件 + 9 个共享文件的税务改动（两个 pom 加 `paytech-tax-api`、application.yml `tax-base-url` 与元数据日志前缀 `/tax/v1`、`OperationsErrorStatusResolver` 税务错误码映射、5 个测试文件；`OperationsConsoleBffApplicationAutoTest` 的前缀列表手工合并）。未改 Code-MoR-taxcombo 与 Code-MoR-jq-m 源码 | 删除该目录即可 |
| 00:32 | 合并构建 `-pl operations-console-bff/paytech-operations-console-bff-test -am package`：BFF 测试模块 **2519/2519**，链上其他模块测试全部通过；JAR 含 `TaxOrderController`、`DataQueryKeyTypeController`、`DataQueryJointController`、`RiskUnderwritingController` 与 `paytech-tax-api`；相对原运行 JAR 无删除的类 / 依赖 | — |
| 00:37:44—00:38:17 | Platform：`cp -p` 备份 → `.tmp` + rename 安装 → 停 pid **92711**（REQ-05 wfdq JAR）→ 按抓取的 argv / 环境 / 工作目录重启（含 SPRING_APPLICATION_JSON internal-auth DUAL、RabbitMQ），18 秒就绪 | `Code-MoR/local-environment/e2e/jars/paytech-platform-support-app-exec.jar.pre-jq` |
| 00:38:39—00:38:59 | ops-bff：同法，停 pid **8739**（税务会话 22:23 安装的 taxcombo JAR）→ 合并 JAR，6 秒就绪，新 pid **73742** | `…/paytech-operations-console-bff-app-exec.jar.pre-jq` |
| 00:40:43—00:41:10 | Platform 再重启，追加 `DATAQUERY_HTTP_CALLERS=operations-console-bff`（本机原未配置 `data-query.http.callers`，联合查询与单模板执行会拒绝 BFF），15 秒就绪，新 pid **76398** | 去掉该覆盖项重启即恢复 |
| 00:42:35—00:42:57 | 本机语言包 **#19**（`stage5/stage5_i18n_local.py`）：英文基准 = 本机全部 ACTIVE key ∪ jq-m 的 data-query 与 nav，新增 215 key（data-query 213、nav 2），不废弃；zh-CN 215 条（`.jq-notes/zh-CN-data-query.json` + jq-m zh-CN nav 两条）。发布接口同步响应超过网关 10 秒返回 504，库中 #19 已 READY | 把 #18 设为当前版本 |
| 约 07:33（03:33Z），team-lead 经用户批准执行 | `stage5/60-data-query-local.sql`（本机专用）：授权 131204 DATA_QUERY_EXECUTE、块 131 其余 10 条路由、菜单 131301—131305；Gateway 542 → 552，持续刷新成功 | `stage5/60-data-query-local-rollback.sql` |
| 01:19—01:21、07:34—07:36 | `stage5_e2e.py --prepare` 与完整运行（`--with-slow`，三次；慢查询模板每次临时启用后恢复 INACTIVE） | 演示数据见第 4 节 |

两次重启后 Platform / ops-bff 日志无 ERROR，Gateway 持续刷新成功。重启工具：`stage5/swap.py`（复用 REQ-03，抓取文件只在会话暂存区）。

## 2. 已完成的冒烟与演示数据准备

- 01:1x `stage5/smoke_rq03_tax.py`（只读，管理员令牌经 Nginx 18444）**5 / 5**：REQ-03 承保列表 200（5 条）与统计 200；税务 S1 订单
  `ord_3LNgHd0TWGaDLjatKpMN9CnRwDu7TN` 200；不存在订单 404 `TAX_ORDER_NOT_FOUND`；键类型列表 200。日志 `stage5/logs/40-smoke-rq03-tax.log`。
- 税务 ops-bff 源码核对：合并所用的 22 个税务新增文件、`OperationsErrorStatusResolver` 税务段、两个 pom 的 `paytech-tax-api`、
  `paytech/tax/paytech-tax-api` 与 `Code-MoR-tax`（税务会话现用）逐字相同；`Code-MoR-taxcombo` 与 `Code-MoR-tax` 的 ops-bff 差异只是
  taxcombo 另带的 REQ-03 承保文件（main 已含），因此无需重建。
- 01:19—01:21 `stage5_e2e.py --prepare` **20 / 20**（日志 `stage5/logs/50-e2e-prepare.log`、`stage5/stage5-e2e-prepare-result.json`）：
  数据源 `FIXTURE_JQ` `241672330982461440`；演示键类型 `JQ_E2E_SEQ` `241672332832149504`；K1—K5 用例（K5 按原顺序整体重写后
  全部 27 个键类型 sortNo 为 10、20 …，相对顺序不变）；13 个 `JQ_*` 演示模板（`JQ_SLOW_ORDER` 保持 INACTIVE）。

## 3. API 联调结果（`stage5/stage5_e2e.py`，经 Nginx 18444 → Gateway → ops-bff → Platform，真实令牌）

07:34 首次完整运行 43 项 42 通过：唯一失败为“J2 缺少 entryKeyType”期望 Platform 的 400 BUSINESS_CHECK_FAILED，实际为 ops-bff
`DataQueryJointController` 自己的参数校验（`@RequestParam` 必填 + 格式）在 BFF 返回 400 INVALID_REQUEST、请求不到 Platform。这是 BFF
合同、仍为 400（不是 502）；脚本改为期望 INVALID_REQUEST 并补“未知键类型 → 400 BUSINESS_CHECK_FAILED”。模板往返只在首次创建时执行（07:34
那次通过），重跑改为读回核对 v1 / v2 / v3。**07:36 最终运行 41 / 41 通过。**

| 组 | 用例 |
|---|---|
| 准备 | 数据源 FIXTURE_JQ ACTIVE；13 个演示模板就绪，保存均返回 INFO“参与联合查询，触发键 …” |
| K1—K5 | 列表含首批 26 个且按 sortNo；状态 / 关键字筛选；新建；编码重复 400；正则含断言 422 `valuePattern`；修改加版本；版本过期 409；不存在 404；停用 / 同状态不加版本 / 启用；重排未覆盖全部 400、版本不符 409、整体重写成功；被 ACTIVE 模板引用时停用 400；引用数 ORDER_ID ≥ 6 |
| 模板配置 | 保存：维度键标为链路的 WARNING `joint.columns[1].keyRole` + 参与 INFO；详情回显 joint / jointQuery；去掉配置后 v2 不参与；恢复 v1 → v3 配置相同；停用后 jointQuery `TEMPLATE_INACTIVE`；键参数未声明 422 `joint.keyParameter.name`；编辑器校验 `joint.subjectKeyType` 错误 |
| J1 / J2 | 入口键类型、分组只含泳道（收单 10、支付 70、计费 150，无“归属维度”；rank 因 K5 重排变为 10 的倍数）、护栏；可达预览最少步数（退款单号 0、订单 1、支付动作 3）、断点、JQ_SLOW_ORDER 列为 TEMPLATE_INACTIVE；不可作入口 / 未知 400 BUSINESS_CHECK_FAILED；缺参数 400 INVALID_REQUEST（BFF） |
| J3 | 退款单号入口逐层展开 10 个链路模板、各一次、全部成功；屏蔽列 `bu***************.test`；维度键不进入发现键；边可追溯（nodeId / rowIndex / column）；支付尝试入口查回订单与收银台且泳道相同（收单 10，sortNo 20 / 10）；CO-1 命中两个 Client 全部展开；LIST 合并（含 05001 去重）、标量逐值、templateCodes 重试；正则不符 / 超过 20 个值 / 不可作入口 / 层号越界 422 并定位字段 |
| 加载更多 | 游标节点（每页 1 行）truncated + nextCursor，经单模板执行取下一页 9002；偏移节点 truncated、无 nextCursor，经单模板执行第 2 页 9102、total 2 |
| 权限 | 只有 DATA_QUERY_READ 的账号：J1 403、J3 403、K2 403、K1 可读 |
| 税务共存 | 合并 ops-bff 税务查询路由无 5xx |
| 失败隔离（--with-slow） | JQ_SLOW_ORDER FAILED `DATA_QUERY_TIMEOUT`，同一步 JQ_ORDER_BY_ID / JQ_REFUND_BY_ORDER 成功；之后恢复 INACTIVE |

## 4. 演示数据（浏览器走查）

登录：`/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env` 的 `E2E_ADMIN_EMAIL` / `E2E_ADMIN_PASSWORD`（同 REQ-01 / REQ-05；
本机平台管理员角色现有 DATA_QUERY_READ / SOURCE_MANAGE / TEMPLATE_MANAGE / EXECUTE）。菜单：数据查询 → 联合查询（`/data-query/joint-query`）、键类型（`/data-query/key-types`）。
语言包：本机 #19（READY）。

| 对象 | 编码 / ID |
|---|---|
| 数据源 | `FIXTURE_JQ` `241672330982461440`（`mor_data_query_fixture`，只读账号 `data_query_fixture_reader`，读 `t_jq_*` 链路表） |
| 键类型 | 首批 26 个（`9000000000001441xx`）+ 演示 `JQ_E2E_SEQ` `241672332832149504`（计费分组，不可作入口）；K5 重排后 sortNo 为 10、20 …（顺序同初始化） |
| 链路模板（ACTIVE，v1） | `JQ_REFUND_BY_NO`、`JQ_REFUND_BY_ORDER`、`JQ_ORDER_BY_ID`（偏移分页，buyer_email KEEP_EDGES 屏蔽，client_id / client_order_id 维度）、`JQ_SESSION_BY_ID`（标量）、`JQ_INTENT_BY_ORDER`（source_id VARCHAR）、`JQ_INTENT_BY_ID`、`JQ_ATTEMPT_BY_INTENT`（游标，每页 1 行 → 截断与加载更多）、`JQ_ATTEMPT_BY_ID`、`JQ_ACTION_BY_ATTEMPT`（偏移，每页 1 行）、`JQ_BILLING_BY_ORDER`（order_ref VARCHAR）、`JQ_ORDER_BY_CLIENT_ORDER_ID`、`JQ_ORDER_BY_CLIENT`（客户维度入口） |
| 默认停用 | `JQ_SLOW_ORDER`（2 秒超时；失败演示时临时启用，用后停用）；`JQ_E2E_ROUNDTRIP`（v1 有配置 / v2 无 / v3 恢复，已停用） |
| 受限账号 | `JQ_READER_*` 角色（仅 DATA_QUERY_READ），账号 `241766823245123584`，口令只在会话暂存区 |

建议入口值：退款单号 `rfd_A00000000000000000000000007001`；支付尝试 `9001`；客户订单号 `CO-1`（两个 Client）；客户 `3101`；渠道交易号 `EXT-AAA`（无参与模板，断点演示）。

## 5. 日志与已知现象

- ERROR：ops-bff 1 条（00:42:53，语言包发布同步响应超过 10 秒的 DEPENDENCY_TIMEOUT，发布已 READY）；Platform 3 条 `TransactionTimedOutException`
  （03:32 / 05:04 / 05:23 +04），均紧跟 Hikari“clock leap detected（delta 18 分钟）”，是本机休眠唤醒后 Gateway 每 5 秒的快照轮询事务超时，
  不是本次脚本请求；之后照常。
- 浏览器走查前提醒：`JQ_ATTEMPT_BY_INTENT` / `JQ_ACTION_BY_ATTEMPT` 故意每页 1 行，链路中会显示“已截断 / 加载更多”。

## 6. 恢复方法

1. JAR：`cp -p <jar>.pre-jq <jar>.tmp && mv -f <jar>.tmp <jar>`（Platform、ops-bff），再用 `stage5/swap.py restart`（去掉 `DATAQUERY_HTTP_CALLERS`）。
   ops-bff 还原为税务会话的 taxcombo JAR（`.pre-jq`），Platform 还原为 REQ-05 JAR。
2. `mor_platform_v2e2e`：`stage5/60-data-query-local-rollback.sql` → `changes/2026-10-29-joint-query-registration-144-rollback.sql`
   → 旧 Platform 上线前 `changes/2026-10-29-joint-query-rollback.sql`；或从 `stage5/backup/` 恢复。
3. 语言包：把 #18 设为当前。
4. 演示数据：停用 / 删除第 4 节模板与数据源；`JQ_READER_*` 账号与角色经 IAM 停用。
5. 合并构建目录 `Code-MoR-jq-bffcombo` 可删除。
