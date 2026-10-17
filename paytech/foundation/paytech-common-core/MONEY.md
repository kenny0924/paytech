# Money

`com.paytech.foundation.money.Money` 是 Common Core 内的不可变值对象，封装币种与精确金额。随 `paytech-common-core` 提供 Jackson 3 Codec，不引入 Spring、数据库或业务状态，也不提供独立 Money Artifact。

```java
Money fee = Money.of("USD", "2.35");
Money exact = Money.of("USD", new BigDecimal("100.12"));
Money zero = Money.zero("USD");
Money remaining = exact.subtract(fee);
String amountText = fee.toAmountString(); // "2.35"，适用于币种位于外层的独立金额字段
```

## 校验与计算

- 无公开构造器、setter 或 float/double 工厂。BigDecimal 调用方也不得从浮点数构造金额。
- 币种严格为 JDK Currency 识别的三个大写字母；拒绝空白、未知、测试、无币种以及非普通货币代码。不自动转大写；不代表该币种已在业务中启用。
- 文本金额只接受普通十进制，例如 `0`、`-1.25`；不接受指数、加号、前导零、空白或千位分隔符。BigDecimal 可来自精确指数表示，但输出始终使用普通十进制。
- 技术上限为有效精度 38、输入小数位 18、整数位 38；文本最多 58 字符。先限制输入的 scale 与整数位，再校验去除尾随零后的有效精度；币种补零不会缩小可表示金额范围，极端 scale（包括零）仍被拒绝。
- 金额的有效小数位不得超过 JDK `Currency#getDefaultFractionDigits()`：USD/AED 最多 2 位、JPY 为 0 位、KWD 为 3 位。不会自动舍入；超精度直接拒绝。纯尾随零不增加有效小数位，例如 USD `1.2300` 合法并规范化为 `1.23`。这仍不代表该币种已被具体业务启用，业务模块继续负责金额上下限等专项规则。
- 尾随零与负零规范化；`1.0`、`1.00` 相等且 hashCode 一致。金额允许负数；业务自行限制正数或非负数。
- add/subtract/compareTo 只接受非空、相同币种；溢出拒绝，不截断、不舍入。输入和运算结果均采用相同精度边界。
- 提供 negate/abs/isZero/isPositive/isNegative；没有隐式汇率转换、乘除法、费用计算或默认 rounding mode。
- `ofMinorUnits` 只供需要最小单位的第三方渠道协议适配使用，不用于第一方 HTTP 金额解析。minorUnitScale 必须显式指定、处于 0–18，且不得构造超过该币种默认小数位的非零精度；避免把所有货币机械当作两位小数。
- 参数或运算校验失败抛出 `InvalidParameterException`，错误文本不拼接原始输入。

## JSON 契约

```json
{"currency":"USD","amount":"10.00"}
```

金额始终为**币种主单位字符串**，不使用最小单位整数：USD `"10.00"` 表示 10 美元，`"1000"` 表示 1000 美元。请求可省略尾随零，响应按币种补齐小数位：USD `"10.00"`、零 `"0.00"`、JPY `"10"`、KWD `"10.000"`。不乘除 100、不隐式舍入；Java 内部的尾随零规范化、比较及 hashCode 语义保持不变。

独立金额字段调用 `toAmountString()`；Money JSON Codec 复用同一方法。调用方继续负责金额正负限制、业务数据库范围和商品数量等规则，不再包装第二个金额值对象。使用此方法不要求把既有 String 字段改为嵌套 Money JSON 对象。

类级 Jackson 3 Codec 在默认启用注解的 JsonMapper 上生效，无需全局注册或改写所有 BigDecimal 的行为。流式读取严格拒绝重复键（包括转义同名键）、未知字段、缺失字段、null、非字符串字段、数值金额和非对象输入。容器对象仍必须验证其必填 Money 属性：一个完全缺失的外层属性不是 Money 自身能够全面约束的输入。

宿主不得关闭这些注解或覆盖 Codec；宿主仍负责文档/HTTP Body 大小限制、尾随文档处理、DTO 必填校验、权限与业务规则。此模块测试覆盖默认 JsonMapper，不声称已经完成所有 HTTP/MQ/Redis 宿主集成。

## 验证与发布

```sh
./mvnw -o -pl paytech-common-core -am clean verify
```

当前包含 Money 的 Common Core 增量仅供本地验证，未发布 Nexus。消费服务远端构建前需经授权发布新的 Common Core Snapshot。
