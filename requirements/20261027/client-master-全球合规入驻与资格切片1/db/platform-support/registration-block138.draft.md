# Platform 注册块 138 · 预研草稿（已被 block138-spec.json + gen_block138.py 生成的 SQL 取代；本文保留为推导记录）

> 用途：为全球合规入驻切片 1 的 Gateway 路由、权限、菜单与按钮登记预先确定清单形状、既有 ID 解析方式、Guard 与 Header 取值和预编译门禁。
> **最终 SQL 等 BFF 外部路径定稿后由主会话另派任务生成**；本文件中的外部 / 下游路径、权限码与菜单父级均为占位建议，用 `TBD` 标出。
> 依据：technique.md 2.1（CM-1—CM-13）、2.2（CP-1—CP-6）、2.3「Platform 块 138」、S-05、S-07、S-11；analysis/stage2-frontends.md 第 5 节；
> 块 137 样板 `requirements/20261023/commercial-商业关系快照与真实解析接入/db/platform-support/registration-block137.sql` 及其 release/sql/61、62、69；
> 既有入驻注册 `requirements/20260914/client-console-注册与客户入驻/db/registration-plan.json`。
> 只读核对来源：本机 `mor_platform_v2e2e`（2026-10-27）。**mor-test 的 ID 与 Guard 值须在定稿前重新只读查询**，本机值不能直接搬用。

> **定稿记录（2026-10-27，主会话最终决定）**：OPERATIONS 部分已定稿为 `block138-spec.json` → `gen_block138.py` 生成的 `block138-precheck.sql`、`registration-block138.sql`、`block138-activate.sql`、`block138-deactivate.sql`、`block138-rollback.sql`（217 行：permission 7、role_permission 7、console_resource 7、route 17、header 66、query_param 11、guard 102；路由码按 ops-fe 定名，o01 增 responsiblePartnerId 与 jurisdictionCode）。
> 与本文差异以 spec 为准：Client 菜单在商务下 sort 80；入驻配置 310、入驻码 320 为顶级；risk-rating 复用 `CLIENT_ONBOARDING_CLASSIFICATION_EDIT`（无 RISK_RATE 权限与按钮）；检查结果复用 `CLIENT_ONBOARDING_EVIDENCE_REVIEW`；新权限 7 个（ONBOARDING_CONFIG_READ、CLIENT_READ、ONBOARDING_CODE_READ、ONBOARDING_CODE_MANAGE、CLIENT_ONBOARDING_SCREENING、CLIENT_ONBOARDING_RECOMMEND、CLIENT_ONBOARDING_WAIVE）；激活停用 c03、停用与回滚恢复 c03；OPERATIONS 写路由转发 idempotency-key。
> **G4 程序查看路由已于 2026-10-27 重新加入**（`operations-onboarding-config-programs`，白名单 `jurisdiction`）；此前一度删除：截至当时 compliance（cmp-dev）与 ops-fe 均未确认程序只读 API。缺口：运营控制台入驻配置页暂无程序查看；有 API 后以补充块或另一 spec 增加（`ONBOARDING_CONFIG_READ` 权限可复用）。
> CLIENT 路由为补充件（已生成 `block138-client-*`，c17—c21；c18 白名单含 applicationId）：client-fe 答复后生成 `block138-client-*`（spec `block138-client-spec.json`，idStart route 441、header 581、query 651、guard 901，与 OPERATIONS 区间不重叠）；CP-4 结果路由若路径不变则不新增。

## 1. 交付文件（定稿时）

| 文件 | 内容 |
|---|---|
| `db/platform-support/registration-block138.sql` | 休眠注册：permission ACTIVE、PLATFORM_ADMIN（role `900000000000000510`）授权 REVOKED、菜单 / 按钮 / 路由 DISABLED；逐字追加到 `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` 末尾 |
| `release/sql/60-platform-block138-registration.sql` | 同上（发布副本） |
| `release/sql/61-platform-block138-activate.sql` | 路由 ACTIVE、资源 ACTIVE、授权 ACTIVE、role.version+1 |
| `release/sql/62-platform-block138-deactivate.sql` | 激活的逆操作 |
| `release/sql/69-platform-block138-rollback.sql` | 按自身 ID 段删除：guard_rule → query_param → route_header → gateway_route → console_resource（按钮先于菜单）→ role_permission → permission |
| 预编译证据 | `assets/harness/block_compile_check.sh` 在 baseline / dormant / active / deactivated / rolled-back 五态均 `COMPILE OK` |

ID 区间 `900000000000138001—900000000000138999`：`1381xx` permission、`1382xx` role_permission、`1383xx` console_resource、`1384xx` gateway_route、`1385xx` route_header、`1386xx` query_param、`1387xx` guard_rule。
本机核对：块 138 区间 0 行（`t_gateway_route`）；仓库 initial-data 末块为 137。

## 2. 既有对象：用子查询解析，不写死 ID

既有入驻注册由 Platform 管理 API 生成雪花 ID，各环境不同（本机示例见括号）。块 138 引用它们一律用子查询：

| 对象 | 解析方式 | 本机值（仅供核对） |
|---|---|---|
| Client 通道 target | `(SELECT target_id FROM t_gateway_target WHERE target_service_code = 'client-console-bff' AND channel = 'CLIENT')` | 236033358134513664 |
| Operations 通道 target | 固定 `900000000000000001`（`operations-console-bff`，initial-data 种子） | 900000000000000001 |
| 权限 `CLIENT_ONBOARDING_REVIEW_READ` | `(SELECT permission_id FROM t_permission WHERE permission_code = 'CLIENT_ONBOARDING_REVIEW_READ')` | 236033358407143424 |
| 权限 `CLIENT_ONBOARDING_CLASSIFICATION_EDIT` | 同上按 code | 236033358574915584 |
| 权限 `CLIENT_ONBOARDING_EVIDENCE_REVIEW` | 同上按 code | 236033358742687744 |
| 权限 `CLIENT_ONBOARDING_RETURN` | 同上按 code | 236033358893682688 |
| 权限 `CLIENT_ONBOARDING_DECIDE` | 同上按 code | 236033359078232064 |
| 菜单 `CLIENT_ONBOARDING_REVIEW`（客户入驻审核，`/client-onboarding/reviews`，顶级，sort 300） | `(SELECT resource_id FROM t_console_resource WHERE resource_code = 'CLIENT_ONBOARDING_REVIEW')` | 236034943153606656 |
| 按钮 `CLIENT_ONBOARDING_CLASSIFY` / `_REVIEW_EVIDENCE` / `_RETURN` / `_DECIDE` | 按 resource_code | 父级为上行菜单 |

注意：既有资源码没有 `OPERATIONS_` 前缀（`CLIENT_ONBOARDING_REVIEW`，component_key 才是 `OPERATIONS_CLIENT_ONBOARDING_REVIEW`）；新按钮挂在该菜单下时 `parent_resource_id` 用子查询。
MySQL 不允许 `INSERT … VALUES` 中引用同表子查询的场景只出现在 `t_console_resource` 自引用父级：父级是既有行时用 `INSERT … SELECT … FROM t_console_resource WHERE resource_code = …` 形式（块 137 父菜单是固定 ID，无此问题）。
回滚只按自身 ID 段删除，不触碰既有行。

## 3. 路由清单模板

外部路径约定：Client `/api/client/<bff 前缀>/v1/…` → client-console-bff；Operations `/api/operations/<bff 前缀>/v1/…` → operations-console-bff，下游路径 = 去掉 `/api/<channel>` 前缀（与既有路由一致）。BFF 前缀待 BFF agent 定稿。

### 3.1 CLIENT 通道（target client-console-bff，CLIENT_JWT + HUMAN_AUTHENTICATED，无 permission）

| # | 来源 | Method | 外部路径（建议） | 下游路径（建议） | Body 上限 | Query 白名单 |
|---|---|---|---|---|---|---|
| C1 | CM-1 | GET | `/api/client/onboarding/v1/jurisdictions` | `/client-onboarding/v1/jurisdictions` | 0 | — |
| C2 | CM-2 | GET | `/api/client/onboarding/v1/form-schema` | `/client-onboarding/v1/form-schema` | 0 | `jurisdiction, partyType, organizationType` |
| C3 | CM-6 | POST | `/api/client/onboarding/v1/applications/{applicationId}/declarations` | `/client-onboarding/v1/applications/{applicationId}/declarations` | 16384 | — |
| C4 | CM-7 | GET | `/api/client/onboarding/v1/applications/{applicationId}/requirements` | `/client-onboarding/v1/applications/{applicationId}/requirements` | 0 | — |
| C5 | CM-8 | POST | `/api/client/onboarding/v1/applications/{applicationId}/withdrawal` | `/client-onboarding/v1/applications/{applicationId}/withdrawal` | 16384 | — |
| — | CP-4 | GET | TBD：若 BFF 把审核结果并入既有 `c05` 申请详情或 `c15` review-history，则**不新增路由** | — | — | — |
| — | CM-3 入驻码 | — | 不新增：创建申请沿用既有 `client-onboarding-c04`（请求体加 `onboardingCode`，Body 上限 16384 已足够） | — | — | — |
| — | 删除 | GET | `client-onboarding-c03 /api/client/onboarding/v1/form-options` 被 CM-2 取代：块 138 不删除既有行，前端切换后由激活脚本置 DISABLED（或后续块清理），需主会话决定 | — | — | — |

route_code 建议：`client-onboarding-jurisdictions`、`client-onboarding-form-schema`、`client-onboarding-declaration-create`、`client-onboarding-requirements`、`client-onboarding-withdrawal`。

### 3.2 OPERATIONS 通道（ops-fe 2026-10-27 确定）

target `900000000000000001`，OPERATIONS_JWT + HUMAN_PERMISSION；外部路径 = `/api/operations` + 下游路径；GET Body 0，写 65536（块 137 形状）。

| # | Method | 下游路径 | 权限 | Query 白名单 |
|---|---|---|---|---|
| W1 | GET | `/client-onboarding-admin/v1/responsible-partners` | 既有 `CLIENT_ONBOARDING_REVIEW_READ` | — |
| W2 | POST | `/client-onboarding-admin/v1/cases/{caseId}/checks/{checkId}/result` | 既有 `CLIENT_ONBOARDING_EVIDENCE_REVIEW` | — |
| W3 | POST | `/client-onboarding-admin/v1/cases/{caseId}/screenings/run` | 新 `CLIENT_ONBOARDING_SCREENING` | — |
| W4 | POST | `/client-onboarding-admin/v1/cases/{caseId}/screening-hits/{hitId}/resolution` | 同上 | — |
| W5 | PUT | `/client-onboarding-admin/v1/cases/{caseId}/risk-rating` | 新 `CLIENT_ONBOARDING_RISK_RATE` | — |
| W6 | POST | `/client-onboarding-admin/v1/cases/{caseId}/recommendation` | 新 `CLIENT_ONBOARDING_RECOMMEND` | — |
| W7 | POST | `/client-onboarding-admin/v1/cases/{caseId}/requirements/{requirementId}/waiver` | 新 `CLIENT_ONBOARDING_WAIVE` | — |
| G1 | GET | `/onboarding-config-admin/v1/jurisdictions` | 新 `ONBOARDING_CONFIG_READ` | — |
| G2 | GET | `/onboarding-config-admin/v1/onboarding-schemas` | 同上 | `jurisdiction` |
| G3 | GET | `/onboarding-config-admin/v1/catalog-items` | 同上 | `catalogCode, jurisdiction` |
| G4 | GET | `/onboarding-config-admin/v1/programs` | 同上（**待 compliance 确认程序只读 API，可能删除**） | `jurisdiction` |
| L1 | GET | `/client-admin/v1/clients` | 新 `CLIENT_READ` | `page, size, status, jurisdiction, responsiblePartnerId` |
| L2 | GET | `/client-admin/v1/clients/{clientId}` | 同上 | — |
| L3 | GET | `/client-admin/v1/responsible-partners` | 同上 | — |
| K1 | GET | `/onboarding-code-admin/v1/local-partners` | 新 `ONBOARDING_CODE_READ` | — |
| K2 | GET | `/onboarding-code-admin/v1/local-partners/{localPartnerId}/onboarding-codes` | 同上 | `status` |
| K3 | POST | `/onboarding-code-admin/v1/local-partners/{localPartnerId}/onboarding-codes` | 新 `ONBOARDING_CODE_MANAGE` | — |
| K4 | POST | `/onboarding-code-admin/v1/local-partners/{localPartnerId}/onboarding-codes/{codeId}/revocation` | 同上 | — |
| — | GET | 既有 `client-onboarding-o01` `/cases`：加 1 行 query_param `responsiblePartnerId`（route_id 子查询，自身 ID 1386xx） | — | `responsiblePartnerId` |

既有证据核验 / 退回 / 批准 / 拒绝路由不变，只改请求体。K3 响应含一次性明文入驻码：Gateway 不缓存响应；ops-bff 该前缀按 metadata-only 记录日志（ops-fe 负责）。
W2 与既有 o06 `POST /cases/{caseId}/checks` 段数不同，不构成重叠；定稿后以编译门禁确认。

### 3.3 权限、菜单与按钮（ops-fe 2026-10-27 确定）

| 新权限 code | resource_type / action / risk |
|---|---|
| `ONBOARDING_CONFIG_READ` | ONBOARDING_CONFIG / READ / LOW |
| `CLIENT_READ` | CLIENT / READ / LOW |
| `ONBOARDING_CODE_READ` | ONBOARDING_CODE / READ / LOW |
| `ONBOARDING_CODE_MANAGE` | ONBOARDING_CODE / MANAGE / HIGH |
| `CLIENT_ONBOARDING_SCREENING` | CLIENT_ONBOARDING / SCREENING / MEDIUM |
| `CLIENT_ONBOARDING_RISK_RATE` | CLIENT_ONBOARDING / RISK_RATE / MEDIUM |
| `CLIENT_ONBOARDING_RECOMMEND` | CLIENT_ONBOARDING / RECOMMEND / HIGH |
| `CLIENT_ONBOARDING_WAIVE` | CLIENT_ONBOARDING / WAIVE / HIGH |

既有权限按 code 子查询：`CLIENT_ONBOARDING_REVIEW_READ`、`CLIENT_ONBOARDING_EVIDENCE_REVIEW`、`CLIENT_ONBOARDING_RETURN`、`CLIENT_ONBOARDING_DECIDE`。

| 资源 code（component_key 同 code） | 类型 | 父级 | 显示名 | 前端路由 | 权限 |
|---|---|---|---|---|---|
| `OPERATIONS_ONBOARDING_CONFIG` | MENU | 与既有 `CLIENT_ONBOARDING_REVIEW` 相同（现为顶级，parent NULL） | 入驻配置 | `/client-onboarding/config` | ONBOARDING_CONFIG_READ |
| `OPERATIONS_ONBOARDING_CODES` | MENU | 同上 | 入驻码 | `/client-onboarding/codes` | ONBOARDING_CODE_READ |
| `OPERATIONS_CLIENTS` | MENU | 商务 `900000000000095301`，sort 50 | Client | `/master-data/clients` | CLIENT_READ |
| `OPERATIONS_ONBOARDING_CODES_MANAGE` | BUTTON | `OPERATIONS_ONBOARDING_CODES` | 签发与作废 | — | ONBOARDING_CODE_MANAGE |
| `CLIENT_ONBOARDING_SCREENING` | BUTTON | 既有 `CLIENT_ONBOARDING_REVIEW`（子查询） | 筛查与命中处置 | — | CLIENT_ONBOARDING_SCREENING |
| `CLIENT_ONBOARDING_RISK_RATE` | BUTTON | 同上 | 客户风险评级 | — | CLIENT_ONBOARDING_RISK_RATE |
| `CLIENT_ONBOARDING_RECOMMEND` | BUTTON | 同上 | 推荐结论 | — | CLIENT_ONBOARDING_RECOMMEND |
| `CLIENT_ONBOARDING_WAIVE` | BUTTON | 同上 | 豁免材料要求 | — | CLIENT_ONBOARDING_WAIVE |

人工检查结果复用既有按钮 `CLIENT_ONBOARDING_REVIEW_EVIDENCE`（核验材料与检查）。
mor-test 只读核对（2026-10-27）：8 个新权限码、8 个新资源码、18 条新路径均未占用。排序冲突：商务下 sort 50 已被「变更历史」`OPERATIONS_COMMERCIAL_HISTORY` 占用（本机与 mor-test 相同；商务子菜单 5—70 已用），顶级 sort 300 为「客户入驻审核」——两个新顶级菜单建议 310 / 320，`OPERATIONS_CLIENTS` 建议改 80（Local Partner 为 70）或接受同序。
前端 `nav.resource.*` 的 en / zh-CN 键由 ops-fe 加；`console-resource-capabilities.json`（S-11）的 3 个菜单组件 + 5 个按钮条目归属待主会话指派（该文件不在本 agent 写入范围内）。

## 4. Header、Guard 与超时取值

**所有路由**：`request_cookie_mode=DROP`、`response_cookie_mode=NONE`、`idempotency_mode=NONE`、`body_receive_timeout_millis=5000`、`response_timeout_millis=10000`（与既有入驻路由一致；CM-2 form-schema 在 client-master 事务外调 CP-1，若 BFF 预算超 10 s 再议）。

**Header**（照抄本机同形路由）：

| 形状 | 行 |
|---|---|
| CLIENT GET（同 c05） | `authorization:CORS_REQUEST_ALLOWED` |
| CLIENT 写（同 c14） | `authorization:CORS_REQUEST_ALLOWED`、`content-type:CORS_REQUEST_ALLOWED`、`content-type:FORWARD_REQUEST`、`origin:FORWARD_REQUEST`（2026-10-16 起 idempotency-key 不转发；c14 本机无该头） |
| OPERATIONS GET（块 137） | `authorization:CORS_REQUEST_ALLOWED`、`origin:FORWARD_REQUEST` |
| OPERATIONS 写（块 137） | GET 两行 + `content-type` CORS / FORWARD + `idempotency-key` CORS / FORWARD |

注意：本机既有 o07 为 `idempotency-key:CORS_REQUEST_ALLOWED` 而无 FORWARD；块 137 写路由两者都有。Gateway 拥有 `Idempotency-Key`（2026-09-17 决定），定稿时以 mor-test live 块 137 行为为准并与 BFF agent 确认 BFF 是否需要收到该头。

**Guard**（编译器上限：SHARED_ROUTE 速率 ≤ 600/min、并发 ≤ 50；超限即使 DISABLED 也会打断快照刷新）：

| 形状 | SHARED_ROUTE | CLIENT_IP | AUTHENTICATED_PRINCIPAL |
|---|---|---|---|
| CLIENT 读 / 写（本机 c05、c14 live 值） | 600/min + 并发 50 | 120/min + 并发 10 | 60/min + 并发 5 |
| OPERATIONS 读（块 137 约定） | 300/min + 并发 50 | 30/min + 并发 3 | 30/min + 并发 2 |
| OPERATIONS 写（块 137 约定） | 300/min + 并发 50 | 30/min + 并发 3 | 10/min + 并发 2 |

本机既有 o01 / o07 live 值与 Client 路由相同（60/5、120/10、600/50），与块 137 约定不同；registration-plan.json 原始值又不同（如 a01 SHARED 120 / IP 3）——说明既有入驻路由在注册后经管理 API 调整过。**定稿前在 mor-test 只读查询 c05 / c14 / o01 / o07 的 guard 行，以 live 值为 CLIENT 路由模板**；OPERATIONS 新路由按块 137 约定（每主体并发 2：运营工作台页面不得扇出并发请求）。

每条路由 6 行 guard（三维度 × 速率 + 并发）。按 3.1 的 5 条 CLIENT + 3.2 的 18 条 OPERATIONS（G4 待定）估算：permission 8、role_permission 8、console_resource 8、route 23（22）、header 约 82、query_param 15（G4 删除则 14；含 o01 新增 1 行）、guard 138（132）。

## 5. 执行前核对（定稿 SQL 头部照写，mor-test 只读执行）

```sql
-- 自身区间为空
SELECT 'permission', COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000138001 AND 900000000000138999
UNION ALL SELECT 'role_permission', COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000138001 AND 900000000000138999
UNION ALL SELECT 'console_resource', COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000138001 AND 900000000000138999
UNION ALL SELECT 'gateway_route', COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000138001 AND 900000000000138999
UNION ALL SELECT 'route_header', COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000138001 AND 900000000000138999
UNION ALL SELECT 'query_param', COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000138001 AND 900000000000138999
UNION ALL SELECT 'guard_rule', COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000138001 AND 900000000000138999;
-- 子查询依赖存在且唯一（每行期望 1）
SELECT 'client target', COUNT(*) FROM t_gateway_target WHERE target_service_code = 'client-console-bff' AND channel = 'CLIENT'
UNION ALL SELECT 'review menu', COUNT(*) FROM t_console_resource WHERE resource_code = 'CLIENT_ONBOARDING_REVIEW'
UNION ALL SELECT 'o01 route', COUNT(*) FROM t_gateway_route WHERE route_code = 'client-onboarding-o01'
UNION ALL SELECT 'existing perms', COUNT(*) FROM t_permission WHERE permission_code IN ('CLIENT_ONBOARDING_REVIEW_READ',
  'CLIENT_ONBOARDING_CLASSIFICATION_EDIT', 'CLIENT_ONBOARDING_EVIDENCE_REVIEW', 'CLIENT_ONBOARDING_RETURN', 'CLIENT_ONBOARDING_DECIDE'); -- 期望 5
-- 新 code、(method, path) 未被占用：定稿时按最终清单列出 IN (...)，期望 0
```

并行会话（tax 等）未声明块号；定稿当日再查一次 live 区间。

## 6. 预编译门禁（必须，发布前）

`assets/harness/block_compile_check.sh`：断网一次性 MySQL（默认 `mysql:8.4.11`，应换成 mor-test 同一镜像 ID）导入 `mor_platform` 备份 → 依次执行块 SQL → 每步用**候选 Platform JAR** 内的生产 `GatewayConfigurationCompiler` 编译（`BlockSnapshotCheck.java`，按 `GatewayConfigurationRepositoryImpl` 加载方式组装 `LoadedConfiguration`，只把 HUMAN_PERMISSION 路由引用的权限交给编译器）。

```bash
H=requirements/20261027/client-master-全球合规入驻与资格切片1
$H/assets/harness/block_compile_check.sh <mor_platform-full.sql> <paytech-platform-support-app-exec.jar> <工作目录> \
  dormant=$H/release/sql/60-platform-block138-registration.sql \
  active=$H/release/sql/61-platform-block138-activate.sql \
  deactivated=$H/release/sql/62-platform-block138-deactivate.sql \
  rolled-back=$H/release/sql/69-platform-block138-rollback.sql
```

通过标准：五态全部 `COMPILE OK`；dormant 的 `block effective=0`，active 的 `block effective` = 块路由数；rolled-back 与 baseline 的路由数一致。

本机验证（2026-10-27，`mor_platform_v2e2e` 只读 dump + 本机 e2e Platform JAR）：baseline 526 路由 / 475 有效 → 合成 DISABLED 路由 dormant OK（有效 475）→ ACTIVE OK（有效 476）→ 把该 DISABLED 路由的 SHARED_ROUTE 速率改为 1200 后 `COMPILE FAILED: RATE_LIMIT Guard 数值形状不合法`，退出码 3。门禁能拦截 DISABLED 行的 Guard 越界（块 135 事故形态）。

## 7. 待主会话 / BFF agent 决定

1. client-console-bff 的路径（3.1 仍为建议）与全部 route_code；OPERATIONS 路径已定。
2. CP-4 是否新增 Client 路由；O4 程序查看是否开放。
3. ~~新权限码拆分与菜单父级~~：已由 ops-fe 确定（3.2、3.3）；剩余新菜单 sort 冲突。
4. G4 程序查看路由是否保留（等 compliance）。
5. `client-onboarding-c03 form-options` 的停用时机（块 138 激活脚本内 or 后续块）。
6. OPERATIONS 写路由是否转发 `idempotency-key`（块 137 转发、既有 o07 不转发）。
