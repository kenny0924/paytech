# 卡数据经 Platform 渠道执行决定

- Decision status: Confirmed
- Implementation status: Platform 部分已实现并本机验证（2026-10-14）；Payment 读卡接口与 Card 受控领取未实现
- Deployment status: Not deployed
- Decision date: 2026-10-14
- 用户决定：卡支付走渠道服务端卡 API（PayerMax Direct API - CARD），不使用渠道收银台；PCI DSS 认证一定会取得。卡在服务间传递使用卡 ID / Token，Platform 可以读取卡数据，但不能打印日志，只能在内存中存在。
- 详细合同：[PayerMax 卡直付对接技术方案](../../requirements/20261014/payment-PayerMax卡直付对接/technique.md)
- 实施与部署状态：[当前台账](../CURRENT_IMPLEMENTATION.md#payment-payermax-card-direct-20261014)

## 1. 背景

[Platform 执行决定](./2026-09-21-platform-integration-http-execution.md)让渠道协议统一由 Platform 执行；[Card 需求](../../requirements/20260912/payment-自建CardVault/requirement.md) CARD-01 与 [Platform 内部接口](../../requirements/20260914/platform-support-外部API配置与执行/internal-api.md)第 2 节禁止原始卡数据进入 Platform。卡直付渠道（如 PayerMax）对含卡号的完整请求体签名，签名方必须看得到卡号，出站代理替换卡号的做法不可行。两条规则不能同时成立，需要决定。

## 2. 决定

- **服务间只传 Token：** Payment 调用 Platform 标准 API 时，`input` 只带支付工具 Token（`card.token`），不带卡值。
- **Platform 凭 Token 读卡：** 流程初始化函数 `PAYMENT_CARD` 以 Caller `platform-support-service`、用途 `CHANNEL_EXECUTION` 调用 Payment 私网读卡接口 `POST /payment-card/v1/channel-card-materials/reveal`（合同 `ChannelCardMaterialApi`），带 Token 与支付尝试 ID。
- **卡值只在内存：** 读到的卡值包装为卡值句柄，只能写入原子 API 声明的卡字段，在发送字节中展开；不缓存、不落库；Platform 日志、执行轨迹、沙箱验证证据、标准 API 响应、Problem 与脚本异常都不含卡值（证据中卡号只保留前 6 后 4）。
- **Payment 读卡接口：** 响应含卡原值，该路径前缀配置为 MVC 日志只记元数据；Card 判断 Token 归属、当前是否允许用卡，CVV 只能领取一次。
- **PCI 范围：** Platform 执行进程及其网络段纳入卡数据环境。上生产以 PCI DSS 认证与渠道开通为前提；开发与验证只用渠道公布的测试卡。

## 3. 修订与不变的边界

- 修订 [Card 需求](../../requirements/20260912/payment-自建CardVault/requirement.md) CARD-01：卡原值可经 Card 读卡接口进入 Platform 渠道执行内存；其余“不进入 Commerce、其他 Payment 通用命令、非卡 Vault、日志与事件”的约束不变。
- 修订 [Platform 内部接口](../../requirements/20260914/platform-support-外部API配置与执行/internal-api.md)第 2 节：`input` 仍不得含卡原值，卡只能以 Token 进入。
- CVV 不落任何持久介质的规则不变；恢复与查询不读取 CVV。
- 沙箱验证账号可录入渠道公布的测试卡（凭据 `testCard.*`，按 [沙箱验证决定](./2026-10-14-platform-integration-sandbox-verification.md)明文存于 Platform 库），不得录入真实卡。
