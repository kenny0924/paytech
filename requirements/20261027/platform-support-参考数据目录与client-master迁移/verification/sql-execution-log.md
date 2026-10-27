# SQL 执行记录

## 阶段 4（pf-dev / cm-dev，本地 auto 测试库）

| 库 | 文件 | 备份 | 结果 |
|---|---|---|---|
| `mor_platform` | `changes/2026-10-27-reference-data.sql` | `/Users/kenny/mor-backups/refdata-20261027/mor_platform.sql.gz` | 见 [pf-dev.md](./pf-dev.md) 第 2 节 |
| `mor_client_master` | `changes/2026-10-27-reference-data-catalog.sql` + `-comment.sql` | `/Users/kenny/mor-backups/refdata-20261027/mor_client_master.sql.gz` | 见 [cm-dev.md](./cm-dev.md) 第 2 节 |

## 阶段 5（e2e-dev，本机 e2e 栈，2026-10-27，时间为 UTC）

本机 e2e 服务实际读取的库：Platform → `mor_platform_v2e2e`，client-master → `mor_client_master_v2e2e`（`mor_platform` / `mor_client_master` 是 auto 测试库，不被 e2e 进程使用）。执行前两库都还是旧结构（`t_currency_spec`、`t_country_region`，`t_catalog_item` 含 48 行参考数据）。SQL 用 root 经 `docker exec mor-local-mysql-1` 执行，口令只经 `MYSQL_PWD` 传入。

| 时间 | 库 | 步骤 | 备份 / 前置 | 结果（前 → 后） |
|---|---|---|---|---|
| 10:39 | 两库 | 备份 | `/Users/kenny/mor-backups/refdata-20261027/e2e-mor_platform_v2e2e.sql.gz`（sha256 `82b5cf2b…`）、`e2e-mor_client_master_v2e2e.sql.gz`（`89a1045c…`）；`--single-transaction --routines --triggers` | 两个 dump 均以 `Dump completed` 结尾 |
| 10:40 | `mor_platform_v2e2e` | `block139-precheck.sql`（只读） | — | ID 区间 7 表全 0；新 code 0；新路径 0；依赖 `CLIENT_ONBOARDING_CLASSIFICATION_EDIT` 1、PLATFORM_ADMIN 1 |
| 10:40:21 | — | 停旧 Platform（`swap.py stop platform`） | 改名后旧镜像无法读新表名 | pid 10577 停止 |
| 10:40 | `mor_platform_v2e2e` | `paytech/platform-support/docs/database/sql/changes/2026-10-27-reference-data.sql`（worktree） | 预检 PRE 通过 | 旧表 `t_currency_spec`、`t_country_region` 不存在；`t_ref_currency` 1 → 4（USD + AED / SAR / EGP 1002—1004，全 ACTIVE，数字码 840 / 784 / 682 / 818）；`t_ref_country` 249（三位码 / 数字码 / 英文名 249 行全有）；`t_ref_country_subdivision` 0 → 47（AE 7、SA 13、EG 27）；`t_ref_industry` 0 → 42（1 级 10、2 级 32）；`t_ref_mcc` 0 → 298（高风险 14）；`ref_data_assert` 过程已删除 |
| 10:40 | — | 新 Platform 启动 | — | readiness UP；无 ERROR；旧接口 `/currency-specs`、`/country-regions` 形状与执行前一致（见报告第 3 节） |
| 10:41 | `mor_platform_v2e2e` | `registration-block139.sql`（休眠） | 预检全部符合 | permission 1、role_permission 1、console_resource 1、route 5、header 10、query 11、guard 30（共 59）；Gateway 快照 routeCount 497 → 497 |
| 10:41 | `mor_platform_v2e2e` | `block139-activate.sql` | — | routes 5、resources 1、grants 1、role 1；Gateway 快照 routeCount 497 → **502**（10:41:38 首次刷新） |
| 10:41:58 | `mor_client_master_v2e2e` | `paytech/client-master/docs/database/sql/changes/2026-10-27-reference-data-catalog.sql` | — | 退役目录项 48 → 0；`business.currency` 8 行 CURRENCY → REF_CURRENCY；地址组件 2 行 → REF_COUNTRY_SUBDIVISION；下划线省州 13 → 0、ISO 省州 0 → 13（全部为 `AE_AZ` → `AE-AZ`） |
| 10:42 | `mor_client_master_v2e2e` | `2026-10-27-reference-data-catalog-comment.sql` | — | 3 列注释更新；`catalog_code` 类型 / 可空 / 排序规则与备份一致（varchar(64)，utf8mb4_0900_ai_ci） |

### 环境修复：注册块 125（不是本需求的 SQL；用户 2026-10-27 批准，只在本机 `mor_platform_v2e2e`）

本机 e2e 库从未注册币种控制台（块 125），币种管理接口与页面 404 `GATEWAY_ROUTE_NOT_FOUND`，阻塞 AC-13 币种部分。其他缺失的块（120—122、124、128、133）按指示未补。

| 时间 | 步骤 | 结果 |
|---|---|---|
| 11:41 | 前置核对 | target 900000000000000001、PLATFORM_ADMIN 角色、平台配置菜单 562 各 1；块 125 ID 区间 0 行 |
| 11:41 | worktree `platform-support-initial-data.sql` 第 5541—5629 行（"ID block 125" 自包含段，一个事务） | permission 2、role_permission 2、console_resource 2、route 4、header 20、guard 24（54 行）；Gateway routeCount 502 → 502（休眠） |
| 11:42 | `requirements/20261020/platform-support-币种主数据维护与读取缓存/release/test-activate.sql` | routes 4、resources 2、grants 2、role 1；Gateway routeCount 502 → **506** |
| 11:43 | 验收新建 DRAFT 币种 XTS（963，ISO 测试代码）并修改 | 页面冒烟后于 11:58 删除：`DELETE FROM t_ref_currency WHERE currency_code='XTS' AND status='DRAFT'`（1 行）；之后币种为 AED / EGP / SAR / USD 4 个 ACTIVE，`/currency-specs` 回到 535 字节 |

回退块 125：`release/test-deactivate.sql`（同一需求目录），再按 ID 900000000000125101—900000000000125799 删除（guard → header → route → console_resource → role_permission → permission）。

### 行政区 local_name 改用 CLDR 阿拉伯文名（用户决定 E-2）

| 时间 | 库 | 文件 | 结果 |
|---|---|---|---|
| 11:50 | `mor_platform`（auto 测试库，pf-dev 备份 `mor_platform.sql.gz`） | worktree `changes/2026-10-27-reference-data-subdivision-local-name.sql`（列注释 + 47 行 UPDATE） | before 47 行 / NULL 0 → after 47 / 0；30 行值改变；重复执行为空操作。随后把这 30 行 version 2 → 1，使测试库与测试 `ddl.sql` 种子（version 1、新名称）一致 |
| 11:50 | `mor_platform_v2e2e`（备份 `e2e-mor_platform_v2e2e.sql.gz`） | 同上 | 47 / 0 → 47 / 0；30 行改变（version 1 → 2）、17 行与 CLDR 原本相同；重复执行为空操作；AE-DU = `امارة دبي`、SA-01 = `منطقة الرياض`、EG-C = `محافظة القاهرة` |

回退：`changes/2026-10-27-reference-data-subdivision-local-name-rollback.sql`（恢复原手写名称与原列注释）。

### 语言包重新发布：MCC 全部中文（用户决定，取代本机英文填充）

| 应用 | zh-CN 导入 | 发布 | 核对 |
|---|---|---|---|
| client-console | 266（此前填为英文名的 MCC） | **#2** READY，20 / 20 | 线上 zh-CN refdata 387 条与 `release/i18n-bundles/client-console.refdata.zh-CN.json` 逐条相同 |
| operations-console | 266 | **#4** READY，52 / 52 | 与 `operations-console.refdata.zh-CN.json` 逐条相同 |

记录：`verification/i18n-local-*.json`（第二份）。refdata 每个应用 387 个 key，zh-CN 387 个。

### 验收中的临时数据改动（均已恢复并核对）

| 时间 | 改动 | 目的 | 恢复 |
|---|---|---|---|
| 10:52—11:33（多次） | `t_ref_country_subdivision` AE-UQ、`t_ref_country` AQ、`t_ref_mcc` 5655 / 5411、`t_ref_currency` EGP 临时置 INACTIVE（每次数秒） | 停用编码的提交 / 分类拒绝（B3、AC-07、AC-08） | 脚本 `finally` 改回 ACTIVE 并回读；11:33 核对五表全部 ACTIVE（币种 4、国家 249、行政区 47、行业 42、MCC 298） |
| 10:59（约 3 秒） | `RENAME TABLE t_ref_country / t_ref_industry` → `*__stage5_off`，随后改回 | 只让参考数据接口不可用（AC-09），Platform、Gateway 认证、文件不受影响 | 改回后接口 200；11:33 核对无 `*stage5*` 表 |

### 本机语言包（Platform 多语言，经 Nginx → Gateway → ops-bff）

| 应用 | 新建命名空间 | key 导入 | zh-CN 导入 | 发布 |
|---|---|---|---|---|
| client-console | auth、common、developer、enum、invitation、onboarding-schema、onboarding、team、workspace、**refdata**（本机此前没有任何命名空间与发布） | 新增 1381、废弃 0 | 1381 | **#1** READY，语言包 20 / 20 |
| operations-console | onboarding-schema、**reference-data**、**refdata** | 新增 1245、基准变更 1、废弃 11（本需求删除的分类 / 币种页旧文案） | 1246 | **#3** READY，语言包 52 / 52 |

记录：`verification/i18n-local-20261027T104705Z.json`。首次发布时 refdata 的 zh-CN 只覆盖 121 / 387（S-09），而两个应用要求 zh-CN 覆盖率 100%，本机临时把其余 266 个 MCC 的 zh-CN 填为英文名；用户随后决定全部翻译，已用正式译名替换并重新发布（见下方"语言包重新发布"）。

## 回退（本机，按顺序）

1. 停 ops-bff、client-bff、client-master、Platform。
2. `mor_platform_v2e2e`：`block139-deactivate.sql` → `block139-rollback.sql`（需求目录 `db/platform-support/`）。
3. `mor_client_master_v2e2e`：`changes/2026-10-27-reference-data-catalog-rollback.sql`（注释 DDL 不必回退）。
4. `mor_platform_v2e2e`：`changes/2026-10-27-reference-data-rollback.sql`（反向 RENAME、删列与新表、删除 1002—1004）。
5. 换回 `*.pre-refdata` JAR 并按捕获的 argv / 环境重启（见 stage5-report.md 第 8 节）；如 Platform 登录 503 `vault-service`，`docker restart mor-local-vault-service-1`。
6. 语言包：未验证撤销发布的方式；回退时可保留 client-console #1、operations-console #3（旧前端不读 refdata / reference-data 命名空间），或从第 1 行的 e2e 整库备份恢复 `mor_platform_v2e2e`（会同时撤销上面第 2、4 步）。

| 2026-10-27 | 主会话清理验收 DRAFT 币种 XTS | `mor_platform_v2e2e.t_ref_currency` 现为 AED / EGP / SAR / USD 4 行 ACTIVE |
