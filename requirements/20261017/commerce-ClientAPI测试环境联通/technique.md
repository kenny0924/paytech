# Client API 在 mor-test 联通 — 技术方案

> 第 1—4 节为阶段 2 现状差异与影响分析（2026-10-17 已确认，“按建议”：D1—D6）；第 5—11 节为阶段 3 部署与路由方案（评审稿）。

## 1. 现状核对

| 项 | 现状 | 依据 |
|---|---|---|
| Commerce 库 | test `mor_commerce`、`mor_payment` 均 **0 张表**；`commerce_app@%` 用户与密码变量已存在 | `test-environment/compose.yaml:12-13` |
| Commerce Schema | `commerce-schema.sql` 17 张表 + `commerce-initial-data.sql`；test 初始化脚本只覆盖 Platform、Risk | `commerce-service/docs/database/sql/`；`test-environment/scripts/initialize.py:118-131` |
| 部署脚本 | Compose、`build.sh`、`deploy.sh` 都没有 Commerce；可照 risk-service 写法（`x-java`、`x-discovery`、固定 IP、健康探针） | `compose.applications.yaml:68-107`；`scripts/build.sh:56-72`；`deploy.sh:28-48` |
| Commerce 启动参数 | 无默认值必须给：`COMMERCE_CARD_PAYMENT_METHOD_CODE`、`COMMERCE_CHECKOUT_PUBLIC_ORIGIN`、Snowflake region/worker、`compensation.instance-id`；RabbitMQ vhost 默认 `/mor-local` 要改 `/mor-test`；`SERVER_ADDRESS=0.0.0.0` | `application.yml` |
| RabbitMQ | Commerce 启动时自行声明 `topic_payment_result`、`cg_commerce_payment_result` 及死信；无需预建 | `PaymentResultConsumerConfiguration.java:25-80` |
| Customer 接口 | 删除 Customer 本地删除并登记存卡失效补偿任务，HTTP 调用本身不碰 Payment；查询不碰 Payment；列存卡调 Payment | `CustomerServiceImpl.java:60-66` |
| Gateway 通道 | 不按域名识别，只认 Nginx 覆盖写入的 `X-Entry-Channel: CLIENT_API`；Gateway 无需新增环境变量 | `TrustedRequestContextWebFilter.java:69-80` |
| Nginx | 只有 8080（OPERATIONS）与 8443（CLIENT），宿主机 28080/28443 | `nginx.conf:23-137` |
| 路由设计 | 冻结清单：Target `commerce-client-api`（`commerce-service`，根路径 `/commerce/client-gateway-api/v1`），10 条 `client-api-commerce-*` 路由，POST 转发 `Idempotency-Key`；Scope `checkouts.write`、`orders.read`、`orders.cancel`、`refunds.read`、`refunds.write`。**Customer 4 个操作没有设计 Scope 与路由** | 设计 `:185-195`、`:241-265` |
| Scope 目录 | test `t_api_scope` 只有夹具 `orders.read` | 接入自助发布记录 |
| 下游 Header | Gateway 只转发路由白名单 + `X-Request-Id`，再写 `X-Gateway-Client-Id`、`X-Gateway-Credential-Id`；与 Commerce 合同一致 | `ClientApiDownstreamIdentityGlobalFilter.java:34-37` |
| 请求幂等 | **Gateway 未实现 `Idempotency-Key`**（决定已归 Gateway，台账 Pending）。Commerce 6 个写接口要求该 Header 存在但不使用其值；`command-idempotency` 依赖仍在，但已无代码调用，开关无行为差异 | `CURRENT_IMPLEMENTATION.md:516-522` |
| Gateway 上线门禁 | GATEWAY-CLIENT-API-01：全量测试 39 个与基线相同的失败、分支覆盖 78.7%；租户缺失时 503 还是 401 待 Platform 确认；要求先 DISABLED 登记、逐条启用并核对路由数 | `CURRENT_IMPLEMENTATION.md:877`、第 13 节 |
| Webhook 出站 | 只允许 `allowed-schemes`（test 为 https）；SSRF 防护拦截 172.16/12，test 后端网段 172.30.73.0/24 在内，需 `trusted-networks` 放行；**没有 Webhook 专用信任证书配置**，自签证书只能靠 JVM 全局信任库；证书 SAN 必须匹配主机名，主机名不能含下划线 | `WebhookAddressGuard.java:25-66`；`WebhookHttpSender.java:49-59` |
| 现有接收端 | 本机 `developer_flow.py` 内的 HTTP 接收端（127.0.0.1、无 TLS），签名校验可复用 | `developer_flow.py:62-95` |
| XXL-Job | `compose.scheduler.yaml` 已定义 admin（profile `scheduler`，127.0.0.1:28090），`xxl_job` 库已初始化，未运行；`compose.sh` 不带该 profile；执行器与任务须在 Admin 界面手工登记；Commerce 未配置执行器（`xxl.job.executor.enabled` 默认关） | `compose.scheduler.yaml:37-77`；`paytech-foundation/README.md:758-796` |
| Platform 投递重试 | Platform 投递也是补偿任务，首投递即时，**后续重试只靠 XXL-Job 扫描**；test 现在没有扫描，失败的投递不会重试 | `WebhookDeliveryHandler.java:46-148` |

## 2. 差异（要新增或改动的）

| 编号 | 改动 | 归属 / 影响面 |
|---|---|---|
| G1 | `mor_commerce` 执行 17 表 Schema 与初始化数据（人工 SQL） | test 数据库；需 SQL 授权 |
| G2 | Compose 增加 `commerce-service`（固定 IP 172.30.73.30、Snowflake worker 未占用值）；`build.sh`、`deploy.sh` 加 Commerce | `test-environment/` 共享文件 |
| G3 | Nginx 新增 Client API 入口（如 8444 ssl → 宿主机 127.0.0.1:28444），写 `X-Entry-Channel: CLIENT_API`、`X-Real-IP`，无前端 | Nginx 重启影响所有控制台入口（秒级） |
| G4 | Platform 登记 Target `commerce-client-api` 与路由、`t_api_scope` 补 Scope 目录 | test Platform 配置；Gateway Snapshot 刷新 |
| G5 | 验收用 HTTPS 接收端容器；test Platform 加 `webhook.trusted-networks=<接收端>/32` 与信任证书 | Platform 重启；Webhook 会话的配置 |
| G6 | 启动 XXL-Job Admin，Commerce 开执行器并登记 `compensationEventScan` | 新容器；Admin 手工登记 |
| G7 | 验收脚本 `client_api_flow.py`（凭证 → Client API → Webhook）与发布记录 | 本需求目录 |

产品代码：预计不改。

## 3. 影响与风险

- **Gateway 门禁**：台账要求“全量门禁与前置完成后”才能启用 CLIENT_API 路由。这是对生产的约束；在 test 上启用会先于门禁。
- **请求重放**：Gateway 不处理 `Idempotency-Key`，客户重试 POST 不会拿到原响应。业务唯一性仍在（`clientOrderId`、`clientRefundId` 永久唯一，重复取消返回原结果），所以不会重复建单或重复退款，只是重试响应可能是冲突而非原结果。
- **JVM 信任库**：给 Platform 换信任库会影响它所有出站 TLS（SES、渠道沙箱等），必须用“JDK 默认 cacerts + test CA”的合并库，不能只放 test CA。
- **共享环境**：Nginx、Platform 重启和 Compose 改动影响其他会话，需逐次协调。
- **Commerce 固定 Mock**：建单使用固定 CCT 商业事实（主体 `AE001`）、零税、假合同文档（阶段 1 Q3 已接受）。
- **Payment 不在 test**：Client API 建单不调 Payment（Intent 在消费者结账时创建）；`order.paid` 由手工 PaymentSucceeded 夹具产生（阶段 1 Q1）。

## 4. 已确认的决定

| 编号 | 问题 | 建议 |
|---|---|---|
| D1 | Customer 4 个操作在冻结设计里没有 Scope 和路由，阶段 1 定的 13 个要不要保留？ | **只开放设计内的 10 条路由**（建单、订单、退款）。Customer 需要新增公开 Scope（如 `customers.read/write`），属对外合同变更，另立需求；本期从 13 个收为 10 个 |
| D2 | Gateway 未实现 `Idempotency-Key`，test 上怎么处理？ | 按设计白名单原样转发，不做重放；作为已知限制写进发布记录。Commerce `command-idempotency` 保持默认（无行为影响） |
| D3 | Gateway CLIENT_API 生产门禁未满足，test 上能否启用？ | 可以，仅限 test：按台账流程先登记为 DISABLED，逐条启用并核对 Gateway 路由数，失败先撤销验收凭证再停路由；不据此宣称生产可上线 |
| D4 | 接收端 HTTPS 用什么证书、Platform 怎么信任？ | 用 test 已有 CA 签发主机名 `webhook-receiver` 的证书；Platform 挂载“cacerts + test CA”合并信任库（`JAVA_TOOL_OPTIONS` 指定），`trusted-networks` 只放接收端 /32。不放开 http |
| D5 | XXL-Job 只给 Commerce，还是同时给 Platform？ | 本期同时登记 Commerce 和 Platform 两个执行器：Platform 现在失败的 Webhook 投递在 test 上永远不重试，补上后 AC-05 才完整（需 Webhook 会话同意） |
| D6 | test 部署文件（Compose、build/deploy 脚本、Nginx）改动是否随本需求提交？ | 是，只提交 Commerce 条目、Client API 入口与接收端相关段落 |

## 5. 部署拓扑

```text
宿主机 127.0.0.1:28444 ──TLS──> nginx:8444 (X-Entry-Channel: CLIENT_API, X-Real-IP)
   └─> api-gateway:18080  ── PLT-I03 凭证 + Scope ──> lb://commerce-service/commerce/client-gateway-api/v1/...
commerce-service (172.30.73.30) ── Risk / CCT / Platform Webhook 受理
platform-support-service ──HTTPS──> webhook-receiver (172.30.73.31，test CA 证书)
xxl-job-admin ──> commerce-service:9999、platform-support-service:9999（执行器）
```

## 6. Commerce 部署（G1、G2）

| 项 | 方案 |
|---|---|
| 构建 | 从干净提交 worktree 构建 `paytech-commerce-web` 可执行 JAR → `artifacts/commerce-service.jar`，镜像 `mor-test/commerce-service:client-api-20261017`（通用 `java/Dockerfile`）；`build.sh` 增加同样步骤 |
| Compose | 照 risk-service：`x-java` + `x-discovery`，`SERVER_ADDRESS=0.0.0.0`，`backend` 固定 IP `172.30.73.30`，健康探针 `/commerce/actuator/health/readiness`（端口 18102） |
| 数据库 | `MOR_COMMERCE_DB_URL=jdbc:mysql://mysql:3306/mor_commerce…`，用户 `commerce_app`，密码 `MOR_COMMERCE_DB_PASSWORD`（已在 `.env.test`） |
| Redis / RabbitMQ | 同 risk-service；`COMMERCE_RABBITMQ_VHOST=/mor-test` |
| 必填参数 | `COMMERCE_CARD_PAYMENT_METHOD_CODE=CARD`、`COMMERCE_CHECKOUT_PUBLIC_ORIGIN=https://localhost:28443`（test 无结账页，仅占位）、Snowflake region 0 / worker 28、`COMPENSATION_INSTANCE_ID=commerce-test` |
| 补偿执行器 | `xxl.job.executor.enabled=true`、`app-name=commerce-service`、`ip=172.30.73.30`、`port=9999`、`log-path=/tmp/xxl-job`、`admin.addresses=http://xxl-job-admin:8080/xxl-job-admin`、`access-token=${MOR_XXL_JOB_ACCESS_TOKEN}` |
| SQL | `mor_commerce` 为空：执行 `commerce-schema.sql`（17 表）与 `commerce-initial-data.sql`，执行前核对空库，执行后核对表数与 PDManer 一致；回滚为删除这 17 张表（库内无其他数据） |
| deploy.sh | 增加 Commerce 阶段（在 Platform 之后） |

## 7. Client API 入口与路由（G3、G4）

**Nginx**：新增 `server { listen 8444 ssl; }`，证书沿用 `tls/server.crt`（localhost）；只代理 `^~ /api/v1/` 到 Gateway，其余 404；写 `X-Entry-Channel CLIENT_API`、`X-Real-IP $remote_addr`，清空 `Forwarded`/`X-Forwarded-*`；保留合法调用方 `X-Request-Id`，否则用 `$request_id`。Compose 发布 `127.0.0.1:28444:8444`。

**Scope 目录（DML）**：`t_api_scope` 补 `checkouts.write`、`orders.cancel`、`refunds.read`、`refunds.write`（`orders.read` 已有）；ID 取 `9300000000000002—05`，执行前查重。

**Target 与路由**：按设计第 4 节冻结清单登记 Target `commerce-client-api` 与 **9 条路由**——清单中 `client-api-commerce-checkout-read`（`GET /api/v1/orders/{orderNo}/checkout`）Commerce 尚未实现（Client API 合同模块无此操作），不登记，避免对外暴露必然 404 的路由（Scope、Query 白名单、Body 上限、POST 转发 `Accept/Content-Type/Idempotency-Key`、GET 转发 `Accept`、Guard 规则 CLIENT_IP/AUTHENTICATED_SUBJECT/TENANT）。新增 `register_client_api.py`：经运营管理接口 `/gateway-admin/v1/target-services`、`/routes` 先建为 DISABLED，再逐条启用，每条启用后核对 Gateway Snapshot 中 CLIENT_API 路由数递增；失败按 D3 回退。

## 8. Webhook 接收端（G5）

| 项 | 方案 |
|---|---|
| 程序 | `webhook_receiver.py`（标准库 `ThreadingHTTPServer` + TLS），收到的请求按序写入挂载目录（JSON：头部、正文 Base64、时间），始终返回 204；签名校验在验收脚本里做，复用 `developer_flow.signature_ok` |
| 镜像 | `python:3.13-alpine`（固定 digest）；只读根、非 root、只接入 `backend`，IP `172.30.73.31`，主机名 `webhook-receiver`；不发布宿主机端口 |
| 证书 | 用 `private/test-ca.key` 签发 SAN=`webhook-receiver` 的服务端证书，放 `private/webhook-receiver/` |
| Platform | 挂载合并信任库（Temurin 镜像自带 cacerts + `test-ca.crt`，`keytool` 生成到 `private/platform-truststore.p12`），`JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=… -Djavax.net.ssl.trustStorePassword=…`；`WEBHOOK_TRUSTED_NETWORKS=172.30.73.31/32` |
| Compose | 接收端放 `compose.acceptance.yaml`（profile `acceptance`），与业务服务分开，验收后可停 |

## 9. XXL-Job（G6）

1. `docker compose … -f compose.scheduler.yaml --profile scheduler up -d`（`xxl_job` 库已初始化，schema-init 只校验版本）。
2. Commerce、Platform 设执行器参数（Platform：`app-name=platform-support-service`、`ip=172.30.73.21`、`port=9999`）。
3. Admin 界面（127.0.0.1:28090）登记两个执行器组与两个任务：BEAN `compensationEventScan`、`SHARDING_BROADCAST`、`SERIAL_EXECUTION`、失败重试 0、每分钟一次。登记步骤与截图式记录写入发布记录。

## 10. 验收脚本（G7）

`client_api_flow.py`（`E2E_TARGET=test`）：
1. 用已批准验收 Client 登录 Client Console，自助建凭证（5 个 Scope）与 Webhook 端点 `https://webhook-receiver/hook`。
2. 经 `https://localhost:28444/api/v1/` 建单、查单、按 clientOrderId 查、取消（→ `order.cancelled`）。
3. 无凭证 / 错凭证 / 缺 Scope 的凭证分别被拒（401/401/403）。
4. 另建一单 → 发布 PaymentSucceeded 夹具 + 支付投影夹具（标注）→ `order.paid` → 建退款（→ `refund.created`）→ 查退款、可退金额、订单退款列表。
5. AC-05：暂停 Platform 后取消订单，确认任务 ERROR；恢复 Platform，等待 XXL-Job 扫描补交，接收端收到 1 次且内容与冻结一致。
6. 逐个读取接收端目录，校验签名、Envelope 与 GET 结果一致；日志无完整凭证、`whsec_`、载荷。

## 11. 发布顺序与回退

| 步骤 | 内容 | 回退 |
|---|---|---|
| 1 | 备份 `mor_platform` 路由 / Scope 相关表、Compose 与 Nginx 文件、容器 inspect | — |
| 2 | Commerce SQL（DDL + 初始化 DML） | 删除 17 张表 |
| 3 | 启动 XXL-Job Admin | 停容器 |
| 4 | 部署 Commerce | 停容器 |
| 5 | 起接收端；替换 Platform（信任库、`trusted-networks`、执行器） | Platform 换回原镜像与原 env |
| 6 | Nginx 加 8444 入口并重载 | 恢复原配置重载 |
| 7 | Scope DML；路由先 DISABLED 再逐条启用 | 撤销验收凭证 → 停用路由与 Target |
| 8 | XXL-Job 登记执行器与任务 | 停任务 |
| 9 | 验收脚本、日志检查、发布记录 | — |

每次重启 Platform 或 Nginx 前通知相关会话并取得同意。
