# 本机全链路搭建方案（评审稿）

> 状态：2026-10-14 评审稿，未执行。目的：在本机浏览器走通“客户注册 → 填资料与上传 → 提交 → 运营审核（含下载材料）→ 客户看到结果”。
> 本稿只是方案；每一步写库、改环境配置都按第 6 节逐项授权。不部署、不推送、不连接任何外部环境。

## 1. 目标链路

```text
浏览器 https://127.0.0.1:18443 (客户)  ─┐
浏览器 https://127.0.0.1:18444 (运营)  ─┤ Nginx（本机 Homebrew，设置 X-Real-IP / X-Entry-Channel）
                                        ├─ /      → Vite 开发服务器 18302 / 18301
                                        └─ /api/  → API Gateway 18080
Gateway ─(Nacos mor-local)→ client-console-bff 18202 / operations-console-bff 18201
BFF ─(Nacos)→ Platform 18106、CCT 18101、Risk 18104
Platform/CCT ─(Nacos)→ Vault 18107；Platform → Mailpit(SMTP)、MinIO 19400
浏览器上传/下载文件 → 直连 MinIO https://127.0.0.1:19400（预签名地址）
```

服务一律以宿主机 Java/Go 进程运行、通过本机 Nacos（命名空间 `mor-local`）互相发现，与生产调用方式一致；不使用只允许 `auto` 测试 Profile 的直连地址开关。

## 2. 现状（2026-10-14 只读核对）

| 项 | 现状 |
|---|---|
| Docker 基础设施 | MySQL、Redis、RabbitMQ、Nacos、MinIO 运行正常 |
| `mor_platform` | 表结构与已提交 Schema 一致（另有其他会话的 4 张沙箱验证表）；路由、账号、签名密钥均为 0 行，初始化目录 DML 从未执行 |
| `mor_cct` / `mor_risk` / `mor_vault` | 表结构已建（38 / 81 / 3 张）；Risk 入驻资料配置 `t_dd_program` 0 行 |
| Nginx | Homebrew `/opt/homebrew/bin/nginx` 已装；仓库无 CLIENT/运营本机 HTTPS 配置 |
| Vault | 无运行实例；本机没装 Go（构建需要 Go 1.27.1）；现成测试镜像构建于 9 月 4 日，旧于当前代码；没有覆盖 Platform/CCT 的策略文件 |
| 邮件 | 无收信服务；Platform 默认要求 STARTTLS |
| 旧脚本 `run-local-https.py` | 引用的 Platform 启动类不存在、读取的 Redis 变量名不对，且不含 CCT/Risk，不再沿用 |

## 3. 搭建步骤

### 步骤 1：本机工具与环境文件（不写库）

1. **Mailpit 收信服务**：`local-environment/compose.yaml` 新增 `mailpit`（Profile `mail`，按镜像摘要固定），SMTP `127.0.0.1:11025`，网页 `127.0.0.1:18025`。
2. **Vault 构建**：用官方 `golang:1.27.1` 镜像在容器里编译出 macOS 可执行文件（不在本机装 Go），产物放 `local-environment/vault/bin/`（加入 `.gitignore`）。也可以你自己装 Go 1.27.1，二选一。
3. **本机 Vault 策略**：新增 `local-environment/vault/policy.local.json`，只包含本链路需要的规则：
   - `platform-support-service`：`EMAIL_ADDRESS_V1`（稳定模式），用途 `PLATFORM_LOGIN_EMAIL_V1`，PROTECT/REVEAL。
   - `client-contract-service`：`EMAIL_ADDRESS_V1`、`PHONE_E164_V1`（引用模式），用途 `CLIENT_ONBOARDING_CONTACT`。
   - 实施时以代码中实际调用为准逐条核对，不加多余规则。
4. **本机密钥生成脚本**（`verification/local-e2e/generate-material.*`，复用测试环境 `GenerateOperationsMaterial.java` 的做法）：生成
   - 运营、客户两套 RSA 签名私钥 + HMAC 密钥 + 版本号；
   - Vault 本机 AES 根密钥；
   - 引导管理员 `admin` 的随机初始密码及其 Argon2id 校验串。
   私钥与密码只写入 `local-environment/.env.local`（已忽略提交），不打印；公钥 JWK 与校验串写入待执行 SQL。
5. **Nginx 本机配置**：`verification/local-e2e/nginx.conf.template`，两个 HTTPS 站点：
   - `127.0.0.1:18443`（客户）：`X-Entry-Channel: CLIENT`；
   - `127.0.0.1:18444`（运营）：`X-Entry-Channel: OPERATIONS`；
   - 都覆盖 `X-Real-IP`、清除 `Forwarded/X-Forwarded-*`，`/api/` 转 Gateway，其余转对应 Vite 开发服务器（保留热更新）。
   证书用 Homebrew openssl 生成自签名证书，放在忽略提交的目录。
6. **启动脚本**：`verification/local-e2e/start.sh` / `stop.sh`，按第 4 节顺序启动各进程，日志写到忽略提交的目录，每个服务等健康检查通过再起下一个。

### 步骤 2：写本机库（每项单独授权，均附回滚 SQL 与执行记录）

| # | 库 | 内容 | 回滚 |
|---|---|---|---|
| 2a | `mor_platform` | 执行已审阅的 `platform-support-initial-data.sql`（运营目录：目标、角色、权限、页面、路由，按原文件状态） | 按文件中的固定 ID 删除 |
| 2b | `mor_platform` | 引导管理员 `admin`（BOOTSTRAP_ADMIN，强制改密）+ 运营、客户各一条 ACTIVE 签名公钥 | 按固定 ID 删除 |
| 2c | `mor_platform` | 开放平台网关与页面配置管理（PLT-16/17 那组：路由 ACTIVE、管理员授权 ACTIVE），这样后续才能用管理接口登记入驻路由 | 恢复为 DISABLED / REVOKED |
| 2d | `mor_risk` | 执行 `risk-generic-materials-init.sql`（3 个审查方案、10 条材料要求）；`@approved_by` 用 2b 之后首个真人管理员 ID | 按固定 ID 删除 |
| 2e | MinIO | 设置 CORS 允许 `https://127.0.0.1:18443` 的 PUT（Content-Type、If-None-Match）与 GET | 恢复默认 |

`mor_cct` 不需要初始化数据（表单目录与行业在配置文件中）。

### 步骤 3：通过界面与管理接口完成初始化（不直接写库）

1. 运营站点用 `admin` 登录 → 强制改密 → 再登录 → 创建首个真人管理员（`bootstrap/administrator`）→ 新管理员登录并改密。
2. 用新管理员会话，按 `db/management-command-templates.json` 的 50 条命令依次调用管理接口：建 CLIENT 目标 `client-console-bff`、5 个权限、39 条路由、5 个页面资源；再把这批路由、权限、页面启用，给管理员角色授予 5 个入驻权限。
   由脚本执行，每条记录请求与结果（不记密钥），失败即停，可从断点继续。
3. Gateway 每 5 秒刷新一次配置快照，确认快照里出现 CLIENT 路由。

### 步骤 4：走一遍业务链路（验收）

1. 客户站点注册 → Mailpit 收验证邮件 → 验证 → 登录。
2. 填写个人或企业资料，上传 PDF/JPG/PNG（浏览器直传 MinIO → 格式检查 → 可用）→ 提交。
3. 运营站点进入“客户入驻审核” → 查看详情 → 下载材料 → 分别验证“退回”“通过”“拒绝”。
4. 客户站点看到对应结果提示；退回可修改后重提。
5. 结果写入执行记录：每一步截图或接口证据，以及发现的问题清单。

## 4. 进程与端口

| 进程 | 端口 | 启动要点 |
|---|---|---|
| Vault | 18107 | `VAULT_RUNTIME_MODE=local`，`mor_vault`，策略文件，注册到 Nacos `mor-local` |
| Platform | 18106 | Profile `local`；开启运营/客户认证、Redis 协调、补偿、入驻文件（MINIO）；邮件指向 Mailpit 且本机关闭 STARTTLS 要求 |
| CCT | 18101 | `onboarding.draft-enabled`/`recovery-enabled=true`；Nacos 命名空间改为 `mor-local` |
| Risk | 18104 | `onboarding.review.enabled`/`materials.enabled=true` |
| client-console-bff | 18202 | `client-console-bff.onboarding-enabled=true`，`console-origin=https://127.0.0.1:18443` |
| operations-console-bff | 18201 | `console-origin=https://127.0.0.1:18444` |
| API Gateway | 18080 | 开启 CLIENT 通道；运营、客户站点地址与签发方配置为上述 HTTPS 地址 |
| Vite（客户 / 运营） | 18302 / 18301 | 开发模式，由 Nginx 反向代理 |
| Nginx | 18443 / 18444 | HTTPS 自签名 |
| Mailpit | 11025 / 18025 | 新增 Docker 服务 |

所有服务只绑定 `127.0.0.1`；启动参数全部来自 `.env.local` 与命令行覆盖，不改各服务已提交的 `application.yml` 默认值。若发现某项只能通过修改代码才能本机启用，先停下报告，不顺手改。

## 5. 风险与已知限制

- **证书信任**：浏览器要信任三张自签名证书（Nginx 两站点共用一张、MinIO 一张），需要你在“钥匙串访问”里手动添加信任；不信任时上传会直接失败。
- **首次启动可能暴露配置缺口**：多个服务的“入驻”开关此前只在自动测试中打开过，真实启动可能缺配置项；遇到时按“先报告再修”处理。
- **客户审核后状态**：已知“拒绝后客户仍为 PENDING”、“通过后没有工作区入口”两个缺口，本次验收会看到，不在本方案内修。
- **数据隔离**：本机库会留下真实测试数据；`stop.sh` 只停进程，不清库。需要重来时按第 2 节回滚。
- **不扫病毒**：文件只做格式检查，按已确认的本机方案。

## 6. 需要你授权的动作

| 编号 | 动作 | 类型 |
|---|---|---|
| A1 | 新增 Mailpit 到 `local-environment/compose.yaml` 并启动 | 本机环境配置 |
| A2 | 拉取 `golang:1.27.1` 镜像在容器内编译 Vault（或你自行安装 Go） | 本机环境 |
| A3 | 在 `.env.local` 写入新生成的本机密钥与初始密码 | 本机配置 |
| A4 | 执行 2a 初始化目录 DML | SQL |
| A5 | 执行 2b 引导管理员与签名公钥 | SQL |
| A6 | 执行 2c 开放网关与页面配置管理 | SQL |
| A7 | 执行 2d Risk 入驻资料配置 | SQL |
| A8 | 设置 MinIO CORS | 本机环境配置 |
| A9 | 通过管理接口登记并启用入驻路由、权限、页面（步骤 3.2） | 本机业务配置 |

文件新增（脚本、模板、本文档、执行记录）属于普通编辑，不单独请示。
