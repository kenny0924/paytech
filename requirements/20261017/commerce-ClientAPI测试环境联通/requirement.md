# Client API 在 mor-test 联通 — 需求（阶段 1 评审稿）

> 状态：阶段 1 已确认（2026-10-17，“可以”，Q1—Q5 按建议）。确认记录见 [delivery.md](./delivery.md)。

## 1. 来源与背景

- 来源：用户 2026-10-17 在 Commerce Webhook 业务事件收尾后，选择“Client API 在 mor-test 跑通全链路”。
- 目标：在 test 上走通客户接入的完整路径——在 Client Console 拿到 API 凭证，用凭证经 Gateway 调 Client API 建单、查单、取消、退款，在自己的端点收到签名 Webhook 事件。

## 2. 现状（2026-10-17 只读核对）

| 项 | 现状 |
|---|---|
| test 已运行 | Gateway、Nginx、Platform、CCT、Risk、Vault、Client/Operations BFF 与 Web、MySQL、Redis、RabbitMQ、Nacos、MinIO、Mailpit |
| test 未运行 | **Commerce、Payment、Finance**、XXL-Job（`compose.scheduler.yaml` 已有 `scheduler` profile，未启动） |
| 数据库 | `mor_commerce`、`mor_payment` 库已存在，表结构是否齐全待阶段 2 核对 |
| Commerce 外部依赖 | 真实：Risk 结账评估、CCT 基础预检、Platform Webhook 受理、Payment Intent 与存卡接口。**生产代码中的固定 Mock**（`CustomerCheckoutIntegrationConfiguration`）：CCT 商业事实（固定 `AE001` 等）、税、合同文档、Consumer 保护（Vault）、支付状态查询；`FixedPaymentTerminationClient`（取消时的支付终止）；`UnavailablePaymentRefundClient`（退款提交总失败） |
| Client API 操作 | Commerce 共 15 个：建单 1、订单 3（查、列表、取消）、退款 5、Customer 4、存卡 2 |
| Gateway | CLIENT_API 通道已实现（专用域名推导通道、PLT-I03 凭证校验、精确 Scope、最小下游 Header），test 上没有任何 CLIENT_API Target/Route |
| Nginx（test） | 只有控制台入口 `8080/8443 localhost`，没有 Client API 专用入口 |
| Webhook 投递 | test Platform 只允许 https、不放行内网地址；客户自助建端点会解析主机名 |

## 3. 使用者与目标

| 使用者 | 目标 |
|---|---|
| Client（接入方，用验收账号模拟） | 用自助凭证调 Client API，并收到对应 Webhook |
| 研发 / 验收 | test 上有可重复跑的接入全链路脚本，补验本机做不到的“Platform 恢复后补交” |

## 4. 本期范围（建议）

| 编号 | 功能 |
|---|---|
| F-01 | Commerce 部署 mor-test：镜像、Compose 服务、`mor_commerce` 表结构（按 Schema SQL 手工执行）、Nacos/RabbitMQ 配置，接入 Risk、CCT、Platform |
| F-02 | Client API 入口：test Nginx 新增 Client API 专用入口；Platform 登记 CLIENT_API Target 与业务路由（Scope 绑定），Gateway Snapshot 生效 |
| F-03 | 路由范围：~~13 个~~ 按阶段 2 D1 改为设计冻结的 10 条中 Commerce 已实现的 **9 条**（建单、订单 3 条、退款 5 条；结账查询未实现，阶段 3 发现）；Customer 4 个另立需求，存卡 2 个依赖 Payment 不开放 |
| F-04 | XXL-Job：test 启动 `scheduler` profile，登记 Commerce 执行器与补偿扫描任务（`compensationEventScan`） |
| F-05 | Webhook 接收端：test 内提供一个只用于验收的 HTTPS 接收端（见 Q4） |
| F-06 | 验收脚本与发布记录：凭证 → Client API → Webhook 全链路，`E2E_TARGET=test` 可重跑 |

## 5. 本期不做

- Payment、Finance 部署 test；Hosted Checkout 消费者付款页面（`checkout-web`）。
- 把 Commerce 中 CCT 商业事实、税、文档、Vault 的固定 Mock 换成真实 Owner 接入（另立需求，见 Q3）。
- 退款结果终态（依赖 Payment PAY-I04）。
- 生产或其他环境。

## 6. 验收标准

| 编号 | 场景 | 期望 |
|---|---|---|
| AC-01 | 用 Client Console 自助创建的 `ck-` 凭证经 Client API 入口建单 | 201，返回订单号；无凭证、错凭证、Scope 不符分别被 Gateway 拒绝 |
| AC-02 | 查单、按 clientOrderId 列表 | 与建单结果一致；他人订单不可见 |
| AC-03 | 取消订单 | 200；端点收到签名有效的 `order.cancelled` |
| AC-04 | 对已支付订单建退款 | 201；端点收到 `refund.created`（已支付订单的来源见 Q1） |
| AC-05 | Platform 暂不可用时取消订单，恢复后 | 业务成功；XXL-Job 扫描补交，端点收到一次，内容与冻结时一致 |
| AC-06 | 日志 | Gateway、Commerce、Platform 日志无完整凭证、`whsec_` 密钥、Webhook 载荷 |
| AC-07 | 回退 | 有可执行的回退步骤（停路由、停 Commerce），不影响现有控制台 |

## 7. 已确认的决定

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | test 没有 Payment，订单无法真实付款。`order.paid` 与“对已支付订单退款”怎么验？ | 与本机一致：向 test RabbitMQ 发布一条合规 `PaymentSucceeded` 作为**标注过的验收夹具**（并补一行支付投影）；不部署 Payment |
| Q2 | 开放哪些操作？ | 13 个（不含存卡 2 个）；Customer 删除若依赖 Payment 存卡清理，阶段 2 核对后再定 |
| Q3 | Commerce 生产代码里的固定 Mock（CCT 商业事实 `AE001`、税为 0、合同文档、Vault）随镜像上 test，可以接受吗？ | 接受，作为 test 联通的已知限制写进发布记录；替换为真实接入另立需求 |
| Q4 | Webhook 接收端放哪？test Platform 只允许 https 且不放行内网 | 在 mor-test 内起一个验收用 HTTPS 接收端容器（自签证书），test Platform 仅为该容器地址放行内网与信任其证书；不向外网推送。替代方案：用外部公开服务（数据会出外网），不建议 |
| Q5 | 会碰到其他会话的东西：Compose、Nginx、Gateway Snapshot、Platform 配置、XXL-Job | 每次替换或改配置前按惯例通知并取得相关会话同意；Commerce 目录内只改部署所需文件 |

## 8. 适用阶段

1 需求评审 → 2 现状差异与影响（核对 `mor_commerce` 表、Gateway 幂等与 Commerce 命令幂等的现状、Client API 域名推导、Customer/存卡依赖）→ 3 部署与路由方案 → 4 研发（部署文件、接收端、脚本；产品代码原则上不改）→ 6 test 发布（SQL、部署、路由，需单独授权）→ 7 test 验收 → 8 收尾。阶段 5 本机验证可并入阶段 4（本机已验过业务链路）。
