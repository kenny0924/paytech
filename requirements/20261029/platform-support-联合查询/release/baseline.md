# 联合查询 · mor-test 现网基线（阶段 6，2026-10-30 09:55—10:45 +04 只读读取）

> 读取方式：`docker inspect`、`docker exec … printenv` / `sha256sum`、`docker cp` 从容器**取出** JAR 与 health-probe、root 只读会话（`SET SESSION TRANSACTION READ ONLY`，`@@transaction_read_only = 1` 已确认）、`mysqldump --single-transaction` 只读导出到本机 `~/Desktop/Workspace/Code-MoR-backups/jq-20261030/`（含数据，不入库）、`compose.sh config`（只渲染）。
> 没有停 / 起 / 重启任何容器，没有写任何库、XXL、MQ、Platform 数据，没有登录 mor-test 运营 API，没有发布语言包，没有 `docker cp` 写入容器。

## 1. 容器镜像（= 回滚目标）

| 服务 | 运行镜像 ID | 容器启动（UTC） | JAR | 来源 |
|---|---|---|---|---|
| platform-support-service | `sha256:49f710118adb1d074de4bb4aa711e88cebec1544f3bfb00db7b4b12f213329d9` | 10-30 05:38 | `6d7ab1069d63938b`（容器内 `sha256sum` 与取出文件一致） | REQ-03 rq03-20261029a（覆盖文件在 `Code-MoR-rq03/requirements/20261029/risk-decision-承保记录/release/`）；税务发布未换 Platform |
| operations-console-bff | `sha256:e243d57bd5406a2ac8efa133b52586cc38acfd60ec2c739e6fe5ff1dabd6fe27` | 10-30 05:48 | `3080a027d5b22356` | 税务 tax-20261029a（`requirements/20261029/tax-结账算税与事件登记/release/`） |
| operations-console-web | `sha256:748ca7b9c6b417104bbb7e3a09ba4b2630d8c4c513a4de2c43644a769b66a97a` | 10-30 05:49 | dist 266 | 税务 tax-20261029a；本需求的 ops-web 候选由 jq-web 准备 |
| api-gateway | `sha256:270c8ba294ddbb026a70b030e02eb1f998c8d95a49ba88cecaeab49b516efbf4` | 10-30 05:37 | — | 不换，只做快照编译验证 |

### 1.1 基线证明（候选源码不回退现网）

税务已提交为 main `fa21d5ce`（`6eaf1bd3` 只在其上加文档）。逐文件核对：`Code-MoR-tax-rel`（tax-20261029a 的构建源，30361be7 + 379 个税务文件）与 `fa21d5ce` **379 个文件逐字节相同**。因此候选基线直接取 main `6eaf1bd3`，不再手工叠加税务补丁。用 `git archive 6eaf1bd3 paytech` 干净构建（不含本需求文件），`harness/jarcmp.py`（条目 CRC，排除 MANIFEST / maven 元数据 / 索引）：

| 项 | 结果 |
|---|---|
| ops-bff | **2943 项 0 差异**（现网 e243d57b = `6eaf1bd3`） |
| platform | 3122 项，**只差 2 个共享条目**，都是税务提交带来的、Platform 未重新发布：`paytech-messaging.jar!catalog/events.yaml`（`OrderPaymentSucceeded` 的 tax 消费方与字段注释）、`paytech-platform-support-access-control.jar!operations/console-resource-capabilities.json`（新增 `OPERATIONS_FINANCE_TAX` 页面能力）。现网 Platform 其余内容 = `6eaf1bd3` |

结论：现网没有 `6eaf1bd3` 以外的未提交内容；候选 = `6eaf1bd3` + 本需求 236 个文件，不回退任何现网工作；Platform 候选顺带上线税务的两个共享条目（菜单资源管理页从此能识别“税务查询”页面，events.yaml 只是目录说明）。

## 2. 容器环境变量（`evidence/live-env-20261030.txt`，密钥类值已替换为 `<redacted>`）

| 服务 | 键数 | 说明 |
|---|---|---|
| platform-support-service | 59 | 全部来自主 compose 与 `env_file`（`private/platform.env`、`private/client-onboarding.env`）；`DATAQUERY_HTTP_CALLERS_0=operations-console-bff` 已在 `compose.applications.yaml`。历次 Platform 覆盖（参考数据、REQ-05、REQ-03 等）都只设 image |
| operations-console-bff | 21 | 主 compose 20 个 + 税务覆盖的 `OPERATIONS_TAX_BASE_URL=http://tax-service:18116`；`OPERATIONS_{ACCOUNT,BILLING,LEDGER,RECONCILIATION}_BASE_URL` 已在主 compose |

本需求不新增 env：`data-query.joint.*` 用 `application.yml` 默认值（单步 8 秒、单键 50 值、单模板 100 行、单步 20 次、并发 8；页面累计 6 层 / 60 次 / 60 秒）。

## 3. mor_platform（只读，`precheck/mor_platform-precheck-20261030.out`）

| 项 | 读数 |
|---|---|
| 结构 | `t_query_key_type` 不存在；`t_query_template_version.joint_json` 不存在（`constraints_json` 在） |
| 块 144 | permission / role_permission / console_resource / route / header / query / guard 区间全部 **0**；8 个 route_code、(方法, 路径)、2 个 resource_code、2 个前端路由 **均未占用** |
| 依赖 | 权限 131101 `DATA_QUERY_READ`、131103 `DATA_QUERY_TEMPLATE_MANAGE`、131104 `DATA_QUERY_EXECUTE` ACTIVE；平台管理员角色 `…510` 四项授权 ACTIVE；菜单组 131301 与 131302—131305 ACTIVE；块 131 18 条路由 ACTIVE；target `…000001`（operations-console-bff）ACTIVE |
| 路由 | ACTIVE **633**、DISABLED 6（共 639）；最大块 146（税务，路由 2）；140—143、146 均 ACTIVE；145 无行 |
| 数据源 | `MOR_ACQUIRING_REPLICA` / `MOR_LEDGER_REPLICA` / `MOR_RECONCILIATION_REPLICA` / `MOR_CLIENT_MASTER_REPLICA` / `MOR_COMMERCIAL_REPLICA` / `MOR_RISK_DECISION_REPLICA` / `MOR_TEST_MYSQL_INSTANCE` ACTIVE；`MOR_CHANNEL_CONNECTOR_REPLICA` **INACTIVE**（从未检查）；没有 payment / billing / account 数据源 |
| 模板 | 7 条 DEMO_*（`DEMO_REFUND_REQUESTS` INACTIVE）、4 条 GOLIVE_*；**无 JQ_\***（编码不冲突） |

## 4. 只读账号与被查库

`data_query_demo_reader@%`：`SELECT ON mor_acquiring / mor_channel_connector / mor_client_master / mor_commercial / mor_ledger / mor_reconciliation .*`，`mor_risk_decision.t_client_underwriting` 表级。**没有 mor_payment / mor_billing / mor_account** → `sql/root-demo-reader-grants.sql`。口令在 `test-environment/private/data-query-demo.env`（只由登记脚本读取，不打印）。

被查表结构：模板用到的 25 张表在 mor-test 与本机 `*_v2e2e` 逐列比对 **22 张相同**；`mor_account.t_owner_account`、`mor_ledger.t_ledger_account` / `t_chart_account` 的 `account_no`（及 `account_status`）本机 v2e2e 没有（v2e2e 落后于现行结构），用到它们的 2 条模板（账户分录、凭证分录）改在本机 auto 库 `mor_account` / `mor_ledger`（与 mor-test 同结构）验证，并在 mor-test 只读会话实际执行。

## 5. mor-test 业务数据（只读，`precheck/demo-chains-mortest-20261030.out`）

- 只有运营主体 **AE001**（无 SA / EG），订单全部 `HOSTED_CHECKOUT`；没有 `SAAS_AUTOMATED` 订单，**不存在一条“收单 → 支付 → 计费 → 账户 → 总账”全部真实的链路**：
  - 计费事实只有 2 条 PAYMENT（billing 阶段的合成订单、税务阶段 7 验收订单 `ord_3LOyZii4…`），其支付引用（意图 / 尝试 / 渠道交易引用）是合成 ID `9999…`，在 mor_payment 不存在；
  - 唯一 SUCCEEDED 支付意图 `239094951181373440`（PAYERMAX 3DS，2026-10-22）的 `source_id` 不对应收单订单，也没有计费事实；
  - `t_channel_interaction`、`t_channel_callback_item`、`t_channel_refund` 为空；10 条退款申请都是 PROCESSING、未建渠道退款。
- 因此验收用 5 个入口拼出全链路（stage6-report 第 4 节）。

## 6. 语言包（只读，`precheck/i18n-plan-dryrun.out`）

operations-console 当前 **#24**，27 个命名空间、10 902 个 ACTIVE key。计划 #25：新增 **215**（data-query 213、nav 2），基准变更 0、废弃 0、恢复 0；zh-CN 只导入这 215 条，其余 10 902 条 zh-CN 保持线上；不新建命名空间；`zhChangedNotImported` 0。tax 命名空间无任何变化。

## 7. 其他

- 本机（不是 mor-test）：本机 e2e Platform 已被多语言发布变更记录会话换成其 JAR（pid 14518，09:49 +04，`.pre-i18nchg` = 本需求阶段 5 的 JAR）；ops-bff 仍是阶段 5 的联合构建（pid 73742）。
- 并行发布：`requirements/20261030/platform-support-多语言发布变更记录/` 也在准备 Platform 发布（阶段 5 完成待确认，提交 `69f87bbf` 在独立分支、未入 main）。两者谁后上线，谁的 Platform 候选必须包含对方改动（stage6-report 待裁决 D-2）。
