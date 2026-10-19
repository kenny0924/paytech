# 阶段 5 本机全链路冒烟（commercial 真实进程 + HTTP）

> 任务卡：[tasks/F-阶段5本机冒烟.md](../tasks/F-阶段5本机冒烟.md)。执行日期 2026-10-20（UTC 2026-10-19 20:48–20:55）。
> 结论：9 步全部完成，功能**通过**；未发现业务代码缺陷。发现 1 处任务卡环境参数错误、1 处任务卡数据缺口（均已按下文处理），以及 3 条非阻塞观察项。

## 1. 环境

| 项 | 值 |
|---|---|
| 源码 | detached worktree `/Users/kenny/Desktop/Workspace/Code-MoR-smoke`，提交 `0ecc5713`（集成分支 A/T0/D/B/C） |
| 构建 | `cd paytech && JAVA_HOME=jdk-25 ./mvnw -o -q -pl commercial/paytech-commercial-app -am -DskipTests package`；制品 `paytech-commercial-app-1.0.0-SNAPSHOT-exec.jar`（复制到会话临时目录后运行，避免其他构建覆盖） |
| JVM | JDK 25.0.1，`java -jar …-exec.jar --spring.cloud.nacos.discovery.enabled=false`；端口 18108 / 管理 19108 |
| 数据库 | `jdbc:mysql://127.0.0.1:13306/mor_commercial_lineb`，用户 `commercial_app`（密码取 `MOR_COMMERCIAL_DB_PASSWORD`） |
| RabbitMQ | 127.0.0.1:5672，vhost `/mor-local`（`MOR_RABBITMQ_*` → `COMMERCIAL_RABBITMQ_USERNAME/PASSWORD/VHOST`） |
| Redis | 127.0.0.1:16379（`MOR_REDIS_PASSWORD` → `COMMERCIAL_REDIS_PASSWORD`） |
| 其他环境变量 | `COMMERCIAL_SNOWFLAKE_REGION_ID=0`、`COMMERCIAL_SNOWFLAKE_WORKER_ID=30`（见 §4 E1）、`COMMERCIAL_XXL_JOB_ENABLED=false` |
| Nacos | 关闭服务发现；client-master 未启动 |
| 事件观察 | 管理 API（127.0.0.1:15672）建临时队列 `smoke-commercial-stage5-tmp`（非持久、`x-expires=1h`），绑定 fanout `commercial.events`；结束时已删除 |
| 操作者 | Maker `900001`，Checker `900002`；写入头 `X-Caller-Service: operations-console-bff` |
| 进程日志 | `/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/logs/commercial-smoke.log` |
| 请求/响应全量记录 | [stage5-smoke-transcript.jsonl](stage5-smoke-transcript.jsonl)（61 条，不含凭据） |

## 2. 冒烟数据标识

| 对象 | ID / 代码 |
|---|---|
| 具体画像 | `SMK_CARD_VISA_CREDIT_DOM` = 238040976449462272 |
| 兜底画像 | `SMK_CARD_DEFAULT` = 238040977296711680 |
| 退役画像 | `SMK_UNUSED_RETIRE` = 238040977980383232 |
| Apple Pay 画像（补充，见 §4 E2） | `SMK_APPLE_PAY` = 238041331799285760 |
| 合同 | `SMK-CLIENT-20261020-01` = 238041051456200704，Client 770000000001，OE `AE001` |
| 开通项 | CARD_VISA = 238041135459721216；APPLE_PAY = 238041156468989952 |
| 条款方案 | `SMK-CLIENT-PRICING-01` = 238041176094138368（CLIENT_PRICING） |
| 版本 | versionNo 1 = 238041192099602432（APPROVED 搁置）；versionNo 2 = 238041349469888512（基线，现 SUPERSEDED）；versionNo 3 = 238041841470136320（换版，现 ACTIVE） |

下文“v1”指基线 versionNo 2，“v2”指换版 versionNo 3（原因见 §4 E2）。

## 3. 逐步结果

### 步骤 1 画像 —— 通过

| 请求 | 关键入参 | 状态 | 关键出参 |
|---|---|---|---|
| `POST /pricing-profiles/v1` | `SMK_CARD_VISA_CREDIT_DOM`，PAYMENT_METHOD，CARD，条件 CARD_BRAND=VISA、FUNDING_TYPE=CREDIT、ISSUER_REGION=DOMESTIC | 200 | DRAFT，changeVersion 1 |
| `POST /pricing-profiles/v1` | `SMK_CARD_DEFAULT`，CARD，`conditions=[]` | 200 | DRAFT |
| `POST /pricing-profiles/v1` | `SMK_UNUSED_RETIRE`，CARD_BRAND=MASTERCARD | 200 | DRAFT |
| `POST …/{具体}/activate`、`POST …/{兜底}/activate` | expectedChangeVersion 1 | 200 ×2 | ACTIVE，changeVersion 2 |
| `POST …/{第三}/retire` | 1 | 400 | `BUSINESS_CHECK_FAILED`：只有 ACTIVE 画像可以退役（DRAFT 不可直接退役，符合状态机） |
| `POST …/{第三}/activate` → `retire` | 1 → 2 | 200 → 200 | ACTIVE → RETIRED，changeVersion 3 |
| `GET /pricing-profiles/v1?kind=PAYMENT_METHOD&status=ACTIVE` | | 200 | 仅返回两个 ACTIVE 画像 |

### 步骤 2 合同 —— 通过

| 请求 | 关键入参 | 状态 | 关键出参 |
|---|---|---|---|
| `POST /agreements/v1` | CLIENT、MOR、SETTLE_THROUGH_PLATFORM、PLATFORM，entityNo AE001，参与方 CLIENT + OPERATING_ENTITY，SIGNING_DOCUMENT（fileId 990000000001 + SHA-256） | 200 | DRAFT，changeVersion 1 |
| `POST …/submit-for-signature` | 1 | 200 | PENDING_SIGNATURE，2 |
| `POST …/parties/880000000001/acceptance` | CLICK_ACCEPTANCE，2 | 200 | PENDING_SIGNATURE，3 |
| `POST …/parties/880000000002/acceptance` | CLICK_ACCEPTANCE，3 | 200 | SIGNED，4 |
| `POST …/activate` | 4 | 200 | ACTIVE，5 |
| `GET /agreements/v1/{id}` | | 200 | `signedTime` = 最后接受时间，两参与方 `evidenceDocumentId` 指向签署文件 |

事件：临时队列收到 1 条 `AgreementStatusChanged`，`message_id=AGREEMENT:238041051456200704:AgreementStatusChanged:5`，`aggregateVersion=5`，payload `status=ACTIVE`、`entityNo=AE001`、`clientId=770000000001`、`changeVersion="5"`，带 `payloadHash`。

### 步骤 3 订阅与开通 —— 通过

| 请求 | 关键入参 | 状态 | 关键出参 |
|---|---|---|---|
| `PUT …/products` | `[ACQUIRING]`，5 | 200 | changeVersion 6 |
| `POST …/capabilities` | CARD_VISA，AE001，CURRENCY=AED、CURRENCY=USD、REQUIRE_3DS=1，`rollingReserveRate=0.05`、`rollingReserveDays=180` | 200 | ENABLED，7 |
| `POST …/capabilities` | APPLE_PAY，AE001，CURRENCY=AED | 200 | ENABLED，8 |

`GET` 回读：`productCodes=[ACQUIRING]`，两开通项约束与保证金字段完整。

### 步骤 4 条款 —— 通过（数据按 §4 E2 补充）

| 请求 | 关键入参 | 状态 | 关键出参 |
|---|---|---|---|
| `POST /commercial-terms/v1/plans` | `SMK-CLIENT-PRICING-01`，CLIENT_PRICING | 201 | DRAFT，changeVersion "1"，`entityNo=AE001` |
| `POST …/plans/{planId}/versions` | BLENDED，EXCLUSIVE，"1" | 201 | versionNo 1，DRAFT，"2" |
| `PUT …/versions/{v}/prices` | 4 条价格（AED/USD × 具体 p1 / 兜底 p2），组件 CAPTURE_PCT 0.025 + CAPTURE_FIXED 1.00、AUTH_FIXED 0.10、REFUND_FIXED 2.00 RETURN_PRORATA、CHARGEBACK_FIXED 100；结算 T+2 AE_BUSINESS_DAYS UTC 86399 | 200 | "3" |
| `POST …/submit`（900001） | "3" | 200 | PENDING_APPROVAL |
| `POST …/approve`（900001，maker=checker） | "4" | **400** | `审批人不能是提交人: 900001`（预期拒绝） |
| `POST …/approve`（900002） | "4" | 200 | APPROVED，"5" |
| `POST …/schedule` | effectiveFrom now+70s | **400** | `激活校验 3（覆盖完整）：开通项没有命中的价格: AE001/APPLE_PAY/AED`（见 §4 E2） |
| 补充：`POST /pricing-profiles/v1` + activate | `SMK_APPLE_PAY`，APPLE_PAY，无条件 | 200 / 200 | ACTIVE |
| `POST …/versions`（copyFrom versionNo 1） | expectedChangeVersion "6" | 409 | `CONCURRENT_REQUEST`：当前 5（操作员输入错误，CAS 正确拒绝） |
| `POST …/versions`（copyFrom versionNo 1） | "5" | 201 | versionNo 2，复制得 4 条价格各 5 个组件（经 `GET /commercial-term-versions/v1/{v}/prices` 核对） |
| `PUT …/prices` | 原 4 条 + AED × `SMK_APPLE_PAY` p3（CAPTURE + REFUND 组件） | 200 | "7" |
| submit（900001）→ approve（900002） | | 200 → 200 | APPROVED，"9" |
| `POST …/schedule` | effectiveFrom `2026-10-19T20:52:39Z` | 200 | SCHEDULED，"10" |
| `POST …/activate`（提前） | "10" | 400 | `激活校验 1：生效起点未到`（预期） |
| `POST …/activate`（到点后） | "10" | 200 | ACTIVE，"11" |

事件：收到 1 条 `CommercialTermVersionActivated`，`message_id=COMMERCIAL_TERM_PLAN:238041176094138368:CommercialTermVersionActivated:11`，payload `versionId=238041349469888512`、`versionNo="2"`、`effectiveFrom=2026-10-19T20:52:39Z`、`supersededVersionId=null`。

### 步骤 5 命中 —— 通过

`POST /commercial-prices/v1/resolutions`，termVersionId = v1，USD，CARD，contextAsOfTime `2026-10-19T20:52:55Z`：

| 场景 | 状态 | 结果 |
|---|---|---|
| VISA / CREDIT / DOMESTIC | 200 | `priceId=238041407628107788`，`SMK_CARD_VISA_CREDIT_DOM`，priorityNo 1，5 组件，`settlement` T+2 / AE_BUSINESS_DAYS / UTC / 86399，`pricingModel=BLENDED`、`feeTaxTreatment=EXCLUSIVE` |
| 缺 FUNDING_TYPE | 422 | `PRICING_FACTS_INCOMPLETE`：缺少定价事实: FUNDING_TYPE |
| AMEX / CREDIT / DOMESTIC | 200 | `SMK_CARD_DEFAULT`（兜底），priorityNo 2，`conditions=[]` |

`GET /commercial-prices/v1/238041407628107788` → 200，同一画像、版本、币种与 5 个组件。

### 步骤 6 费率卡 —— 通过

`GET /rate-cards/v1?clientId=770000000001&asOf=2026-10-19T20:53:01Z&currency=AED` → 200。

- 顶层：`clientId`、`asOf`、`currency=AED`、`agreements[]`。
- 合同块：`businessMode=MOR`、`fundMode`、`pricingAuthority`、`products=[ACQUIRING]`、`capabilities[]`（CARD_VISA 带 `dimensionCode=CARD_BRAND/VISA`、约束、保证金 0.05/180；APPLE_PAY 带 AED 约束）。
- `termVersions[]`：1 条，versionNo 2，`status=ACTIVE`、`timing=CURRENT`、`effectiveFrom=20:52:39Z`。
- 币种分块只有 AED（USD 按过滤条件排除）：CARD_VISA/AED 列出具体画像 p1 与兜底 p2，APPLE_PAY/AED 列出 `SMK_APPLE_PAY` p3。
- 每个画像含 `priceId`、`profileName`、`specificity`、`settlement` 与按计费事件分组的组件（AUTH、CAPTURE[PCT+FIXED]、CHARGEBACK、REFUND）。

### 步骤 7 换版 —— 通过

| 请求 | 状态 | 关键出参 |
|---|---|---|
| `POST …/versions`（copyFrom v1，"11"） | 201 | versionNo 3（v2），DRAFT |
| `PUT …/prices`（CAPTURE_PCT 改 0.022） | 200 | "13" |
| submit（900001）→ approve（900002） | 200 → 200 | APPROVED |
| `POST …/schedule` effectiveFrom `20:54:23Z` | 200 | SCHEDULED；此时 v1 `effectiveTo` 已被设为 `20:54:23Z`，仍 ACTIVE |
| `POST …/activate`（到点后） | 200 | v2 ACTIVE，"17"；v1 → SUPERSEDED |

事件：收到 `CommercialTermVersionActivated`，`message_id=…:CommercialTermVersionActivated:17`，`versionId=238041841470136320`、`versionNo="3"`、`supersededVersionId=238041349469888512`。

命中交叉核对（USD，VISA/CREDIT/DOMESTIC）：

| 版本 @ 时点 | 状态 | 结果 |
|---|---|---|
| v2 @ now（20:54:36Z） | 200 | v2，CAPTURE_PCT `0.022` |
| v1 @ 20:52:55Z（v1 生效期内） | 200 | v1，`priceId=238041407628107788`，CAPTURE_PCT `0.025` |
| v1 @ now | 409 | `PRICE_AMBIGUOUS`：价格父链冲突，时点适用 false |
| v2 @ 20:52:55Z | 409 | `PRICE_AMBIGUOUS`：价格父链冲突，时点适用 false |

### 步骤 8 Checkout 解析 —— 通过（预期失败关闭）

`POST /checkout-context/v1/resolutions`，clientId 770000000001，USD，CARD，AE，en-AE：

| 调用方 | 状态 | 结果 |
|---|---|---|
| `X-Caller-Service: acquiring-service` | 503 | `OWNER_UNAVAILABLE`。日志：`No servers available for service: client-master-service`，同一 requestId `acbe392a-…` |
| `X-Caller-Service: operations-console-bff`（非法调用方） | 404 | `CLIENT_NOT_FOUND`（不泄露存在性） |

### 补充负向检查

| 请求 | 状态 | 结果 |
|---|---|---|
| 条款 submit 缺 `X-Gateway-Actor-Subject` | 400 | `INVALID_REQUEST`（MissingRequestHeaderException） |
| 画像 retire 缺 `X-Gateway-Actor-Subject` | 400 | `INVALID_REQUEST` |

### 步骤 9 清理 —— 完成

- commercial JVM（pid 66271）已正常停止，18108 无监听。
- 临时队列已删除（删除前已排空）。
- **`mor_commercial_lineb` 保留全部冒烟数据**（上表 §2 对象及其 BCR、`t_compensation_event` 记录），由主会话决定是否清理。
- 未运行任何 SQL，未触碰其他容器或进程。

## 4. 偏差、缺陷与观察

### 任务卡 / 环境问题（非业务代码缺陷）

- **E1 Snowflake Worker ID 越界。** 按要求设置 `COMMERCIAL_SNOWFLAKE_WORKER_ID=33` 后启动失败：`IllegalArgumentException: workerId must be between 0 and 31: 33`（日志首次启动段）。改用 30 后启动成功。`mor_commercial_lineb` 只有本进程写入，不存在 ID 冲突。建议任务卡与部署实例清单改为 0–31 内的值。
- **E2 任务卡数据缺口：APPLE_PAY 没有价格。** 步骤 3 开通了 APPLE_PAY（AED），步骤 4 却只给两个 CARD 画像定价，排期时激活校验 3 正确拒绝。为继续流程，补建 `SMK_APPLE_PAY` 画像，并新建复制版本加入 AED 价格。versionNo 1 停留在 APPROVED 无法回退，作为搁置版本留在库中；它不影响后续排期与激活。建议任务卡为每个开通项补齐价格，或说明需要先停用 APPLE_PAY。

### 观察项（不阻塞，供主会话裁决）

- **O1 版本与时点不匹配返回 409 `PRICE_AMBIGUOUS`。** 枚举说明为“歧义或父链冲突”，technique.md §2 沿用该错误码，属于合同内行为。但调用方用错时点时收到“歧义”语义较难诊断，可考虑后续细分或在文档中写明。
- **O2 client-master 不可用记为 ERROR“系统处理结果未知”。** 只读解析失败关闭是确定结果，日志级别与措辞可再评估。
- **O3 发布消息携带 Spring 内部头。** 头里有 `spring_listener_return_correlation` 与 `spring_returned_message_correlation`，前者在所有消息中取值相同。这是 `publisher-returns` 的副产物，不影响消费，可考虑在发布前剔除。
- 画像与合同创建返回 200，条款方案与版本创建返回 201，两条 B/C 线的创建状态码不一致，供统一时参考。

## 5. 结论

六层模型主链路已在真实进程中走通。画像、合同签署激活、订阅开通、条款 Maker-Checker、排期激活、价格命中、费率卡、换版与 Checkout 失败关闭均符合 technique.md 合同。两类领域事件都经补偿任务发布并到达 `commercial.events`。没有需要修改业务代码的缺陷。
