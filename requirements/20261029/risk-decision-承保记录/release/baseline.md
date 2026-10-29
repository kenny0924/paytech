# REQ-03 risk-decision 承保记录 · mor-test 现网基线（阶段 6，2026-10-29 21:30—22:15 +04 只读读取）

> 读取方式：`docker inspect`、`docker exec … sha256sum`、`docker cp` 从容器**取出** JAR / dist / health-probe、root 只读会话（`SET SESSION TRANSACTION READ ONLY`，`@@transaction_read_only = 1` 已确认）、`mysqldump --single-transaction` 只读导出到本机 `~/Code-MoR-backups/rq03-20261029/`（含数据，0600，不入库）。
> 未停 / 起 / 重启任何容器，未写任何库，未登录运营 API，未调用 Platform / IAM / i18n / data-query 写接口，未 `docker cp` 写入容器。

## 1. 容器镜像（= 回滚目标）

| 服务 | 运行镜像 ID | 容器启动（UTC） | JAR / dist | 来源（compose 覆盖文件） |
|---|---|---|---|---|
| risk-decision-service | `sha256:288b0ff6a607a8e82bbf7e7cd03023bcca9159fc72e599281ec2fda81fe28a88` | 10-25 13:43 | JAR `0196699b7bac2fac`（容器内 `sha256sum` 与取出文件一致） | 商业关系快照 relsnap-20261025a（`4b661830`）：`Code-MoR-relsnap/…/release/compose-candidate.yaml`（本服务只设 image） |
| operations-console-bff | `sha256:6833f484994be41f72ee3fb3ee73006514b9089e5a5d4330af6e3bc245c597bd` | 10-29 09:43 | JAR `5cee8709b55af2d1` | REQ-05 wfdq-20261029a（`af972805`）：`Code-MoR-wfdq/…/compose-candidate.operations-console-bff.yaml` |
| operations-console-web | `sha256:0b456b8aed53df0d31719f995a755d8f74d1abbd2a8b05e6de53ef5e0c34ae72` | 10-29 13:02 | dist 256 个文件（`evidence/ops-web-dist-live-0b456b8a.sha256`） | REQ-12 rq12-20261029a（`88453366`）：`Code-MoR-rq12/…/compose-candidate.operations-console-web.yaml` |
| platform-support-service | `sha256:7671724696de0756d1884f64fb2478505f1d775588e1a5e80bd4febe890d567a` | 10-29 09:42 | JAR `0345c0dd5b7e7bff`（离线编译演练也用它） | REQ-05 wfdq-20261029a：`Code-MoR-wfdq/…/compose-candidate.platform-support-service.yaml`（只设 image）；本需求增补候选只差页面能力清单（stage6-report 第 8 节） |

### 1.1 基线证明（候选源码不回退现网）

候选基线 = `e3e0c12f`（main HEAD，`git log e3e0c12f..main` 为空），干净 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-rq03-rel`，先不加本需求文件构建（`-am clean package -DskipTests`，私有仓库 `~/.m3-rq03`；Web `npm ci && npm run build`）：

| 项 | 方法 | 结果 |
|---|---|---|
| risk-decision | `harness/jarcmp.py`（条目 CRC，排除 MANIFEST / maven 元数据 / 索引） | **1384 项，0 差异**（`evidence/jar-baseline-risk-live-vs-e3e0c12f.txt`）：relsnap 之后 risk-decision 及其依赖无代码变化 |
| operations-console-bff | 同上 | 2824 项、0 独有，16 项不同，**全在 `paytech-client-master-api`**（REQ-12 `88453366` 给 `FormSchemaResponseBody` / `ClientDraftModels` 加了 JavaDoc）。`javap -p -c -constants` 去掉行号表后 **字节码 0 差异**（只是行号表变化）；现网 ops-bff 是 REQ-05 构建，早于 REQ-12，REQ-12 本身不发布 ops-bff |
| operations-console-web | dist 与 `docker cp mor-test-operations-console-web-1:/usr/share/nginx/operations-console/.` 逐文件 SHA-256 | **256 个文件逐字节相同**（`evidence/ops-web-dist-baseline-compare.txt` 为空） |

结论：现网三个服务没有 `e3e0c12f` 以外的内容。候选 = `e3e0c12f` + 本需求 180 个文件，不回退任何现网工作。

## 2. 容器环境变量键

`scripts/env_check.sh` 渲染本目录 6 个覆盖文件，与运行容器**逐键逐值相同**（risk-decision 33、ops-bff 20、ops-web 7；`evidence/env-check-20261029.txt`）。本需求**不新增 env / 配置**：
- risk-decision：`/risk-underwriting` 只记元数据的前缀写在镜像内 `application.yml`（`web.logging.metadata-only-path-prefixes`）；新代码无 `@Value` / `@ConfigurationProperties`。
- ops-bff：同上加前缀；Risk 客户端复用现有 `operationsRiskFlowProxyFactory`（服务发现 `risk-decision-service`），只在 Risk 错误码表加 `RiskUnderwritingErrorCode`。

## 3. mor_risk_decision（只读，`precheck/mor_risk_decision-precheck-20261029.out`）

| 项 | 读数 |
|---|---|
| 三张新表 | 均不存在；同名索引 0；回滚过程 `rq03_client_underwriting_rollback` 不存在 |
| 表数 | 54（发布后 57；本机 e2e 前滚后同为 57） |
| 字符集 | `utf8mb4` / `utf8mb4_0900_ai_ci` |
| 应用账号 | `risk_decision_app`：`SELECT, INSERT, UPDATE, DELETE ON mor_risk_decision.*`（覆盖新表，不需 GRANT） |

## 4. mor_platform 注册与 Gateway（只读，`precheck/mor_platform-block-143-precheck-20261029.out`）

| 项 | 读数 |
|---|---|
| 块 143 | 七张表 `…143001—…143999` 区间**均 0**；`RISK_UNDERWRITING%` 权限、`OPERATIONS_RISK_UNDERWRITING%` 资源 / `/risk/underwriting` 前端路由、`operations-risk-underwriting-%` 路由 / `/api/operations/risk-underwriting/%` 路径**均 0** |
| 现网最大 | 路由 `900000000000142403`（REQ-05 块 142）、权限 `…139101`、资源 `…139301` |
| 路由计数 | ACTIVE **619**、DISABLED 6（离线编译：loaded 625、effective 619） |
| 依赖 | 角色 `900000000000000510 PLATFORM_ADMIN` ACTIVE；父菜单 `900000000000000565 OPERATIONS_RISK_MANAGEMENT` ACTIVE（权限 `RISK_DECISION_CONFIG_READ`）；target `900000000000000001`（ops-bff）ACTIVE |
| 风控子菜单 | 9 个，sort 10—90；**`OPERATIONS_QUALIFICATIONS`（relsnap 块 137）已占 sort 90**，本块新菜单也是 90（见 stage6-report Q-2） |

## 5. 角色（只读，`precheck/mor_platform-roles-20261029.out`）

运营侧系统角色只有两个：`PLATFORM_ADMIN`（124 项授权、2 个成员）、`OPERATIONS_VIEWER`（6 项：账号 / 角色 / 审计 / 系统 / Gateway 配置只读 + `RISK_DECISION_CONFIG_READ`，2 个成员）。其余 ACTIVE 非 TENANT_* 角色都是历次验收 / 演示的合成角色（QA_* / DEMO_* / CAP_STAGE7_* / RELSNAP_*），没有业务风控角色。风控权限只授给 `PLATFORM_ADMIN`（另有 `RISK_RULE_SMOKE_*` 冒烟角色，全部 REVOKED）。

## 6. data-query（只读，`precheck/data-query-20261029.out`、`precheck/dq-plan-stage6-before-release.json`）

| 项 | 读数 |
|---|---|
| 数据源 | 7 个，全部用账号 `data_query_demo_reader`（`mysql:3306`）；`MOR_RISK_DECISION_REPLICA` **未占用**；参照 `MOR_COMMERCIAL_REPLICA`：连接参数 `{"connectTimeout": "3000", "connectionTimeZone": "UTC"}`、连接池 3 |
| 账号权限 | `SELECT` 于 `mor_acquiring / mor_channel_connector / mor_client_master / mor_commercial / mor_ledger / mor_reconciliation`（库级）；**无 `mor_risk_decision`** → 需 DBA 授权（`sql/40-…`） |
| 模板 | 10 条（DEMO_* 7、REQ-05 GOLIVE_* 3）；`GOLIVE_UNDERWRITING_APPROVED` **未占用** |
| 凭据 | 口令在 `test-environment/private/data-query-demo.env`（`DATA_QUERY_DEMO_READER_PASSWORD`），脚本读取不打印 |

## 7. 语言包（只读，`precheck/i18n-plan-dryrun-operations-console.out`）

operations-console 当前 **#22**（REQ-12）；26 个命名空间、10 441 个 ACTIVE key。计划 #23：新增 **247**（risk 242、nav 3、client-review 1、master-data 1），基准变更 0、废弃 0、恢复 0；zh-CN 只导入这 247 条；其余 10 441 条 zh-CN 保持线上；不新建命名空间；`zhChangedNotImported` 0。key 清单 `i18n-bundles/underwriting-key-changes.tsv`。

## 8. 验收数据候选（只读）

| 标记 | Client | 现状 | 用途 |
|---|---|---|---|
| L | `240686705999454209` "Acceptance Trading 1027040243 LLC"（AE 机构） | 切片 1 mor-test 验收停在四眼前：案件 `240686862329565184` IN_REVIEW、CRR LOW、推荐 APPROVE、责任 Partner `900000000001370001`、MCC 5999 已确认；两个关系人子案件 SUBMITTED；月流水 50000 USD | 由合成复核人批准 → LOW 自动承保 → 修订 |
| H | `241049681294106625` "Refdata AE 1028040509 LLC"（AE 机构） | 参考数据发布的冒烟申请：案件 `241049829650845696` SUBMITTED、未评 CRR、未分类；月流水 50000 **AED**（规则无 AED 阈值） | 审完、MCC 5967（卡组织高风险）、CRR HIGH → HIGH 四眼 |
| M | `239260386824658945` "Accept Tester"（LEGACY 迁移） | 案件 APPROVED、CRR 为空、MCC 5999；遗留申请无 Schema | CRR 为空按 MEDIUM；若工作台对遗留申请不是 READY，只记录现状 |

不用：`DEMO-CL-*` 与 REQ-05 验收用的 4 个 Client（`237575207592763393`、`237573267559391233`、`237573382420406273`、`237573699337822209`）；名称像真实企业的在途申请（如 "Al Waha Business Consulting Civil Co"、"Layla Haddad"）。mor-test 现有 37 个 APPROVED 入驻案件**全部是 LEGACY 迁移**（CRR 为空），没有经新流程批准的 Client——LOW / HIGH 两条路径只能先批准在途测试案件（L、H），属于阶段 7 的写操作。
