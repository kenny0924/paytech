# Vault 托管 Payment 卡数据密钥决定

- Decision status: Confirmed
- Implementation status: Payment 侧实现见[当前台账](../CURRENT_IMPLEMENTATION.md)；Vault 代码不改
- Deployment status: Not deployed（各环境的数据类型与访问规则尚未注册）
- Decision date: 2026-10-15
- 用户决定：每张卡一把数据加密密钥（DEK），DEK 存 Vault；用户理由是把每张卡的密钥与其他敏感值统一在 Vault 维护。经讨论后用户确认“三项都同意”（方案、本例外、本机建表）。
- 详细合同：[Payment Card 加密存储技术方案](../../requirements/20261015/payment-Card加密存储/technique.md)第 2、4 节（第 3 版起卡按卡号唯一，DEK 为每个卡号一把）

## 1. 背景

[Vault 架构](../VAULT_SERVICE_ARCHITECTURE.md)规定 Vault 只托管非卡敏感值，并把“KMS 根密钥、KEK、HMAC Key 或明文 DEK”划给 KMS/HSM。Payment Card 改为真实加密存储时，用户要求每张卡的 DEK 由 Vault 托管，与上述规定冲突，需要例外。

## 2. 决定

- Vault 新增数据类型 `PAYMENT_CARD_DEK`：`REFERENCE` 模式、`dataClass=SECRET`，人工加密与人工解密都关闭。
- 访问规则只有两条：`payment-orchestration-service` + `PROTECT` + `CARD_STORAGE`（新卡号首次使用）；`payment-orchestration-service` + `REVEAL` + `CARD_USE`（已有卡号再次申请工具、读卡）。
- 值为 32 字节随机 DEK 的 Base64。Payment 库只保存 Vault Token；DEK 明文只在 Payment 处理请求的内存中出现。
- Vault 仍不接收卡号、CVV、有效期、持卡人等卡原值；卡密文只在 Payment 库 `t_payment_card`（卡号）与 `t_payment_card_credential`（有效期、持卡人）。
- Vault 代码不改。数据类型与访问规则按 [2026-10-14 策略入库决定](./2026-10-14-vault-policy-and-manual-access.md)在各环境注册。

## 3. 已知代价

- Vault 托管保护卡数据的密钥，按 PCI DSS 进入卡数据环境范围。
- Vault 不可用时，新卡申请与读卡失败（pay 链路原本已依赖 Vault 读取渠道账号配置）。
- Vault V1 的调用方识别（`X-Caller-Service`）可被内部调用方伪造，不是强认证。
- 两个请求并发首次使用同一卡号时，输掉竞争的一方已写入 Vault 的 DEK 没有卡引用；Vault 不提供删除，作为残留保留。单独一把 DEK 不能解出任何卡数据。卡在工具申请主事务之前保存，主事务失败不再产生无引用的 DEK。

## 4. 不变的边界

- CVV 不落任何持久介质；本需求只在 Redis 中以本卡 DEK 加密暂存，到期删除、领取一次即删除。
- 渠道账号配置例外（2026-09-21）、登录密码、根密钥及其他系统 Secret 的边界不变。
