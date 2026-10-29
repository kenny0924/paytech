# REQ-12 业务信息补采 · mor-test 现网基线（阶段 6，2026-10-29 16:20—16:55 +04 只读读取）

> 读取方式：`docker inspect`、`docker exec … printenv`（只取键名）、`docker exec … sha256sum`、`docker cp` 从容器**取出** JAR / dist / health-probe、root 只读会话（`SET SESSION TRANSACTION READ ONLY`，`@@transaction_read_only = 1` 已确认）、`mysqldump --single-transaction` 只读导出到本机 `~/Desktop/Workspace/Code-MoR-backups/rq12-20261029/`（含数据，不入库）。
> 未停 / 起 / 重启任何容器，未写任何库，未调用 Platform 写接口，未发布语言包，未 `docker cp` 写入容器。

## 1. 容器镜像（= 回滚目标）

| 服务 | 运行镜像 ID | 容器启动（UTC） | JAR / dist | 来源 |
|---|---|---|---|---|
| client-master-service | `sha256:c9bb7b8e30f6a44ae528b5054fd15a67e52a52743fc4fbed11436b08f21b8155` | 10-28 05:41 | JAR `a416b537b9a8caf9`（容器内 `sha256sum` 与取出文件一致） | 国家包法务修订 legal-20261028a（`ac3145f4`） |
| client-console-bff | `sha256:8fe7f24c3ef7e08fd5473e73a68f80f72248db607492b0223f2e25d327ce2a3b` | 10-28 05:42 | JAR `e91a2bc5401fb47d` | 国家包法务修订 legal-20261028a |
| client-console-web | `sha256:3db62571940f24d50c87fa2e07143e3536a2b956ffb6db8542994bcd5011a9eb` | 10-28 05:42 | dist 11 个文件（`evidence/client-console-web-dist-live.sha256`） | 国家包法务修订 legal-20261028a |
| operations-console-web | `sha256:96992aba577492309ef4932e2b68cc507bdd587b26c8edb40c7127967ed0312d` | 10-29 09:44 | dist 255 个文件（`evidence/operations-console-web-dist-live.sha256`） | REQ-05 wfdq-20261029a（`af972805`） |
| operations-console-bff | `sha256:6833f484994be41f72ee3fb3ee73006514b9089e5a5d4330af6e3bc245c597bd` | 10-29 09:43 | — | REQ-05 wfdq-20261029a（`af972805`）——**本需求不发布** |
| commercial-service | `sha256:806043a320cfce7eaaf0eed19d0ef202487ec0a6559aaa5b74f8fe3d1085f728` | 10-28 20:10 | — | REQ-01；只被读，不发布 |

### 1.1 基线证明（候选源码不回退现网）

候选基线 = `6753faba`（main HEAD；`git log 6753faba..main` 为空），干净 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-rq12-rel`，先不加本需求文件构建（`-am clean package -DskipTests`，私有仓库 `~/.m3-rq12`；Web `npm ci && npm run build`）：

| 项 | 方法 | 结果 |
|---|---|---|
| client-console-web | dist 与 `docker cp mor-test-client-console-web-1:/usr/share/nginx/client-console/.` 逐文件 SHA-256 | **11 个文件逐字节相同** |
| operations-console-web | 同上（`/usr/share/nginx/operations-console`） | **255 个文件逐字节相同** |
| client-master | `harness/jarcmp.py`（条目 CRC，排除 MANIFEST / maven 元数据 / 索引） | 现网 1883 项、base 1900 项：**现网独有 0**；差异只在随包的 Owner API 与公共目录——`paytech-commercial-api`（REQ-01 开通项前置门禁新增 / 改动的 agreement 类）、`paytech-platform-support-api`（REQ-05 workflow 类）、`paytech-messaging!catalog/events.yaml`（REQ-01）。client-master 自身类与资源 **0 差异**；client-master 不引用 `AgreementApi` 与 workflow API |
| client-console-bff | 同上 | 现网 1022 项、base 1029 项：**现网独有 0**；差异只在 `paytech-platform-support-api` 的 workflow 类（REQ-05）；BFF 自身 0 差异，不引用 workflow API |

结论：现网四个服务没有 `6753faba` 以外的内容（`ac3145f4..6753faba` 之间 client-master / client-console-bff / client-console-web 没有提交）。候选 = `6753faba` + 本需求文件，不回退任何现网工作。

### 1.2 不发布的服务

| 服务 | 判定 |
|---|---|
| operations-console-bff | 现网 = REQ-05 `af972805` 构建；`af972805..6753faba` 只改两份文档（`CURRENT_IMPLEMENTATION.md`、REQ-05 `delivery.md`）；REQ-12 不改 ops-bff（values 通用透传）。兼容：其 client-master-api 与本需求只差 JavaDoc（字段与 JSON 形状不变） |
| client-console-bff | **不需要发布**：本需求只给它加了 1 个测试；现网已是法务修订构建（含 `ac3145f4` 的 BFF 改动，阶段 5 本机发现的"BFF 早于法务修订"问题只存在于本机，不在 mor-test）；与 base 只差随包的 workflow API 类（不使用）。form-schema 新增的 `MULTI_SELECT`、`COMMERCIAL_PAYMENT_METHOD` 与 values 新键都是已有字段中的字符串值，严格解码兼容（阶段 5 本机以 base 构建的 BFF 端到端验证） |
| commercial-service | 只被读：client-master 经服务发现 `commercial-service` 调用 `GET /products/v1/capabilities?status=ACTIVE`；现网 commercial 与 client-master 均无 internal-auth 配置（默认关闭）；client-master 已在调用 commercial 的合同参与方查询，近 24 小时无失败日志 |
| api-gateway / Platform 注册 | 不换、不注册：本需求无新接口，复用已有门户与运营路由 |

## 2. 容器环境变量键（只记键名）

| 服务 | 键 |
|---|---|
| client-master-service（40） | 主 compose + 法务修订覆盖：`SPRING_DATASOURCE_*`、`SPRING_DATA_REDIS_*`、`CCT_RABBITMQ_*`、`MESSAGING_*`、`ONBOARDING_{DRAFT_ENABLED,RECOVERY_ENABLED,ID_DATACENTER,ID_WORKER}`、`COMPENSATION_INSTANCE_ID`、`XXL_JOB_*`、`SPRING_CLOUD_NACOS_DISCOVERY_IP` 等 + 镜像自带 |
| client-console-web / operations-console-web（各 7） | 仅 nginx 镜像自带 |

`scripts/env_check.sh` 渲染本目录 6 个覆盖文件，与运行容器**逐键逐值相同**（`evidence/env-check-20261029.txt`）。本需求不新增 env（commercial 服务发现名已在配置中）。

## 3. mor_client_master（只读，`precheck/mor_client_master-precheck-20261029.out`）

| 项 | 读数 |
|---|---|
| 新列 | `t_client_business_profile` 4 列均不存在 |
| ID 段 `9281200000…` | Schema / 字段 / 目录项 0 行；`FULFILLMENT_TYPE` 与 `COMMERCIAL_` 目录行 0；4 个新 field_key 0 |
| Schema | ACTIVE 9（8 个 v2 + SA 自由职业者 v1，ID `9270000000900301—309`）；v1 RETIRED 8；字段 602；v2 字段 ID `9270000000910001—9270000000950002`（在复制段内） |
| BUSINESS 最大序号（D-2） | 9 个现网 v2 依次 2190 / 2200 / 2240 / 2270 / 2200 / 2250 / 2190 / 2240 / 2250，**与 DML 静态值完全相同** |
| 在途申请（全部冻结 v1，v3 发布不影响） | UNDER_REVIEW 8（v1：700001 ×4、700002、700004、700005、700007）、DRAFT 3（700001）、WITHDRAWN 6、APPROVED 37（无 Schema，遗留） |
| 经营资料 | 51 行 |

## 4. commercial（只读，`precheck/mor_commercial-payment-methods-20261029.out`）

ACTIVE PAYMENT_METHOD 4 项，按可开通项 ID：`STC_PAY`（238410801365454848）、`APPLE_PAY`、`GOOGLE_PAY`、`CARD`——门户选项即此顺序。`CARD` 的 `capability_name` 为合法 UTF-8 `银行卡`（`E993B6E8A18CE58DA1`，列 utf8mb4），阶段 2 看到的 `???` 是终端字符集显示问题；client-master 不使用 `capability_name`，门户 / 运营文案取 `onboardingSchema.paymentMethod.<代码>` 语言包键，**不受影响**。

## 5. 语言包（只读 dry-run，`precheck/i18n-plan-dryrun-*.out`）

| 应用 | 当前 | 计划 | 新增（按命名空间） | 基准变更 / 废弃 / 恢复 | zh-CN 导入 |
|---|---|---|---|---|---|
| client-console | **#7** | #8 | 23（onboarding-schema 17、onboarding 6） | 0 / 0 / 0 | 23（同上，其余 1454 条保持线上） |
| operations-console | **#21** | #22 | 22（client-review 9、onboarding-schema 13） | 0 / 0 / 0 | 22（其余 10 419 条保持线上） |

不新建命名空间；`zhChangedNotImported` 均为 0（client-console 沿用法务修订的 `onboarding.page.*` 例外）。
