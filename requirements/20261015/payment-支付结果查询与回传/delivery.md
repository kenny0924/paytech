# 支付结果查询与回传交付记录

## 适用阶段（草案，待确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认 |
| 2+3. 现状差异与方案设计 | 已确认（第 1 版，Q-04 按方案“只更新投影并告警”） |
| 4. 研发实现 | 已确认 |
| 5. 综合验证与本地验收 | 已确认（本机 RabbitMQ 收发经两侧测试与跨系统 Hash 核对）|
| 6–7. test 发布准备与部署 | 不适用：Payment 未部署 mor-test |
| 8. 提交 | 另行确认 |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-15 | 选题 | 对“建议下一个先做 F-01 和 F-02”回复“可以” | 登记需求并交付阶段 1 评审稿 |
| 2026-10-15 | 1 | “继续” | 阶段 1 按建议确认（Q-01～Q-07，含本会话同时改 Payment 与 Commerce）；进入阶段 2+3 |
| 2026-10-15 | 2+3 | “继续，能多 agent 做就多 agent” | 方案第 1 版确认；本机 Payment 测试库补建 `t_compensation_event`；阶段 4 由两个子任务并行实现 Payment 与 Commerce |

## 当前阶段与下一确认点

全部阶段已确认；已本地提交，未推送、未部署。

## 阶段 4 记录

### 接口合同（先行，供并行实现）

- `PaymentIntentApi.getSubmission`（`GET /payment-execution/v1/intents/{intentId}/submissions/{sourceSubmissionId}?clientId=`）与 `GetSubmissionResponseBody`；本机 `install` Payment `paytech-payment-api` 供 Commerce 编译。

### SQL 执行记录

| 时间 | 目标 | 文件 | 结果 |
|---|---|---|---|
| 2026-10-15 | 本机 `mor_payment_channel_test`，root 账号 | [db/payment/ddl.sql](./db/payment/ddl.sql) | 补建 `t_compensation_event`（15 列，与 Payment Schema 一致）；测试 `init/ddl.sql` 同步；未触碰 mor-test |

### 实现（两个子任务并行，本会话合并检查）

| 系统 | 改动 |
|---|---|
| Payment | `GetSubmissionProcessor`（EX-I06）与 Controller/Converter；`PaymentActionRepository.findLatestByAttempt`、`ChannelTransactionRefRepository.findByAction`；execution 接入 `paytech-compensation-event`，`PayActionActor.doInTransaction` 在成功事务内登记 `PAYMENT_SUCCEEDED`；`PaymentSucceededHandler` 回查权威表重建事件；`PaymentResultPublisher` 端口与 `PaymentResultRabbitPublisher`（`topic_payment_result` / `payment.succeeded.v1`，Correlated Confirm + Mandatory）；RabbitMQ 与补偿配置、Nacos 模板、配置清单、README；测试清理补偿行并等待异步任务 |
| Commerce | `CheckoutCardPaymentPort.query` 增加 clientId 并改调 EX-I06（404 → `PAYMENT_SUBMISSION_NOT_FOUND`；查询结果 3DS 无地址时用 `validateQueried`）；Enforcer 只放行 `spring-boot-starter-amqp`、`spring-amqp`、`spring-rabbit`、`amqp-client`；`PaymentResultConsumerConfiguration`（Quorum 队列 `cg_commerce_payment_result`、DLX/DLQ、手动 Ack）、`PaymentSucceededListener`/`MessageConverter`、`ApplyPaymentSucceededProcessor`、`OrderPaymentService.applySucceeded`（一个事务推进投影、订单 PAID、会话 COMPLETED、可退基准、资金 PENDING，均为条件 SQL） |

### 验收映射

| AC | 验证 |
|---|---|
| AC-01 | Payment `GetSubmissionTest`（7）：成功、失败码、处理中、3DS 无地址、只有工具/跨 Client/跨 Intent 404 |
| AC-02 | Commerce `GetCustomerPaymentTest`（10）：状态映射、无地址 3DS、404 → `PAYMENT_SUBMISSION_NOT_FOUND`、不可用降级、协议不符 502、非公开格式失败码 → `CHANNEL_DECLINED` |
| AC-03 | Payment `PaymentSucceededPublishTest`（4）：成功发布一次、信封与 Payload、Hash、Confirm 后删除补偿行、重放不重复、失败与处理中不发布 |
| AC-04/05/06 | Commerce `PaymentSucceededListenerTest`（5）：五项事实、重复投递一次生效、Hash 篡改/未知订单/金额不符进 DLQ、已取消订单只更新投影 |
| AC-07 | Payment 发布测试核对消息不含卡号、工具 Token、consumerReference、IP、User-Agent、returnUrl |
| AC-08 | 两边测试均使用本机 RabbitMQ `/mor-local` |
| 跨系统 | 合并检查：Payment 测试真实发出的 2 条消息进入 Commerce 生产队列，按 Commerce 方式重算 Hash 一致（验证后已取出清空） |

### 开发检查

- Payment `./mvnw -o verify`：**127/127**（新增 11）。
- Commerce `./mvnw -o test`：**764/764**（新增 15）。

### 偏离与说明

- Q-04 按方案：已取消或过期订单只更新支付投影并记 WARN 后确认。
- Payment Handler 在“尝试成功、Intent 成功尝试指向它、最后动作为成功 PAY、恰好一条渠道交易引用”任一不满足时终态失败，不发布（渠道成功但缺交易号会停在人工处理）。
- Handler 直接读 Repository（同 Risk 先例，`JAVA_PROJECT_STRUCTURE` §5.6 建议经 Domain Service）。
- Publisher 放在 Payment web 的 `integration/mq`（出站适配位置）。
- Commerce 查询：Payment 不可用时仍按既有降级返回 200 PROCESSING；渠道失败码不符合公开格式时为 `CHANNEL_DECLINED`（合并时补充）。
- 投影的 `intent_source_version` 未更新（事件无 Intent 版本）；只支持全额捕获（金额必须等于订单总额）。
- Commerce 监听器常驻，运行时需要 RabbitMQ。
- 本机共享 Broker 上，Payment 测试发出的成功事件会进入 Commerce 的生产队列；Commerce 测试每例前清空该队列。
- 未写“成功事务回滚不留补偿行”测试（无不加生产故障点的回滚入口）。生产 XXL-Job 兜底扫描需各环境开启。

## 阶段 5：交付检查

- `npm --prefix requirements-site test`：137 项，127 通过、10 失败；失败数与本需求开始前基线一致，均为 Commerce 模型统计、Client API 路由、Gateway/Platform 台账、开发约定等既有断言，与本需求改动无关。
- Payment `verify` 127/127、Commerce `test` 764/764（阶段 4 结果，未再改代码）。
- 已本地提交（只含本需求文件；不含 `PayProcessor.java` 他人 import 顺序改动；共享索引与台账只暂存本需求行）。推送、部署未申请。
