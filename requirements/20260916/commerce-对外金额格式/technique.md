# 金额格式调整

本次调整一个公共 Money JSON Codec 和 Commerce 的金额合同说明，新增接口、表、字段与 SQL 均为 0。

| 范围 | 现状 | 本次处理 |
|---|---|---|
| Commerce Client / Customer | 已使用主单位字符串，USD 输出两位小数；部分能力使用自有 MoneyValue | 删除 MoneyValue，创建/查询/退款/历史快照直接复用公共 Money；接口字段结构、错误码和历史规范文本保持 |
| Payment Create Intent | String 金额直接解析为 Money，已经使用主单位 | 不修改业务代码 |
| Foundation Money | 金额精确，但序列化去除尾随零 | 按币种精度补零，独立金额字段与 JSON Codec 复用 toAmountString；有效精度不计补零，以保证最大金额往返；内部值相等与运算语义保留 |
| 公共规范 | 仍允许整数最小单位 | 收敛第一方 API 为主单位字符串，渠道协议仅在适配边界转换 |

不改变金额字段名或接口路径，不增加接口版本。Money 请求继续接受 `"10"`，响应改为 USD `"10.00"`；依赖响应原始字节的消费者应按新格式更新。业务持久化 Hash、签名原文与历史 Reader 不在本次重写范围。

验证使用 Common Core 完整模块测试、Commerce 金额与 API/OpenAPI 合同测试。具体执行结果及制品发布状态见[交付入口](delivery.md)。本次不涉及数据库迁移，不依赖前序 CreateCheckout 国家字段增量 SQL。

CommerceDecimalRules 是无状态的数量/十进制范围规则，只返回 BigDecimal 或执行校验，不保存币种、金额或包装 Money。商品行及汇总计算按[后续重构边界](../commerce-CreateCheckout业务重构/technique.md)收进内部 CreateCheckoutRequest，不保留独立 Calculator。公共 Money 的 InvalidParameterException 在既有请求校验边界映射为 Commerce 的 VALIDATION_ERROR；持久化金额损坏仍作为系统错误。历史快照使用 Money 的数值规范文本，不使用面向外部的补零方法。
