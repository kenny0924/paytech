# 任务卡 01 报告 · 阶段 1：迁移方案、脚本与本机演练

- 分支：`test-env/v2-migration-stage1`（worktree `Code-MoR-mt1`，起点 `main@f73bd1e7`）。未触碰 mor-test 的容器、卷与 `.env.test`；转储只读使用，转储内容与其中任何值没有写入仓库、报告或输出。
- **结论：演练通过。** 迁移核对 150 项全部一致，三个拆分旧库 4,210 行 = V2 六库 4,110 行 + 归档 100 行；V2 服务 9 个全部 UP（任务卡要求的 8 个，另加 payment）；只读抽样 14 项全部一致。`rehearse.sh all` 从空环境到清理完成一次跑通，用时 1 分 48 秒。
- 需要先看：第 6 节待确认（共 12 项），尤其第 1、2、8、9 项。

## 0. 已读输入与方案要点

- 已读：本需求 `requirement.md` 与任务卡；`table-ownership.yaml`；design/03 第 1–4 节；第 02a、02d、03、04、06、13、14 步报告的改动、偏差与待确认；`test-environment/` 的 `compose*.yaml`、`compose.sh`、`java/Dockerfile`、`scripts/{build.sh,build-vault.sh,initialize.py,prepare.py}`、README 中 Vault / XXL / 验收段落；第 14 步 `verification/local-e2e/`（启动器与 SQL）；`local-environment/mysql/init/`；各服务 Schema 与初始化 DML。
- 方案：差异一律从演练库实测（[schema_diff.py](../scripts/schema_diff.py)、[seed_diff.py](../scripts/seed_diff.py)）；迁移 SQL 由 [generate_sql.py](../scripts/generate_sql.py) 从同一演练库生成显式列清单，入库后不手改；[migrate.sh](../scripts/migrate.sh) 只按顺序执行评审 SQL，并在每步前核对状态；业务归属不明处一律按"保留数据、不丢失"进归档库。
- 不涉及同步业务接口开发，AGENTS 的接口开发必读不适用；不涉及事务内外部调用。

## 1. 交付清单

| 交付 | 路径 | 说明 |
|---|---|---|
| 逐表映射与升级差异 | [design/01-migration-map.md](../design/01-migration-map.md) | 140 张拆分旧表逐表去向；Platform / Vault 差异清单 |
| 评审 SQL | [sql/](../sql/) | `10` 建库与账号（口令占位）、`20` Platform、`21` Vault、`30`/`31`/`32` 迁移、`33` 归档、`40` 核对、`50` XXL-Job；`legacy-schema.sha256` 为旧库结构指纹 |
| 执行器 | [scripts/migrate.sh](../scripts/migrate.sh) | `plan` / `apply` / `verify` / `fingerprint`；`--park-legacy` 处理被旧表占用的 V2 库名 |
| 演练 | [scripts/rehearse.sh](../scripts/rehearse.sh)、[rehearse-services.sh](../scripts/rehearse-services.sh)、[sample_reads.py](../scripts/sample_reads.py) | 一次性容器、迁移、服务冒烟、抽样、清理 |
| 生成 / 比对工具 | `scripts/{generate_sql.py,schema_diff.py,seed_diff.py,migrate_support.py}` | 只读 `information_schema`，不输出行值 |
| Compose 内存调优 | `test-environment/compose.applications.yaml` | 18 个 Java 服务分三级 `mem_limit` 与入口 JVM 参数（第 4 节） |
| Runbook | [runbook.md](../runbook.md) | 阶段 2 逐步命令、预期与回滚 |

## 2. 迁移演练

### 2.1 环境

| 资源 | 演练值 |
|---|---|
| MySQL | `mor-mt1-mysql`，`mysql:8.4.11` 与 mor-test 同一镜像摘要，`127.0.0.1:23307`，随机 root 口令只在 scratchpad |
| 模拟项 | `platform_app` / `vault_app` 账号（转储不含账号）；旧 init 遗留的空库 `mor_payment`、`mor_channel_connector`；`mor_payment` 一张空旧表（演练 `--park-legacy`）；`xxl_job` 上游表结构 + commerce-service 执行器与一条补偿扫描任务 |
| V2 口令 | 14 个随机值写入 scratchpad 的 env 文件，经 `MT_ENV_FILE` 交给 migrate.sh，替换只发生在 mysql 的标准输入 |

### 2.2 过程（`rehearse.sh migrate`）

1. `plan`（不带 `--park-legacy`）：按预期在 `mor_payment` 处停止并列出旧表，无写入。
2. `plan --park-legacy`：五个旧库表数与结构指纹一致；14 个 V2 库状态（ABSENT / EMPTY / FOREIGN）与计划动作逐项列出。
3. `apply --park-legacy`：旧表 RENAME 到归档库 → 10 → 14 个库 Schema（8 个空库加初始化 DML）→ 20 → 21 → 30 / 31 / 32 → 33 → 50 → 核对。
4. 再次 `apply`：全部步骤识别为已完成并跳过，核对仍通过。

### 2.3 结果

| 核对 | 结果 |
|---|---|
| 40 逐表行数与内容校验和 | 150 项，MISMATCH 0：134 项逐表复制（其中 9 项按类型拆分）、5 项拆分覆盖、11 项归档 |
| 行数守恒 | mor_cct 513 + mor_commerce 166 + mor_risk 3,531 = 4,210 = V2 六库 4,110 + 归档幂等 100 + 争议 0 |
| V2 库表集合 | 14 个库与评审 Schema 完全一致（client-master 29、commercial 14、acquiring 16、consumer 2、risk-decision 54、compliance 24、payment 15、channel-connector 13、card 8、ledger 31、billing 9、account 8、reconciliation 18、tax 24） |
| Platform | 57 表；USD 1 行；`commerce-client-api` 发现名 `acquiring-service` |
| Vault | `PAYMENT_CARD_DEK` 新建；7 条 V2 调用方规则；策略版本 15 → 16 |
| risk-decision 种子 | 初始化 DML 中 61 个种子主键全部存在于迁入数据 |
| XXL-Job | 新增 `acquiring-service` 分组与 `compensationEventScan`（停止）；commerce-service 任务已停 |
| 归档库 | 12 表 117 行：3 张幂等表 100 行、删除列原值 17 行、7 张争议表 0 行、演练旧表 1 张 0 行 |

非空表的逐项核对见附录 B。

## 3. V2 服务冒烟

`rehearse-services.sh` 以 `test` Profile 启动（与 compose 同一环境变量名），连演练库、一次性 Redis（`mor-mt1-redis:26389`，database 5）与 RabbitMQ（`mor-mt1-rabbitmq:25682`，vhost `/mor-mt1`，带一致性哈希插件）；Nacos 关闭，发现名经 simple discovery 指向本机端口。mor-local 在本步开始时处于停止状态，之后被他人重新启动，本步没有使用也没有操作它。

| 服务 | 端口 | UP 耗时 | 常驻内存 |
|---|---|---:|---:|
| vault（Go，本机编译） | 28197 | 2s | 21 MB |
| platform-support | 18106 | 9s | 338 MB |
| compliance | 18112 / 19112 | 5s | 187 MB |
| risk-decision | 18104 / 19104 | 4s | 251 MB |
| client-master | 18101 / 19101 | 7s | 252 MB |
| commercial | 18108 / 19108 | 2s | 170 MB |
| consumer | 18109 / 19109 | 4s | 181 MB |
| payment（额外） | 18103 / 19103 | 6s | 245 MB |
| acquiring | 18102 / 19102 | 7s | 259 MB |

- `/actuator/health` 与 readiness 全部 UP，Platform 含 `gatewaySnapshot`（由迁移后的路由与 Target 实时编译）。9 份日志 ERROR 0 行。
- 内存为 `top` 采样，JVM 参数 `-Xmx320m -XX:+UseSerialGC -XX:TieredStopAtLevel=1`；元空间实测 Platform 约 99 MB、commercial 约 54 MB；堆占用 38–88 MB。

只读抽样（[sample_reads.py](../scripts/sample_reads.py)，SQL 计数与一次 HTTP 读按接口的同一口径比较）：

| 项 | SQL | HTTP | 结果 |
|---|---:|---:|---|
| Gateway 路由（Snapshot） | 277 | 277 | OK |
| Gateway Target / 其中 acquiring-service | 3 / 1 | 3 / 1 | OK |
| Platform 币种 | 1 | 1 | OK |
| 外部 API 接入方 | 5 | 5 | OK |
| 运营账号（人工、未关闭） | 5 | 5 | OK |
| 风控决策流程（CONFIGURABLE） | 3 | 3 | OK |
| 风控规则集（CONFIGURABLE） | 6 | 6 | OK |
| 风控规则（旧库 → 新库） | 425 | 425 | OK |
| 客户入驻审核案件 / 单案详情 | 34 / — | 34 / 200 | OK |
| 客户申请（旧库 → 新库） | 34 | 34 | OK |
| 收单订单（单笔，Client API） | — | 200 | OK |
| Vault 规则 client-master-service | 4 | 4 | OK |

## 4. Compose 内存调优

保留 `java/Dockerfile` 与其契约测试不变（入口参数不经环境变量导出，健康探针 JVM 不受影响）；在 `compose.applications.yaml` 用三组 `entrypoint` 锚点按服务覆盖内存参数，并给每个 Java 服务显式 `mem_limit`。

| 级别 | 服务 | 堆 | 元空间上限 | 容器上限 | 小计 |
|---|---|---|---|---:|---:|
| large | platform-support、api-gateway | 128m–384m | 256m | 768m | 1,536 MiB |
| medium | acquiring、client-master、risk-decision、compliance、payment、card、ledger、billing、operations-console-bff、client-console-bff | 64m–256m | 192m | 576m | 5,760 MiB |
| small | commercial、consumer、channel-connector、account、reconciliation、tax | 64m–160m | 160m | 448m | 2,688 MiB |

共同参数：`-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k`，代码缓存 48–64m。依据是本步实测（第 3 节）与第 14 步全链路实测（153–649 MiB）。

| 上限合计 | 调优前 | 调优后 |
|---|---:|---:|
| 18 个 Java 服务 | 13,824 MiB | 9,984 MiB |
| mor-test 全部容器（含 MySQL 1,536、Nacos 1,024、RabbitMQ 768、MinIO 512、XXL 512、Redis 256、Vault 256、Mailpit 128、Web / Nginx 192） | 19,008 MiB | 15,168 MiB |

Docker 容量核对（`docker info` 与 `docker stats`，2026-10-18 本步期间）：

| 项 | 值 |
|---|---:|
| Docker 总内存 | 20.49 GiB |
| gitlab 实际占用（无上限） | 7.75 GiB |
| nexus 实际占用（无上限） | 1.38 GiB |
| mor-local 实际占用（上限合计约 4.4 GiB） | 1.77 GiB |
| 其余可用 | 约 9.6 GiB |
| mor-test V2 预计常驻（18 个 Java 约 4.7 GiB + 基础设施约 2.2 GiB） | 约 6.9 GiB |

结论：按实测常驻，V2 全部服务可以在不停 nexus 与 mor-local 的情况下运行，余量约 2.7 GiB。上限合计 15,168 MiB 高于可用的 9.6 GiB，所有服务同时逼近上限时会内存紧张；主会话的估算没有包含 gitlab（7.75 GiB），见待确认第 9 项。

验证：`docker compose config` 渲染通过（占位口令与空 env 文件，在 scratchpad），无重复固定 IP，18 个 readiness 地址都在健康探针白名单中；`test_deploy_script.py` 与 `test_java_image_health_probe.py` 共 51 项通过。

## 5. 演练限制（与 mor-test 的差异）

1. Platform 的运营认证材料由 `GenerateOperationsMaterial.java` 为演练新生成，与 mor-test 私有材料不同，只用于让 IAM 与 ID 生成器装配；Client 认证、通知、文件、Webhook、补偿与 XXL 执行器关闭。
2. Vault 用随机根密钥，迁入的密文在演练中不可解；readiness 不依赖解密。阶段 2 用 mor-test 原 `private/vault.env`。
3. Platform 管理读接口需要有效运营会话，迁入的会话都已过期。`sample_reads.py` 在**演练副本**中把最新一条运营会话的过期时间临时延长 10 分钟，读完即恢复；它检查端口 23307 与容器主机名，不能指向 mor-test。该操作在迁移核对之后执行。
4. 未起 Gateway、两个 BFF、Web 与 Nginx（不持库，不在任务卡第 5 项范围）；Finance、card、channel-connector 只验证空库建表与初始化 DML。
5. mor-test 的 `mor_payment`、`mor_channel_connector` 实际状态未知（不在转储中），演练按"旧 init 建的空库 + 一张旧表"模拟；阶段 2 以 `plan` 的实际输出为准。

## 6. 待确认

1. **`t_command_idempotency`（mor_cct 4 行、mor_risk 96 行、mor_commerce 0 行）**：V2 删除（S10），本方案不迁入任何服务库，整表进 `mor_v2_migration_archive`。归档库保留到何时，由用户决定。
2. **争议 7 表（转储 0 行）**：design/03 归 dispute，但 mor-test 不部署 dispute-service，不建 `mor_dispute`，结构与数据进归档库。若阶段 2 时已有数据，同样只归档。是否需要先建 `mor_dispute`，请确认。
3. **`t_commerce_order` 删除列**：`context_format`（17 行同一取值）与 `preparation_command_id`（全为空）按 `order_id` 归档，不迁入。
4. **旧调用方与历史数据**：Vault 中 `client-contract-service` 的 4 条规则保留；`t_webhook_event` 中冻结的来源 `commerce-service`、审计属性中的旧服务名保留。停用旧规则建议放阶段 3。
5. **Gateway Target 与 Vault 策略用评审 SQL 修改**，没有走运营后台，因此 Platform 审计表没有对应操作记录（Vault 有一条 SEED 策略变更）。如需审计痕迹，阶段 2 可改由运营后台操作，SQL 只作核对。
6. **`mor_payment` 等被旧表占用的 V2 库名**：`migrate.sh` 默认停止，确认后用 `--park-legacy` 把旧表整体移入归档库（数据随表保留）。旧 init 给 `payment_app` 的 `ALL` 权限不收回（与 initialize.py 一致）。
7. **XXL 执行器范围**：只把 commerce-service 的补偿扫描迁到 acquiring-service。`cardOrphanInstrumentPurge`（card）、client-master 与 account 的执行器在 compose 中没有 `XXL_JOB_*` 配置，本次不登记；CURRENT_IMPLEMENTATION 的 Pending 列了 `cardOrphanInstrumentPurge`，需另行补 compose 配置。
8. **mor-test 验收脚本仍是 V1 名称**：`acceptance_flow.py`、`scenario_flow.py`、`client_api_flow.py`、`risk_rule_configuration_smoke.py` 等在 `E2E_TARGET=test` 下读 `mor_cct` / `mor_commerce`、用 `cct_app` 与 `commerce-service`；第 14 步的 V2 版本只支持本机。阶段 2 跑验收前需要先改脚本。`register_client_api.py` 会把 Target 写回 `commerce-service`，阶段 2 不得执行。
9. **Docker 内存**：gitlab 实际占用 7.75 GiB 且无上限，不在主会话"nexus + mor-local"的前提里。常驻可容纳，但上限合计不可容纳；是否给 gitlab 设上限或在阶段 2 期间停用，请决定。
10. **JVM 取舍**：`TieredStopAtLevel=1`（只用 C1）与 SerialGC 降低内存与启动开销，代价是峰值吞吐；对 test 环境建议接受。
11. **旧 RabbitMQ 队列**：`cg_commerce_payment_result`、`cg_cct_client_review`（及 DLQ）需在新消费者上线后排空并删除；有消息时先导出。
12. **结构漂移保护**：`plan` 比对五个旧库的结构指纹（`sql/legacy-schema.sha256`）。若转储之后 mor-test 有人执行过 SQL，`plan` 会停止，需要重新转储并重跑 `rehearse.sh all` 与 `generate_sql.py`。

## 7. 其他验证

- `npm --prefix requirements-site test`：137 项中 127 通过、10 失败；在起点 `f73bd1e7` 的临时 worktree 中同一命令的失败集合完全相同，属既有问题（Commerce / Payment / 台账 / 测试规范等文档断言）。
- 全部 shell 脚本 `bash -n` 通过；macOS 自带 bash 3.2 下实测运行（中文紧跟变量名的写法已全部加花括号）。

## 8. 清理

- `rehearse.sh all` 结束时删除 `mor-mt1-mysql`、`mor-mt1-redis`、`mor-mt1-rabbitmq`（均无卷）；9 个进程全部停止；18101–18112、19101–19112、28197、23307、26389、25682 无监听。
- scratchpad 中保留演练日志与随机口令文件（不在仓库）。

## 附录 A · 原库保留的 Platform / Vault 表行数（转储时刻）

**mor_platform**（56 表，23272 行）：
`t_access_tenant` 34、`t_api_credential` 61、`t_api_credential_scope` 93、`t_api_field` 444、`t_api_scope` 5、`t_audit_chain` 13、`t_audit_event` 2924、`t_audit_event_attr` 12060、`t_auth_session` 740、`t_authentication_challenge` 51、`t_command_idempotency` 187、`t_compensation_event` 0、`t_console_resource` 86、`t_file_object` 37、`t_file_owner_ref` 37、`t_file_scan_policy` 1、`t_file_scan_requirement` 1、`t_file_scan_result` 37、`t_file_upload` 37、`t_gateway_guard_rule` 1660、`t_gateway_route` 277、`t_gateway_route_header` 987、`t_gateway_route_query_param` 242、`t_gateway_target` 3、`t_integration` 5、`t_integration_api` 22、`t_integration_script` 12、`t_integration_standard_api` 22、`t_integration_verify_account` 5、`t_integration_verify_case` 27、`t_integration_verify_run` 94、`t_integration_verify_run_step` 141、`t_jwt_signing_key` 2、`t_notification_attempt` 0、`t_notification_delivery` 0、`t_notification_provider` 1、`t_notification_request` 0、`t_notification_route` 3、`t_notification_template` 3、`t_notification_template_content` 3、`t_password_credential` 70、`t_permission` 74、`t_refresh_token_generation` 834、`t_role` 160、`t_role_permission` 658、`t_security_event` 798、`t_standard_api` 6、`t_tenant_invitation` 8、`t_tenant_member` 65、`t_tenant_member_role` 105、`t_user_account` 71、`t_webhook_attempt` 16、`t_webhook_delivery` 16、`t_webhook_endpoint` 9、`t_webhook_event` 16、`t_webhook_signing_secret` 9。

**mor_vault**（6 表，1775 行）：
`t_vault_access_event` 1293、`t_vault_access_rule` 18、`t_vault_data_type` 7、`t_vault_policy_change` 15、`t_vault_protect_request` 338、`t_vault_record` 104。

## 附录 B · 40 核对中非空的 81 项（其余 69 项两侧均为 0 行且一致）

| 核对项 | 旧侧行数 | 新侧行数 | 结果 |
|---|---:|---:|---|
| `mor_cct.t_application_document->mor_client_master` | 35 | 35 | OK |
| `mor_cct.t_business_change_record->mor_client_master [split]` | 32 | 32 | OK |
| `mor_cct.t_business_change_version->mor_client_master [split]` | 57 | 57 | OK |
| `mor_cct.t_client->mor_client_master` | 32 | 32 | OK |
| `mor_cct.t_client_application->mor_client_master` | 34 | 34 | OK |
| `mor_cct.t_client_business_classification->mor_client_master` | 25 | 25 | OK |
| `mor_cct.t_client_business_profile->mor_client_master` | 32 | 32 | OK |
| `mor_cct.t_individual_profile->mor_client_master` | 32 | 32 | OK |
| `mor_cct.t_organization_profile->mor_client_master` | 2 | 2 | OK |
| `mor_cct.t_party->mor_client_master` | 34 | 34 | OK |
| `mor_cct.t_party_address->mor_client_master` | 34 | 34 | OK |
| `mor_cct.t_party_contact->mor_client_master` | 58 | 58 | OK |
| `mor_cct.t_party_identity_key->mor_client_master` | 32 | 32 | OK |
| `mor_cct.t_party_profile->mor_client_master` | 34 | 34 | OK |
| `mor_cct.t_party_profile_identifier->mor_client_master` | 34 | 34 | OK |
| `mor_cct.t_party_relation->mor_client_master` | 2 | 2 | OK |
| `mor_commerce.t_checkout_session->mor_acquiring` | 17 | 17 | OK |
| `mor_commerce.t_commerce_order->mor_acquiring` | 17 | 17 | OK |
| `mor_commerce.t_compensation_event->mor_acquiring [split]` | 5 | 5 | OK |
| `mor_commerce.t_consumer_contract->mor_acquiring` | 17 | 17 | OK |
| `mor_commerce.t_order_context->mor_acquiring` | 17 | 17 | OK |
| `mor_commerce.t_order_funds_availability_projection->mor_acquiring` | 17 | 17 | OK |
| `mor_commerce.t_order_item->mor_acquiring` | 32 | 32 | OK |
| `mor_commerce.t_order_legal->mor_acquiring` | 17 | 17 | OK |
| `mor_commerce.t_order_payment_projection->mor_acquiring` | 5 | 5 | OK |
| `mor_commerce.t_order_refund_position->mor_acquiring` | 17 | 17 | OK |
| `mor_commerce.t_refund_request->mor_acquiring` | 5 | 5 | OK |
| `mor_risk.t_action_parameter_definition->mor_risk_decision` | 4 | 4 | OK |
| `mor_risk.t_action_version->mor_risk_decision` | 8 | 8 | OK |
| `mor_risk.t_checkout_input_snapshot->mor_risk_decision` | 17 | 17 | OK |
| `mor_risk.t_dd_case_classification->mor_compliance` | 25 | 25 | OK |
| `mor_risk.t_dd_case_dependency->mor_compliance` | 4 | 4 | OK |
| `mor_risk.t_dd_case_state_change->mor_compliance` | 71 | 71 | OK |
| `mor_risk.t_dd_case_subject->mor_compliance` | 38 | 38 | OK |
| `mor_risk.t_dd_evidence_ref->mor_compliance` | 38 | 38 | OK |
| `mor_risk.t_dd_program->mor_compliance` | 3 | 3 | OK |
| `mor_risk.t_dd_program_requirement->mor_compliance` | 10 | 10 | OK |
| `mor_risk.t_dd_program_version->mor_compliance` | 3 | 3 | OK |
| `mor_risk.t_dd_requirement->mor_compliance` | 118 | 118 | OK |
| `mor_risk.t_decision_evaluation->mor_risk_decision` | 22 | 22 | OK |
| `mor_risk.t_decision_flow->mor_risk_decision` | 4 | 4 | OK |
| `mor_risk.t_decision_flow_edge->mor_risk_decision` | 21 | 21 | OK |
| `mor_risk.t_decision_flow_end_output->mor_risk_decision` | 2 | 2 | OK |
| `mor_risk.t_decision_flow_node->mor_risk_decision` | 27 | 27 | OK |
| `mor_risk.t_decision_flow_version->mor_risk_decision` | 6 | 6 | OK |
| `mor_risk.t_decision_point->mor_risk_decision` | 2 | 2 | OK |
| `mor_risk.t_decision_point_input_field->mor_risk_decision` | 304 | 304 | OK |
| `mor_risk.t_decision_point_rule_set->mor_risk_decision` | 33 | 33 | OK |
| `mor_risk.t_decision_point_version->mor_risk_decision` | 35 | 35 | OK |
| `mor_risk.t_decision_release->mor_risk_decision` | 13 | 13 | OK |
| `mor_risk.t_due_diligence_case->mor_compliance` | 38 | 38 | OK |
| `mor_risk.t_evaluation_feature_result->mor_risk_decision` | 10 | 10 | OK |
| `mor_risk.t_evaluation_flow_trace->mor_risk_decision` | 38 | 38 | OK |
| `mor_risk.t_evaluation_rule_trace->mor_risk_decision` | 32 | 32 | OK |
| `mor_risk.t_feature_version->mor_risk_decision` | 2 | 2 | OK |
| `mor_risk.t_list_feature_definition->mor_risk_decision` | 1 | 1 | OK |
| `mor_risk.t_qualification_decision->mor_compliance` | 27 | 27 | OK |
| `mor_risk.t_release_audit_event->mor_risk_decision` | 118 | 118 | OK |
| `mor_risk.t_risk_list->mor_risk_decision` | 1 | 1 | OK |
| `mor_risk.t_risk_list_entry->mor_risk_decision` | 1 | 1 | OK |
| `mor_risk.t_risk_rule->mor_risk_decision` | 425 | 425 | OK |
| `mor_risk.t_risk_variable->mor_risk_decision` | 35 | 35 | OK |
| `mor_risk.t_risk_variable_member->mor_risk_decision` | 12 | 12 | OK |
| `mor_risk.t_rule_action->mor_risk_decision` | 432 | 432 | OK |
| `mor_risk.t_rule_action_parameter->mor_risk_decision` | 24 | 24 | OK |
| `mor_risk.t_rule_node->mor_risk_decision` | 425 | 425 | OK |
| `mor_risk.t_rule_operand->mor_risk_decision` | 850 | 850 | OK |
| `mor_risk.t_rule_reject_reason->mor_risk_decision` | 4 | 4 | OK |
| `mor_risk.t_rule_set->mor_risk_decision` | 30 | 30 | OK |
| `mor_risk.t_rule_set_version->mor_risk_decision` | 38 | 38 | OK |
| `mor_risk.t_rule_subject_tag->mor_risk_decision` | 3 | 3 | OK |
| `mor_risk.t_transaction_risk_assessment->mor_risk_decision` | 5 | 5 | OK |
| `mor_risk.t_velocity_event->mor_risk_decision` | 18 | 18 | OK |
| `mor_risk.t_velocity_event_subject->mor_risk_decision` | 57 | 57 | OK |
| `mor_risk.t_velocity_feature_definition->mor_risk_decision` | 1 | 1 | OK |
| `mor_cct.t_command_idempotency->mor_v2_migration_archive row_count` | 4 | 4 | OK |
| `mor_risk.t_command_idempotency->mor_v2_migration_archive row_count` | 96 | 96 | OK |
| `mor_commerce.t_commerce_order retired columns row_count` | 17 | 17 | OK |
| `mor_cct.t_business_change_record coverage row_count` | 32 | 32 | OK |
| `mor_cct.t_business_change_version coverage row_count` | 57 | 57 | OK |
| `mor_commerce.t_compensation_event coverage row_count` | 5 | 5 | OK |
