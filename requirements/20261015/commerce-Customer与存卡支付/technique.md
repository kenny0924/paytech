# Customer 与存卡支付技术方案

> 状态：阶段 2+3 评审稿（2026-10-15，第 1 版）。依据 [需求](./requirement.md)；确认记录见 [delivery.md](./delivery.md)。

**规模（草案）：** Commerce 新增 2 张表、改 1 张表，Client API 新增 6 个路由、改 1 个请求，结账接口改 2 个；Payment 新增 1 张表、改 2 张表，内部接口新增 3 个、改 1 个；Platform、CCT 不改；结账页与运营页面见 §1.3 的前置缺口。

## 1. 现状差异

### 1.1 逐项差异

| 需求项 | 当前行为及证据 | 目标 | 缺口 |
|---|---|---|---|
| Customer（AC-01、AC-12） | Commerce 没有 Customer；`t_consumer_context.consumer_reference` 插入时写死 NULL（`ConsumerContextMapper.xml:26-38`） | 新表，Client API 管理 | 新建 |
| 建单关联（AC-02） | `POST /api/v1/checkouts` 无 `consumerReference`（`ClientCheckoutApi.java:36-41`）；唯一写事务 `CheckoutCreationServiceImpl.java:31-41` | 请求加字段，事务内查或建 Customer、订单记 `customer_id` | 扩展 |
| 存卡开通（AC-13） | CCT 没有任何按 Client 的开关或能力表；Checkout 解析接口 `POST /checkout-context/v1/resolutions` 不返回能力 | 按 Client 开关 | CCT 无落点，按 D-11 放 Commerce 本地 |
| 凭证验证（AC-06） | `t_payment_card_credential` 无验证状态；支付成功同步落定在 `PayActionActor.doInTransaction`（`attempts.payCompleted`、`intents.attemptSucceed`），`AuthorizeActionActor` 仍为 TODO | 成功事务内标记已验证 | 新增 |
| 生成已存卡（AC-05、AC-07、AC-08） | 无 | 支付成功事务内按同意生成或更新 | 新建 |
| 已存卡支付（AC-09、AC-10） | 申请工具只接受手输卡（`ApplyCardInstrumentRequestBody`） | 增加“已存卡 + CVV”的申请方式 | 扩展 |
| Client 管理已存卡（AC-04、AC-11） | 无 | Client API → Commerce → Payment | 新建 |
| 结账页（AC-09、AC-15～17） | 结账接口 `GET /checkouts/{token}`、`POST /payments` 无已存卡与保存选项；`checkout-web` 只有设计稿，没有前端代码 | 接口返回已存卡与可保存标志，提交支持两种方式 | 接口扩展；页面无代码可改，见 §1.3 |
| 渠道存卡交易标识（Q-06） | Platform 标准 API `PAY` 输入输出与 PayerMax 配置均无 stored credential、CIT/MIT、网络交易 ID 字段 | 首期按普通带 CVV 卡交易发送 | 不改 Platform（D-11） |

### 1.2 逐系统影响

| 系统 | 影响 | 改代码/模型/配置 |
|---|---|---|
| Commerce | Customer 表与 Client API；建单关联；存卡开关；结账接口展示已存卡、提交已存卡支付；调用 Payment 新接口；删除 Customer 后可靠通知 Payment | 代码、模型、SQL |
| Payment | 凭证验证状态；已存卡表；成功事务内存卡；已存卡申请工具；已存卡查询、删除、按 Customer 失效的内部接口 | 代码、模型、SQL |
| Platform | 不改；渠道仍按普通卡交易发送（Q-06） | 无 |
| CCT | 不改（无配置落点，D-11 的后备方案） | 无 |
| Gateway / Platform 路由注册 | Commerce Client API 目前没有任何路由注册（`api-gateway/README.md:41`），新增路由只更新 Route Manifest 文档 | 文档 |
| checkout-web、运营后台 | 见 §1.3 | — |

### 1.3 前置缺口（需你决定）

| 编号 | 缺口 | 影响 | 建议 |
|---|---|---|---|
| G-01 | Commerce 还没有接通 Payment 的 pay：`CheckoutCardPaymentPort` 是固定返回 PROCESSING 的 Mock（`CustomerCheckoutIntegrationConfiguration.java:98-115`，带 TODO） | 没有真实支付成功，就不会存卡；AC-05、AC-09 无法在 Commerce 入口端到端验证 | 把“Commerce 接通 Payment pay”纳入本需求的 Commerce 部分（它本来就是结账主链路的缺口）；或先单独做一个前置需求 |
| G-02 | `checkout-web` 没有前端代码，只有设计稿 | AC-15～17（视觉/交互）没有可改的页面 | 本需求只交付结账接口；视觉/交互验收移到 checkout-web 建设需求，本需求在设计稿中补“保存卡”和“已存卡列表”两个状态 |
| G-03 | 存卡开关放 Commerce 本地后，没有运营后台页面 | 运营无法自助开通 | 首期 Commerce 提供内部接口，开通靠初始化 DML 或内部调用；运营页面作为后续需求 |
| G-04 | 只有自动捕获（PAY）能成功，`AUTHORIZE` 仍为 TODO | 手动捕获订单暂时无法存卡 | 本需求只在 PAY 成功时存卡；`AUTHORIZE` 实现时同一挂点补上 |

## 2. 方案

### 2.1 Commerce：Customer

**表 `t_customer`**

| 字段 | 类型 | 说明 |
|---|---|---|
| `customer_id` | BIGINT PK | Snowflake |
| `client_id` | BIGINT | 所属 Client |
| `consumer_reference` | VARCHAR(128) | Client 传入的不透明引用，永久保存原值 |
| `active_reference` | VARCHAR(128) NULL | 有效时等于 `consumer_reference`，删除时置 NULL；`UNIQUE(client_id, active_reference)` 保证同一引用同时最多一个有效 Customer（MySQL 唯一索引允许多个 NULL） |
| `customer_status` | VARCHAR(16) | `ACTIVE` / `DELETED` |
| `deleted_time` | DATETIME(6) NULL | |
| `version` | BIGINT | CAS |
| `create_time` / `update_time` | DATETIME(6) | |

- **查或建：** 先按（`client_id`, `active_reference`）查；没有则插入，撞唯一键改读。建单在原有写事务内完成，Customer 查或建只操作本库。
- **删除：** 事务内 CAS 置 `DELETED`、`active_reference=NULL`，同事务登记补偿事件“失效该 Customer 的已存卡”；提交后执行，调用 Payment（§2.4）。Commerce 自身在列出和使用已存卡前都检查 Customer 有效，补偿事件只负责让 Payment 侧状态最终一致。
- **订单关联：** `t_commerce_order` 增加 `customer_id BIGINT NULL`，建单时写入，之后不改。`t_consumer_context.consumer_reference` 保持现状（历史列），不再作为身份来源。
- **引用校验：** 长度 1～128，限定字符集 `[A-Za-z0-9._:-]`；形如 Email（含 `@`）或纯 7～15 位数字（像电话）时拒绝。

**表 `t_client_stored_card_setting`**（D-11 的后备方案）

| 字段 | 类型 | 说明 |
|---|---|---|
| `client_id` | BIGINT PK | |
| `stored_card_enabled` | TINYINT(1) | |
| `version`、`create_time`、`update_time` | | |

没有记录视为未开通。

### 2.2 Commerce：对外接口

**Client API（新增 6 个，Route Manifest 同步）**

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/customers` | Body `{consumerReference}`；已有有效 Customer 时返回原记录（200），新建 201 |
| GET | `/api/v1/customers/{customerId}` | 已删除返回 `status=DELETED` |
| GET | `/api/v1/customers?consumerReference=` | 只查有效的 |
| DELETE | `/api/v1/customers/{customerId}` | 不可撤销 |
| GET | `/api/v1/customers/{customerId}/stored-cards` | 有效且未删除的已存卡：`storedCardToken`、`maskedPan`、`expiryMonth`、`expiryYear`、`createdAt` |
| DELETE | `/api/v1/customers/{customerId}/stored-cards/{storedCardToken}` | |

`POST /api/v1/checkouts` 增加可选 `consumerReference`；订单查询响应增加 `customerId`（未关联时省略）。跨 Client 访问统一返回 404。

**结账接口（Customer API）**

- `GET /checkouts/{token}` 增加：
  - `cardSaving.available`：Client 已开通且订单有有效 Customer；
  - `storedCards[]`：同上条件下，该 Customer 有效、未过期的已存卡（`storedCardToken`、`last4`、`expiryMonth`、`expiryYear`）。
- `POST /checkouts/{token}/payments` 的 Body 二选一：
  - `card{...}` + 可选 `saveCard: true`（仅 `cardSaving.available` 时接受）；
  - `storedCard{storedCardToken, cvv}`（`cvv` 必填）。

### 2.3 Commerce → Payment 调用

| 场景 | 调用 |
|---|---|
| 手输卡 | `POST /payment-instrument/v1/card-tokens`，Body 增加可选 `customerId`、`saveCard`（两者同时出现才保存） |
| 已存卡 | 同一接口，`card` 与 `storedCard{storedCardToken, cvv}` 二选一，并带 `customerId` |
| 支付 | `POST /payment-execution/v1/intents/{id}/pay`（G-01 接通） |
| 列出 / 删除已存卡 | Payment 新增内部接口（§2.4） |

`customerId` 是 Commerce 已核对过的订单 Customer；Payment 不接收、不保存 `consumer_reference`。

### 2.4 Payment Card：已存卡

**表变更**

- `t_payment_card_credential` 增加 `verified_time DATETIME(6) NULL`：首次支付成功时写入，之后不改。
- `t_payment_card_credential` 另增加明文 `expiry_month`、`expiry_year`（阶段 4 补充）：已存卡列表要展示有效期并过滤已过期的卡，而有效期原只在加密字段中，列表与支付成功事务内都不能为此调用 Vault；卡号已加密，有效期单独明文保存符合 PCI。
- `t_payment_card_instrument` 增加：
  - `customer_id BIGINT NULL`：本次支付关联的 Commerce Customer；
  - `save_requested TINYINT(1)`：消费者是否同意保存；
  - `payment_stored_card_id BIGINT NULL`：用已存卡支付时指向已存卡。
- 新表 `t_payment_stored_card`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `payment_stored_card_id` | BIGINT PK | |
| `stored_card_token` | CHAR(32) ascii UK | 对外 Token，`sc_` 前缀加随机串；按 Client 生成，不可推算 |
| `client_id`、`customer_id` | BIGINT | 归属 |
| `payment_card_id` | BIGINT | 卡号唯一的那层 |
| `active_card_id` | BIGINT NULL | 有效时等于 `payment_card_id`，删除时置 NULL；`UNIQUE(client_id, customer_id, active_card_id)` |
| `payment_card_credential_id` | BIGINT | 当前使用的已验证凭证 |
| `stored_card_status` | VARCHAR(16) | `ACTIVE` / `DELETED` |
| `consent_time` | DATETIME(6) | 最近一次同意保存的时间 |
| `source_card_instrument_id` | BIGINT | 首次保存来源的交易工具（可追到 Intent 与订单） |
| `deleted_time`、`version`、`create_time`、`update_time` | | |

**支付成功时（同事务，只写本库）**

`PayActionActor.doInTransaction` 在 `payCompleted` 之后调用 Card 的本地服务：

1. 本次交易工具的凭证若未验证，写 `verified_time`；
2. 若交易工具 `save_requested=1` 且有 `customer_id`：按（Client、Customer、卡）找有效已存卡——没有就新建（新 Token）；有就把当前凭证更新为本次凭证、刷新 `consent_time`，Token 不变。

都在原成功事务内，不调外部服务；失败随成功事务一起回滚（不会出现“支付成功但没记成功”）。

**用已存卡申请工具**

`PrepareCardInstrumentProcessor` 增加分支：按 Token 查已存卡，校验 Client 与 Customer 一致、状态有效、凭证未过期；取回 DEK，不新建卡与凭证；新建交易工具（指向已存卡的凭证、记 `payment_stored_card_id`），CVV 按交易工具加密暂存。原提交重放比较已存卡 Token。

**内部接口（Commerce 调用）**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/payment-card/v1/stored-cards?clientId=&customerId=` | 有效且未过期的已存卡；`maskedPan` 为前 6 后 4（取 `card_bin` 前 6 位） |
| DELETE | `/payment-card/v1/stored-cards/{storedCardToken}?clientId=&customerId=` | 置 `DELETED`，幂等 |
| POST | `/payment-card/v1/customers/{customerId}/stored-cards/invalidation` | Body `{clientId}`；该 Customer 的全部已存卡置 `DELETED`，幂等 |

### 2.5 存卡开关

Commerce 本地表（§2.1）。建单不冻结开关；结账接口和提交支付时实时读取，关闭后已存卡保留但不展示、不可用（AC-13）。维护方式见 G-03。

### 2.6 安全与日志

- `consumer_reference`、`customerId` 与已存卡 Token 不进入普通日志和 MQ；结账接口只返回后四位与有效期。
- Client API 的已存卡响应不含卡组、内部卡 ID、指纹、8 位 BIN（AC-14）。
- 结账页使用已存卡时 CVV 必填（D-05），CVV 仍按交易工具一次性暂存。

## 3. 数据库

| 系统 | 变更 | SQL |
|---|---|---|
| Commerce | 新表 `t_customer`、`t_client_stored_card_setting`；`t_commerce_order` 加 `customer_id` | `db/commerce/ddl.sql`（阶段 4 编写） |
| Payment | 新表 `t_payment_stored_card`；`t_payment_card_credential` 加 `verified_time`；`t_payment_card_instrument` 加三列 | `db/payment/ddl.sql`（阶段 4 编写） |

两库均先改 PDManer，再同步 Schema 与测试 DDL；本机测试库执行需另行授权。

## 4. 测试计划（概要）

| 系统 | 入口 | 覆盖 |
|---|---|---|
| Commerce | Client API HTTP | Customer 创建、复用、跨 Client、删除后新建、引用校验；建单关联；已存卡查询与删除（Payment 为测试 HTTP 服务） |
| Commerce | 结账接口 HTTP | 开关与 Customer 条件下的 `cardSaving`、`storedCards`；两种提交方式；已存卡 CVV 必填 |
| Payment | 申请工具 / pay / 内部接口 HTTP | 成功后凭证验证、存卡、重复存卡更新凭证、跨 Client Token 不同；已存卡申请与渠道读卡一致；删除与按 Customer 失效；支付失败不存卡 |

## 5. 实现拆分（Q-07）

| 部分 | 系统 | 依赖 |
|---|---|---|
| P1 | Payment：§2.4 全部 | 无 |
| C1 | Commerce：Customer、开关、Client API、建单关联 | 无 |
| C2 | Commerce：结账接口与 Payment 调用，含 G-01 | P1、C1 |
| D1 | 文档：Client API Route Manifest、结账设计稿 | C1、C2 |

建议顺序：P1 与 C1 并行，再做 C2。
