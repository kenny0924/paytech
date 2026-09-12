# Commerce 基础创建接入切片

本切片承接已确认的两段 Checkout 方案：新建订单只调用 CCT 基础预检，返回基础金额及 PREPARING 会话；支付准备另行接入。用户确认与当前状态分别见 [delivery.md](./delivery.md) 和 [当前状态台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。

## 实施边界

1. 直接消费 CCT 发布的 `CheckoutEligibilityApi`，通过 Spring `@ImportHttpServices` 自动注册接口、Nacos Discovery 和 Spring Cloud LoadBalancer 按 `client-contract-service` 选择实例；不保留 `commerce.cct.base-url` 或手工创建客户端。Foundation Group Configurer 提供严格 Problem 解码；不跨库查询，不提供生产默认通过。
2. 按用户 2026-09-12 的明确修正，未发布的 Commerce 直接替换旧实现：请求快照与回执无 `schemaVersion`，不保留旧创建恢复链、旧 Owner Mock 或 LEGACY_SCOPE Reader。幂等 Hash、Lease/CAS 与检查点保持业务用途。
3. 创建只提交 Order、Items、退款/资金初始投影和 PREPARING Session；不建立 Context/Legal/Payment Projection，不调用订单 Risk、Finance Tax 或 Payment。保留原 Client Order ID、Token、到期、Claim/Lease/CAS 与真实恢复检查点。
4. Create 返回 PENDING/ESTIMATED 与明确为空的税额、舍入及最终总额。GET 按实际准备状态投影；创建成功重放保持原回执，不重新预检或改成后来准备结果。
5. 以原本地旧结构为迁移基线，使用仅增列、放宽新流程所需可空约束的人工增量 SQL，保留历史列；没有本次明确 SQL 授权前不执行。完整目标 Schema 仍只有 PDManer 生成的一份，不把迁移期兼容列加入新写目标。
6. 测试从真实 Commerce HTTP 入口经过本地 MySQL 和 CCT HTTP Simulator，验证失败关闭、无候选、超时、协议异常、幂等和数据边界。Simulator 只证明协议适配；真实 CCT 联调另行记录。

## 数据库动作范围

目标限 `mor-local-mysql-1 / 127.0.0.1:13306/mor_commerce`。用户于 2026-09-12 明确回复“执行”，已授权下列本地验收方案；实际执行与验证结果只在当前状态台账 COM-PRECHECK-01 维护。Risk、CCT 数据库、test 环境、公共入口、部署及提交不在本切片动作范围。

## 实现与验收映射

| 已确认约束 | 代码入口 | 开发/后续综合验证 |
|---|---|---|
| 创建只做 CCT 基础预检 | CctPrecheckConfiguration、CctPrecheckClientImpl、CreateCheckoutProcessor | HTTP Wire Simulator + 真实 LoadBalancer；多实例、移除/空列表、禁重试及入口错误/恢复场景 |
| 五类基础事实与原恢复点 | CheckoutCreationService、CheckoutRepository、BasicCheckoutMapper | 原始 ID、Items、币种和金额一致性；ORDER_COMMITTED 缺事实拒绝；Token 冲突全事务回滚 |
| 基础/最终金额区分 | CheckoutCreationReceipt、CheckoutWireConverter、Client API/OpenAPI | nullable 组、PENDING/ESTIMATED/PREPARING；GET 与创建回执分别投影 |
| 永久 Client Order ID / 幂等 | 原 Command Idempotency 机制，无版本快照 | 负面预检稳定 422；503 保留 CLAIMED；重复请求不重做预检；不能另建替代单 |
| 未发布代码直接替换 | 单一 Codec/Processor/创建 Service；删除旧 Owner 端口 | 无版本请求/回执；非法快照即使 Hash 一致也拒绝；真实创建与恢复入口回归 |
| 人工 SQL / 不影响历史 | 本目录 db/commerce/ddl.sql | 迁移前后结构及旧字段/JSON/Hash/金额核对，不执行全量 CREATE |

CCT 预检明确 `eligible=false` 或 Client 不存在映射稳定 `422 CLIENT_CONFIGURATION_NOT_READY`；配置变化保留可重试并发结果，
配置歧义返回依赖不可用；技术不可用/超时和畸形协议分别保持 503/504/502，不改成无候选，也不永久封死命令。
成功响应的未知字段、重复字段、错误类型、无效日期、资格与 reasonCode 不一致均拒绝。预检不会冻结候选或预占其配置。

本轮不增加公开 Locale 输入；当前创建请求已有 country/currency/items/expiry 保持。后续支付准备按确认的法律 Locale 合同实现。
支付准备尚未写入最终字段，因此新创建只能完成到 PREPARING；生成 URL 不代表 Consumer 页面已可访问。

## 前一批本地迁移验收方案（2026-09-12 已授权，保留执行依据）

以下保留原已执行 SQL 的评审依据；其中旧创建恢复及版本兼容要求已被上面的未发布代码直接替换决定取代。
本次修正只调整 Commerce 代码、合同和验证，不重跑此脚本或物理删除既有列。

1. 目标仅 `mor-local-mysql-1`、`127.0.0.1:13306/mor_commerce`，再次核对容器、库身份及原字段；在用户目录受限位置保存全库备份及 schema/行数摘要，验证备份可读取。
2. 停止该库的并发测试/业务写入后人工应用 [ddl.sql](./db/commerce/ddl.sql)，出错即停。脚本不是幂等迁移，不能带 `--force` 或盲目重跑部分成功的 ALTER。
3. 核验新增列、可空、默认值和注释；旧字段值、旧金额、旧 JSON/Hash 不变。原订单只增加 `context_format=LEGACY_SCOPE` 标记，原身份不做字符串转换。
4. 执行 Commerce 完整 `./mvnw -q verify`，包含真实本地 MySQL、随机端口 HTTP、CCT HTTP Simulator、新创建/旧恢复以及原覆盖率门禁；修复同范围实现缺陷后重验，精确清理本次测试自有记录。
5. 保存日志、测试报告、制品摘要与清理结果。若结构执行失败，先识别哪些 ALTER 已成功并停止写入；恢复必须核对备份后是否有新增业务数据，不自动删库或丢弃新数据。应用回退不能替代数据库数据恢复。

该动作不启动/替换常驻服务，不写 CCT、Risk 或 test 数据库，不开放公共路由。全量目标 Schema 与这个保留旧列的过渡结构不同，
后续支付准备迁移应继续在本需求维护增量脚本，不把当前桥接结构反写为新的 PDManer 目标。

## 本轮交付节点

本地验收按上述范围完成，结果及备份证据见 [COM-PRECHECK-01](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-precheck-20260911)。
支付准备、真实 CCT 跨服务业务联调和发布仍为后续工作，不由本轮“执行”自动授权。

## 2026-09-12 服务发现接法修正

用户明确指出固定 `commerce.cct.base-url` 与手工客户端未使用项目中间件。按既有技术栈修正 Commerce 出站，
CCT Web 补充同名 Nacos 注册，Foundation 现有普通 HTTP JAR 增加 Spring Group Configurer 接口。
不增加中间件模块、不引入 Feign，不修改业务 API、OE 决策、定价、Risk 或数据库结构。
自动测试通过 SimpleDiscovery 提供实例列表，验证真实负载均衡；不连接 Nacos、不注册共享服务，不等同真实 Nacos 联调或部署。

## 2026-09-12 公共 HTTP 职责收敛

用户指出 CCT 配置重复处理 MDC 且代码过于复杂。本次同范围修正将 Request ID 传播放入 Foundation HTTP
公共出站拦截器，与入站 Filter 共用 Request ID Key；不复制完整 MDC、不缓存线程上下文。
删除 CCT 手写 JSON Reader，严格 DTO 绑定使用标准 Jackson，响应字节上限由公共 HTTP 选项表达。
CCT 配置只声明接口、错误转换和响应策略，应用级禁重试配置统一维护；日期、资格和原因一致性仍由 CCT 业务适配器处理。
删除仅被 CCT 使用的 `AbstractClientTemplate`，避免在 Foundation 之外重复包装公共调用与日志。
现有成功/错误合同、服务发现和超时保持，未修改 API、数据库、CCT/Risk 业务或部署配置；验证状态以当前台账为准。
