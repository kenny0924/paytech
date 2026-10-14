# Client 注册与入驻：mor-test 发布清单（评审稿）

> 状态：2026-10-14 用户对本清单回复“可以”，按建议采纳 D1—D4 并批准第 8 节范围；执行结果见 [test-acceptance.md](./test-acceptance.md)。
> 原评审稿只做了只读核对。
> 第 7 节的每一类动作（改仓库文件并提交、生成密钥材料、DDL/DML、替换容器、改 Vault 策略）都要在你批准本清单后按顺序执行；第 2 节有 4 项需要你先决定。
> 基准：渠道沙箱会话 2026-10-14 已把 test 升级到 `ba36b63`（[执行记录](../../../20261014/platform-support-渠道沙箱验证/release/test-acceptance.md)），本次在其之上叠加，不回到旧镜像。

**目标：** 在 test 浏览器里走通“客户注册 → 邮件验证 → 填资料与上传 → 提交 → 运营审核（含下载材料）→ 客户看到结果”，与本机全链路验收（[记录](../verification/local-e2e-execution.md)，38 步通过）同一条链。

## 1. test 现状（2026-10-14 只读核对）

| 项 | 现状 | 本次要做 |
|---|---|---|
| 运行中容器 | 11 个全部 healthy；Gateway/Platform/运营 BFF/运营 Web 为 `ba36b63` 镜像，Risk/Vault/Nginx 为旧镜像 | 见下 |
| `ba36b63` 是否含入驻修复 | **不含**：注册只读事务（`7ead395`）、Tenant 投影映射（`7ead395`）、CCT 联系人 STABLE（`b106d78`）、O10 下载 415（`349df5e`）都在其后 | Platform、运营 Web 从新提交重建；Gateway、运营 BFF 自 `ba36b63` 以来只有格式改动，不重建 |
| `mor_cct` | **0 张表**（库与 `cct_app` 账号已存在） | 按完整 [CCT Schema](../../../../client-contract-service/docs/database/sql/client-contract-schema.sql) 建 38 表（新库路径，[迁移方案](../migration-plan.md)第 1 节） |
| `mor_risk` | 80 表。与当前 Schema 差：① 入驻 4b（缺 `t_dd_case_classification`，`t_due_diligence_case`/`t_dd_case_subject` 缺 5 列）；② 争议 4 表 8 列仍是 `provider_*`、索引 `uk_dispute_provider_act` 未改名。相关 9 张表**均为 0 行** | ① 执行 [risk-onboarding-alter.sql](../db/implementation/risk-onboarding-alter.sql)；② 见决定 D2 |
| Risk 入驻资料方案 | `t_dd_program*` 0 行 | 执行 [risk-generic-materials-init.sql](../db/implementation/risk-generic-materials-init.sql)，`@approved_by` = test 管理员 `221546852372267008` |
| `mor_platform` | 50 表与当前 Schema 一致；签名密钥只有 OPERATIONS 一把；扫描策略 0 行；CLIENT Gateway 目标不存在；ACTIVE 路由 149；PLATFORM_ADMIN v21；`9200000000000101～105` 授权 ID 无冲突 | 加 CUSTOMER 签名公钥、扫描策略、入驻登记、管理员授权 |
| 部署单元 | 没有 CCT、客户端 BFF、客户端 Web；`client-console-web` 没有 Dockerfile | 新增 |
| 基础设施 | 没有 MinIO、没有收信服务 | 新增 MinIO（含初始化）与 Mailpit |
| Vault 策略 | 只有 `TEST_STABLE_V1`/`TEST_REFERENCE_V1` 两个烟测类型 | 加 3 个业务数据类型规则（见第 3 节） |
| Risk 配置 | 没有 RabbitMQ 参数（入驻审核结果要经 MQ 回 CCT） | 补 `RISK_RABBITMQ_*`，vhost `/mor-test` |
| Gateway | 用占位值绕过 Client 配置缺陷（`gateway.client.enabled=false`） | 开启 Client 通道并换成正式值；占位随之删除 |

## 2. 需要你先决定的 4 项

**D1 HTTPS（必须决定）。** 代码要求客户端入口、Gateway Client 来源、客户端 BFF 来源、Platform 签发方、MinIO 端点都是 HTTPS（没有像运营端那样的测试 HTTP 开关），而 test 现在只有 `http://localhost:28080`。建议：
- 客户端：test Nginx 新增 `https://localhost:28443`，使用已存在但从未启用的 `test-environment/private/tls/server.crt`（由 test 自有 CA 签发，含 `localhost`/`127.0.0.1`）。运营端保持 `http://localhost:28080` 不变。
- MinIO：浏览器和容器内 Platform 必须用**同一个地址**访问（预签名地址按配置的端点生成）。建议用 `https://minio.mor-test.localhost:29400`：Chrome 自动把 `*.localhost` 解析到本机，Docker 内用网络别名指向 MinIO 容器，不改 `/etc/hosts`；用 test CA 新签一张含该名称的证书。
- 浏览器信任：test CA 目前不在钥匙串里。**推荐你把 `test-environment/private/test-ca.crt` 加入登录钥匙串并信任**（我给命令，你用 `!` 执行）；否则每次要先手动打开两个地址接受警告，后台上传仍可能失败。
- 改代码加“客户端测试 HTTP 开关”的方案不推荐：要改 Gateway/BFF/Platform 三处生产校验，且只为 test 服务。

**D2 Risk 争议表改名（推荐一起做）。** 新 Risk 镜像的 Mapper 读 `channel_*` 列，不改名则争议相关接口出错；这是“支付渠道命名统一”需求的迁移，仓库里没有现成执行 SQL。4 张表均为空，建议本次由我从当前 Schema 生成 `CHANGE COLUMN`（保留类型/可空/注释）+ 1 个索引改名的 SQL，先给你看再执行，并通知命名统一的负责会话。备选：本次 Risk 不升级——则入驻无法发布。

**D3 收信服务。** 建议 test 新增 Mailpit（不向外投递，网页 `http://localhost:28025` 查看验证邮件），Platform 关闭 STARTTLS。若要接真实 SMTP，需要你提供服务商与账号，本清单不包含。

**D4 Gateway 缺陷。** 本次开启 Client 通道后需要的配置都会正式给出，缺陷不再阻塞，占位值删除；缺陷本身（通道关闭时仍校验）建议另行修复，不放进本次发布。

## 3. 仓库改动（先改、你审、单独授权提交，再从该提交构建）

| 文件 | 改动 |
|---|---|
| `client-console-web/Dockerfile`、`client-console-web/deploy/nginx.conf`、`.dockerignore` | 照运营 Web 的写法：宿主机 `npm run build` 的 `dist` 拷进固定摘要的 nginx 镜像，`/healthz`，`/api` 返回 404 |
| `test-environment/compose.yaml` | 新增 `minio`（与本机同一 Chainguard 摘要，`--address :29400`，网络别名 `minio.mor-test.localhost`，宿主 `127.0.0.1:29400`，CORS 只允许 `https://localhost:28443`、`http://localhost:28080`）、`minio-init`（建桶 `mor-platform-files` 与 Platform 访问账号，复用 `local-environment/minio/init`）、`mailpit`（宿主只暴露网页 28025）；新数据卷 `mor-test-minio-data` |
| `test-environment/compose.applications.yaml` | 新增 `client-contract-service`（172.30.73.26，`onboarding.draft/recovery-enabled=true`，ID datacenter 0 / worker 26，vhost `/mor-test`）、`client-console-bff`（172.30.73.27，`client.onboarding-enabled=true`，来源 `https://localhost:28443`）、`client-console-web`；Risk 补 `RISK_RABBITMQ_*` 与两个入驻开关；Platform 补 `CLIENT_AUTHENTICATION_*`（签发方 `https://customer-auth.mor-test.local`、发件人、公共来源 `https://localhost:28443`）、`TENANT_PROJECTION_ENABLED`、`FILES_ONBOARDING_*`（MinIO、`CLIENT_ONBOARDING_FORMAT`、信任 test CA）、邮件指向 Mailpit；Gateway 补 `GATEWAY_CLIENT_ENABLED=true`、来源与签发方/受众；Nginx 增加 28443 端口与证书挂载 |
| `test-environment/nginx/nginx.conf` | 新增 `listen 8443 ssl` 的客户端 server：`/api` 写入单值 `X-Entry-Channel: CLIENT` 并清除转发头（与运营 server 相同写法），`/` 转 `client-console-web` |
| `test-environment/vault/policy.json` | 加 `EMAIL_ADDRESS_V1`、`PHONE_E164_V1`（均 STABLE）及 3 组规则：Platform `PLATFORM_LOGIN_EMAIL_V1`、CCT `CLIENT_ONBOARDING_CONTACT`（邮箱、电话），各含 PROTECT/REVEAL；原烟测规则保留 |
| `verification/local-e2e/e2e_http.py` 等验收脚本 | 增加入口地址与 CA 参数，使同一脚本能对 test 运行；默认值保持本机不变 |
| 本目录 `release/` | 本清单、候选 Compose 覆盖文件、SQL（CUSTOMER 公钥、扫描策略、管理员授权、D2 改名）、执行记录 |

私密材料只写 `test-environment/private/`（0700/0600，已忽略提交），不打印：`platform.env` 追加 CUSTOMER 签名私钥与 HMAC（新生成，与本机不同）；`.env.test` 追加 MinIO root/KMS/Platform 访问密钥；MinIO 证书与私钥由 test CA 签发。现有 OPERATIONS 材料不轮换。

## 4. 制品

- 从**干净提交**（第 3 节提交后的 HEAD）用 `git worktree` 构建，不用工作区。
- 新建/重建：Platform、CCT、Risk、客户端 BFF 的 `*-exec.jar`（JDK 25，`./mvnw -o -DskipTests package`），运营 Web 与客户端 Web 的 `dist`；镜像 tag `mor-test/<service>:client-onboarding-20261014`，执行时以固定 image ID 写入候选 Compose。
- 不变：Gateway、运营 BFF 沿用当前 `ba36b63` 镜像（Gateway 只改环境变量，需重建容器）；Vault 镜像不变，只换策略文件并重启。
- 回退镜像：当前运行的镜像 ID（执行前记录）。
- 该提交的已有验证：Platform 418/418、CCT 40/40、运营 Web 989/989、运营 BFF 1279/1279；Risk 与客户端 BFF/Web 执行前补跑全量（Risk 测试前停本机 e2e 的 Risk 进程，避免共用 MQ）。

## 5. 执行顺序（任一步与预期不符即停止并报告）

1. **复核**：重跑第 1 节只读查询，确认未漂移；记录 11 个容器的镜像 ID 与 MySQL 容器 ID。
2. **备份**（`~/Code-MoR-backups/20261014-client-onboarding-test/<UTC>/`，0700/0600，附 SHA256SUMS）：`mor_platform` 结构 + `t_jwt_signing_key`、`t_file_scan_*`、`t_role`、`t_role_permission`、`t_permission`、`t_console_resource`、`t_gateway_*` 数据；`mor_risk` 受影响 9 表结构；当前 `vault/policy.json`、`nginx.conf`、两个 Compose 文件副本。
3. **建表/改表**（root，`utf8mb4`，每个文件单独连接，不串接多个含事务的脚本，不用 `--force`）：
   - `mor_cct`：确认 0 表 → 执行完整 Schema → 核对 38 表与 Schema 逐项一致。
   - `mor_risk`：确认 9 表仍为 0 行 → `risk-onboarding-alter.sql` → D2 改名 SQL → 与当前 Schema 逐列、逐索引一致（应为 81 表、无差异）。
4. **初始化数据**（每个文件试运行 ROLLBACK 核对后再 COMMIT）：CUSTOMER 签名公钥 1 行；扫描策略 1+1 行；Risk 资料方案（方案 3、版本 3、材料要求 10）。
5. **基础设施**：启动 `minio` → `minio-init`（成功退出）→ `mailpit`；核对桶存在、CORS 预检返回对应 Allow-Origin。
6. **Vault**：替换策略文件，重启 `vault-service`，healthy 后用烟测脚本确认原有烟测规则仍通过。
7. **应用**（每次一个，`up -d --no-deps --no-build --pull never --wait`，健康后再下一个）：Risk → Platform → CCT → 客户端 BFF → Gateway → 运营 Web → 客户端 Web → Nginx。确认 Nacos 中 CCT、客户端 BFF 各一个实例。
8. **登记与启用**：经 `http://localhost:28080` 管理接口运行 `register_onboarding.py`（CLIENT 目标、5 权限、39 路由、5 页面，逐项启用）；执行管理员授权 SQL（5 行，PLATFORM_ADMIN v21→v22）。等待 Gateway 快照 routeCount 149→188、targetCount 1→2。
9. **部署检查**（第 6 节）。

## 6. 部署检查

- 13 个应用容器 + MinIO/Mailpit healthy；原有 16 个运营菜单页可打开、无 4xx/5xx（含外部 API 与沙箱验证页）。
- 对 test 运行 `acceptance_flow.py --approve`：38 步全部通过（注册 → Mailpit 收信验证 → 登录/刷新 → 资料保存（CCT→Vault STABLE）→ 上传直传 MinIO → 格式检查 → 提交 → 运营审核、下载、退回 → 重提 → 批准 → 客户看到批准）。
- 浏览器人工抽查：`https://localhost:28443` 注册与上传页、`http://localhost:28080` 运营审核页及材料下载。
- 敏感信息检查：容器日志中检索验收邮箱、电话原值与私钥片段为 0。
- 验收会在 test 留下 `acceptance-*@example.test` 账号、申请与审核记录，属于 test 数据，不清理。

## 7. 恢复

- SQL 未提交时失败：同一连接 ROLLBACK 并停止。
- 应用替换后失败：先经管理接口把 39 路由、5 页面、CLIENT 目标置 DISABLED、5 权限置 INACTIVE（若已登记），确认快照不再暴露入驻入口；再按逆序用备份记录的原镜像 ID 回退 Platform、Risk、运营 Web，停止 CCT、客户端 BFF/Web、MinIO、Mailpit；Nginx、Gateway、Vault 恢复备份的配置文件并重建容器。
- 新表与新列：旧镜像不访问 `mor_cct` 新表和 Risk 新列，回退时保留；D2 改名后旧 Risk 镜像会读不到 `provider_*` 列——**回退 Risk 前须先按备份结构把列名改回**（表为空时可直接执行，脚本随 D2 SQL 一起给出）。
- 管理员授权回滚：`DELETE FROM t_role_permission WHERE role_permission_id BETWEEN 9200000000000101 AND 9200000000000105` 并递增 PLATFORM_ADMIN 版本。

## 8. 待批准范围

**按本清单：完成第 3 节仓库改动与私密材料生成并经你确认后提交（不推送）；从该提交构建 6 个镜像；备份；在 test 执行 `mor_cct` 38 表建表、`mor_risk` 入驻改表与 D2 改名、3 份初始化数据和管理员授权；新增 MinIO/Mailpit，更新 Vault 策略；按序替换/新增 8 个应用容器与 Nginx；经管理接口登记并启用入驻入口；运行第 6 节检查。失败按第 7 节恢复。** 不包含：生产或其他环境、真实 SMTP、病毒扫描、Gateway 缺陷修复、推送代码或镜像。
