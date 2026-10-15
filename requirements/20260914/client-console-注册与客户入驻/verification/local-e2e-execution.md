# 本机全链路搭建执行记录

> 方案：[local-e2e-setup-plan.md](./local-e2e-setup-plan.md)。授权：2026-10-14 用户回复“全部授权”（A1—A9）。
> 私密材料只在 `local-environment/e2e/`（已忽略提交，目录 700、文件 600）；本记录不含密码、私钥或令牌。

## 1. 已完成（2026-10-14）

| 编号 | 动作 | 结果 |
|---|---|---|
| A1 | `compose.yaml` 新增 `mailpit`（Profile `mail`，按摘要固定），SMTP 11025 / 网页 18025 | 运行、健康 |
| A8 | MinIO 增加 `MINIO_API_CORS_ALLOW_ORIGIN`（18443、18444）并重建容器（数据卷保留） | 预检返回 204 与对应 Allow-Origin |
| A2 | `golang:1.27.1` 容器内交叉编译 macOS arm64 `vault-server` 到 `local-environment/vault/bin/` | 已构建 |
| A3 | `GenerateLocalE2eMaterial.java` 生成运营/客户签名私钥、HMAC、Vault AES 根密钥、引导与管理员密码 | 写入 `local-environment/e2e/secrets.env`（未写 `.env.local`，便于整体删除） |
| 补充 | Nacos 已开认证但没有任何账号与命名空间：初始化 Nacos 管理员、创建 `mor-local` 命名空间、服务账号 `mor-local-services`（`mor-local:*:*` 读写） | 方案遗漏项，属本机环境配置；密码在 `secrets.env` |
| A4 | 执行 `platform-support-initial-data.sql`（sha256 前缀 98a99503）到 `mor_platform` | 目标 1、路由 105（启用 36）、权限 41、页面 49、角色 2 |
| A5 | 执行生成的 `bootstrap.sql`：引导管理员 `admin`（900000000000009001）+ OPERATIONS/CUSTOMER 签名公钥（…9003/…9004） | 已写入 |
| A6 | 执行 [`local-e2e/a6-enable-config-management.sql`](./local-e2e/a6-enable-config-management.sql)：PLT-16/17 的 18 条路由、4 个页面、4 条管理员授权启用 | 已写入 |
| 启动 | Vault、Platform、CCT、Risk、Client BFF、Operations BFF、Gateway、两个 Vite、Nginx 经 [`run-service.sh`](./local-e2e/run-service.sh) 启动 | 全部健康并注册到 Nacos；Gateway 快照加载成功 |
| 3.1 | [`bootstrap_admin.py`](./local-e2e/bootstrap_admin.py)：引导管理员改密 → 创建首个真人管理员 → 新管理员改密 | 管理员账号 236033080844881920；引导账号随后登录返回 401 |
| A7 | `risk-generic-materials-init.sql`，`@approved_by=236033080844881920` | 方案 3、版本 3、材料要求 10 |
| A9 | [`register_onboarding.py`](./local-e2e/register_onboarding.py)：经管理接口创建 CLIENT 目标、5 权限、39 路由、5 页面并全部启用 | 全部 ACTIVE；Gateway 快照 targetCount=2、routeCount=93 |

## 2. 第二轮（用户回复“同意，继续做吧”）

- **注册缺陷已修**：`RegisterClientProcessor` 删除注入的只读模板字段，改用本类 `transaction()`；Platform 重新打包并重启后，经 Nginx→Gateway→BFF 注册返回 `accepted`，Mailpit 收到验证邮件。新增 `ClientRegistrationTest`（正常注册、邮件投递失败、非法邮箱）与 `OperationsTestMapper.challenges`；**未能运行**：测试要求 `mor_platform` 的测试独占表为空，本机全链路数据使其在开始前拒绝运行（数据未被删除）。
- **管理员角色授权**：已写好 [`a9-grant-admin-role.sql`](./local-e2e/a9-grant-admin-role.sql)，执行再次被本机权限检查拦截，未执行。
- **扫描策略被删除后恢复**：核对时发现 `CLIENT_ONBOARDING_FORMAT` 策略与检查项为 0 行（来源未知，本会话测试只按自身插入 ID 删除）；按原授权的本机 SQL 重新写入。
- **CCT 改用 STABLE**：修改被本机权限检查拦截，未改；需用户明确选择方案。

## 2b. 第三轮（用户：“命令跑完了，2 和 3 按你的想法”）

- **A9 授权**：用户自行执行 `a9-grant-admin-role.sql`，5 个入驻权限对 PLATFORM_ADMIN 均为 ACTIVE。
- **CCT 改 STABLE**：`ClientDraftContactClientImpl` 联系人 Protect 改为 `TokenMode.STABLE`；`api-contract.md` 记录决定；本机 Vault 策略 `PHONE_E164_V1` 同步为 STABLE。CCT 重新打包并重启，Vault 重启加载策略。
- **独立 e2e 库**：root 创建 `mor_platform_e2e` 并授予 `platform_app`；`mysqldump` 复制 `mor_platform` 全部表与数据；Platform 改连该库并重启，注册冒烟确认新账号只写入 `mor_platform_e2e`。
- **清空 `mor_platform` 测试独占表**：本机权限检查拦截，已写成 [`clear-mor-platform-test-tables.sql`](./local-e2e/clear-mor-platform-test-tables.sql) 交用户执行。
- `mor_cct`、`mor_risk` 仍由本机环境与各自 auto 测试共用，未运行 CCT/Risk 测试以免冲突。

## 2c. 接口验收（用户：“继续”）

[`acceptance_flow.py`](./local-e2e/acceptance_flow.py) 经 Nginx 正式入口走完整链路（`--approve` 含重提与批准）。最终一次 38 步全部通过：注册→Mailpit 验证→登录/刷新→建个人申请→保存资料（含联系邮箱/电话，CCT→Vault STABLE）→F01 上传授权→直传 MinIO→F02 完成→格式检查 AVAILABLE→C12 关联→C16/C14 提交→运营 O01/O02→O10 下载并 GET 到 `%PDF`→O07 退回→客户看到退回→重提→O05 核验材料→O03/O04 分类→O08 批准→客户看到批准。

验收中发现并修复的缺陷：

| 现象 | 根因 | 修复 |
|---|---|---|
| F01 400“命令身份格式无效” | 注册计划生成器的幂等路由集合漏了 F01/F02，Gateway 未放行/转发 `Idempotency-Key`（合同第 1 节要求携带） | `complete_registration_plan.py` 补 F01/F02 并重新生成；[`sync_route_headers.py`](./local-e2e/sync_route_headers.py) 经管理接口更新两条在用路由（version 3） |
| F01 503 | 本机 Platform 未开 `tenant.projection.enabled`，CCT 创建申请后的 Tenant 开通无法落地 | `run-service.sh` 增加该开关 |
| 开通被拒 403 `TENANT_APPLICANT_INVALID` | `TenantProjectionMapper.xml` 的 `lockAccount/fileAccount/find/lockTenant/owners` 以下划线列名映射到 fluent DataObject，全局未开下划线转驼峰，`accountType` 等字段恒为空；此路径此前无测试 | 查询列显式别名；新增 `ClientTenantProvisionTest`（开通、读回、重放；非 CUSTOMER 拒绝）；全量扫描其余 Mapper 无同类问题 |
| O10 415 | 运营页下载按钮对无请求体 POST 未声明 JSON 内容类型（BFF 对非 DELETE 写入口统一要求） | `ClientReviewDetail.tsx` 补 `Content-Type: application/json`，与其他无体 POST 一致 |

验收在本机库留下多个 `acceptance-*@example.test` 客户、申请与审核案件；最早几次被中途失败的 CCT Tenant 开通补偿事件为终态 FAILED，未清理。

另：CCT auto 测试与本机 CCT 进程共用 RabbitMQ `/mor-local`，测试期间本机 CCT 消费了测试发布的 2 条审核结果并转入死信；后续跑 CCT/Risk 测试前应先停本机 CCT/Risk，或为本机环境分配独立 vhost。

## 3. 初次阻塞与待决（已由第 2 节更新）

1. **管理员角色未获入驻权限**：`PLATFORM_ADMIN` 是系统角色，管理接口拒绝修改（“系统角色不允许修改或删除”）；改用 SQL 授予时被本机权限检查拦截，未执行。运营端审核页因此还不可用。
2. **客户注册失败（代码缺陷）**：`RegisterClientProcessor` 的写事务字段由 Lombok 构造器注入了全局只读 `transactionTemplate` Bean，`lockAccount` 的锁定读报 “Cannot execute statement in a READ ONLY transaction”；同类 Processor 均直接调用本类 `transaction()`。修复为删除该字段、改用 `transaction()`；Platform 现无注册入口测试覆盖。按方案“需改代码先报告”，未修改。
3. **Vault 邮箱 Token 模式冲突（设计问题）**：Vault 每个 Data Type 只允许一种 Token 模式；Platform 登录邮箱用 `EMAIL_ADDRESS_V1` STABLE，CCT 申请联系人用同一 Data Type 的 REFERENCE。本机策略取 STABLE，CCT 保存联系人邮箱将返回 `TOKEN_MODE_NOT_ALLOWED`。

注册冒烟两次（`smoke-client-1/2@example.test`）在 Vault 留下 2 条 PROTECT 记录，Platform 无账号数据。

## 4. 回滚

| 编号 | 回滚 |
|---|---|
| A1 | `docker compose --profile mail stop mailpit && docker compose rm -f mailpit`，删除 compose 中 `mailpit` 段 |
| A8 | 删除 `MINIO_API_CORS_ALLOW_ORIGIN` 并重建 `minio` |
| A2/A3 | 删除 `local-environment/vault/bin/`、`local-environment/e2e/` |
| Nacos | 控制台删除服务账号、角色、权限与 `mor-local` 命名空间 |
| A4 | 按初始化文件中的固定 ID 删除对应行（`t_gateway_*`、`t_permission`、`t_console_resource`、`t_role*`、`t_tenant` 相关） |
| A5 | 删除 `t_user_account`（…9001 与新管理员 236033080844881920 及其凭据、角色绑定）、`t_password_credential`（…9002）、`t_jwt_signing_key`（…9003、…9004） |
| A6 | 见 SQL 文件末尾注释 |
| A7 | 按文件 ID 依次删除 `t_dd_program_requirement` / `t_dd_program_version` / `t_dd_program` |
| A9 | 经管理接口将 39 路由、5 页面、目标置 DISABLED，5 权限置 INACTIVE |

停止进程：`local-environment/e2e/*.pid` 中各 PID；Nginx 用 `nginx -s stop -c local-environment/e2e/nginx/nginx.conf -p local-environment/e2e/nginx`。

## 2d. 请求幂等迁移后的真实进程联调（2026-10-15 夜，用户：“继续做，多agent”）

- **重建与重启**：以当前工作区 JDK 25 离线构建 CCT、Platform、Client BFF 的 exec JAR（Platform 工作区含另一会话未提交的通知/Webhook 改动；`install` 在 platform-support-test 覆盖率门禁处失败仅因跳过测试，web JAR 已生成）。停止并按 `run-service.sh` 重启 platform、cct（旧 pid 83734）、client-bff，三者健康；Gateway、Risk、Operations BFF 未重启。
- **路由同步**：`sync_route_headers.py`（REASON 已注明 2026-10-15 请求幂等迁移）更新 18 条入驻路由（C04、C06—C13、F01、F02、O03—O09），`idempotency-key` 只保留 CORS 允许、不再转发；Gateway Snapshot 刷新成功（routeCount=157）。
- **验证邮件**：Platform 认证邮件已改经 Delivery（另一会话进行中的改动），本机只有 SES 适配器、未配置，Mailpit 收不到。`e2e_http.mint_signup_link` 作为本机兜底：在 `mor_platform_e2e` 为该邮箱最新 PENDING 注册挑战按本机 `CLIENT_LOCAL_HMAC_KEY_BASE64` 重算 `secret_hmac`（只本机、只 PENDING 行），`acceptance_flow.py` 在 10 秒内收不到邮件时使用。
- **原 38 步验收**：`acceptance_flow.py --approve` 全部 PASS（39 项，含联系邮箱/电话经 CCT→Vault 随机操作键保存、F01/F02 不带转发 Key、退回后重提与批准）。
- **新增 `scenario_flow.py`**（场景 A 退回改证件重提；场景 B 组织重复写 + 拒绝后重新申请）。23:50 起另一会话用其 worktree（`127a8113/scratchpad/wt-prov`，HEAD + 其改动，不含本次迁移）构建的 Platform 替换了本机 e2e Platform（pid 64323），等待 10 分钟未恢复。该构建 F01 仍要求 `Idempotency-Key`，经新 BFF 返回 400（Platform 日志 `MissingRequestHeaderException`）。因此：
  - PASS：注册/验证/登录、个人与组织资料保存（含 Vault）、C07 新增人员、**C09 同内容重复返回同一 relationId、不同内容 409 `RELATION_ALREADY_EXISTS`、C11 重复删除 204/204**。
  - 未能执行（被上述 Platform 替换阻塞）：场景 A 全部提交后步骤、F02 重复完成、C12 重复关联、C13 重复删除、拒绝后重新申请。未停止对方进程。
- **本机遗留数据**：`acceptance-20261015234607/234810@example.test`、`scenario-ret-*`/`scenario-org-*` 账号及其申请（一份已批准个人申请、两个 DRAFT 申请）、MinIO 中验收 PDF；均为本机测试数据，未清理。
