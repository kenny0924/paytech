# 公共 Public Token

`com.paytech.foundation.token.PublicTokenGenerator` 是无 Spring、无业务状态的静态工具。

```java
String body = PublicTokenGenerator.generate("US", Long.toString(clientId));
String checkoutToken = "chk_" + body;
boolean validBody = PublicTokenGenerator.isValid(body);
```

`generate(String... mixingValues)` 只接受可变混入值，也允许不传参数。前缀由调用方拼接；生成时间由内部
`Instant.now()` 取得当前系统 UTC 秒，不接收订单创建时间、Clock 或随机源。时间仅用于大致排序，不保证跨机器
严格单调，也不代替业务创建时间、有效期或数据库唯一约束。相同参数每次仍生成新的随机值。

## 主体格式

- 返回 30 位、大小写敏感的 Base62：27 位 KSUID Core + 3 位 CRC16；不含任何业务前缀。
- Core 为 20 字节：4 字节大端秒数（Unix 秒减 `1_400_000_000`）和 16 字节新的 `SecureRandom` Payload。
- Base62 字符表固定为 `0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz`，编码左侧补 `0`。
- CRC-16/CCITT-FALSE 对上述 20 字节计算，`poly=0x1021`、`init=0xFFFF`、不反射、`xorout=0`；
  无符号结果编码成 3 位 Base62。

有混入值时，将参数数量和各参数的 UTF-8 字节长度分别编码为 4 字节大端整数，按调用顺序送入 SHA-256，
其前 16 字节与随机 Payload 逐字节 XOR。字符串不 trim、不改大小写、不做 Unicode 归一化；数量和长度边界避免拼接歧义。
最多 16 项，各项非 null，`String.length()` 不超过 1024，必须为合法 Unicode；空字符串合法。null 数组、
非法文本或超限输入抛出 `InvalidParameterException`；系统时间超出 32 位无符号偏移范围时抛出 `ErrorException`。

混入值不是密钥，不增加随机熵，不建立可解码身份绑定。不可猜测性来自 128 位 CSPRNG Payload；
CRC 只作输入错误检查，不作为签名或认证。调用方仍负责存在性、归属、状态、到期时间及唯一冲突处理。

## 校验与兼容

`isValid(String body)` 只检查无前缀主体的固定长度、Base62 范围和 CRC，非法输入返回 false。
它不需要混入值，不检查签发方或业务有效期。业务层负责去除并验证自己的前缀。

新旧 Payload 混入方法不影响校验；历史 Checkout 主体 `3Iu73V3AfDlneKkX3g6YoNctEljGRT` 继续有效。
已持久化 Token 直接使用，不重新生成，也不根据当前参数重建。

## 验证入口

JDK 25 下在 Foundation 根目录执行 `./mvnw -o -pl paytech-common-core -am clean verify`。
测试覆盖当前时间、无前缀格式、旧向量、输入边界、校验失败和并发生成；随机样本测试不代表数学上的绝对唯一。
