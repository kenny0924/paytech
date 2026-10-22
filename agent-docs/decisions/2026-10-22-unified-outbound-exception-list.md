# 唯一出口例外清单（与架构门禁 R15 白名单逐项对应）

状态：随[统一对外渠道出口第二段](../../requirements/20261020/channel-connector-统一对外渠道出口/technique.md)（第 1.1、6.6 节）登记，2026-10-22。上位决定见 [2026-10-20 统一对外出口](./2026-10-20-channel-connector-unified-outbound.md) 决定 1。实施、验证与部署状态只记录在 [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md)。

## 规则

面向外部第三方的业务调用统一由 channel-connector 发出。代码层门禁为 `paytech/tools/arch-rules` 的 **R15**（`ExternalEgressRule`）：

- **规则一（外部 SDK）**：AWS SDK（`software.amazon.awssdk`）、MinIO（`io.minio`）、OkHttp（`com.squareup.okhttp3`）、Apache HttpClient 5（`org.apache.httpcomponents.client5` / `core5`）只允许出现在下表登记的模块。依赖图部分检查各模块 POM 的非 test 依赖（Reactor 级 `ReactorArchitectureAutoTest`），类部分检查各服务生产类（`target/classes`）对相应包的引用（各服务 `ArchitectureAutoTest`）。`-test` 模块不进入制品，不检查；传递依赖不计入依赖图部分，实际引用由类部分拦截。
- **规则二（JDK HttpClient 直接发送）**：直接调用 `java.net.http.HttpClient#send` / `sendAsync` 只允许出现在 `paytech-channel-connector-protocol` 的 `com.paytech.channel.connector.protocol.ext.outbound` 包（`ChannelHttpClient`）。经 `JdkClientHttpRequestFactory`、`HttpServiceClientFactory` 构造的内部 RestClient 由框架类发送，不受影响；只检查生产类，测试代码不在范围内。

**新增例外必须同时修改两处**：本清单与 `ExternalEgressRule.SDK_ALLOWLIST`（规则二的唯一位置为 `OUTBOUND_ARTIFACT` / `OUTBOUND_PACKAGE`）。门禁只能拦截常见写法，不能替代代码评审与网络层出口强制（网络层另立需求，需求 Q6）。

## 对外出口例外

| 编号 | 模块（R15 白名单） | 允许的 SDK | 外部目标 | 为何不经 channel-connector | 自身安全控制 |
|---|---|---|---|---|---|
| O3 | `paytech-platform-support-delivery` | AWS SDK（`sesv2`、`sqs`、`url-connection-client`） | AWS SES v2 发信、SQS 投递事件队列 | Platform 通知的自有能力，不是第三方业务渠道；发送账号与开关已有专项配置 | 发送账号凭据经 Vault（`NOTIFICATION_DELIVERY`）读取；`notification.*` 开关；SDK 只用 `url-connection-client`，排除 apache / netty 客户端 |
| O4 | `paytech-platform-support-delivery` | Apache HttpClient 5（`httpclient5`，含 `core5`） | Client 自己填写的 Webhook 地址 | 目标是 Client 自有地址，不是平台对接的第三方渠道 | `WebhookAddressGuard`：DNS 结果校验后由 HttpClient 只连接已校验地址，拦截私网、回环、链路本地、保留与组播地址，部署 `trusted-networks` 放行；签名密钥经 Vault（`WEBHOOK_SIGNING`）；不跟随重定向 |
| O5 | `paytech-platform-support-file-document` | AWS SDK（`s3`、`url-connection-client`）、MinIO（`minio`）、OkHttp（MinIO 传输，`MinioStorageConfiguration` 定制） | AWS S3 / 自建 MinIO 对象存储 | 对象存储基础设施，浏览器经预签名 URL 直传，不是业务渠道调用 | 配置或 AWS 默认凭据链；预签名 URL 有效期与上传校验由文件模块控制 |

## 非出口的内部传输（R15 白名单，仅 Apache HttpClient 5）

| 编号 | 模块（R15 白名单） | 允许的 SDK | 用途 | 证明是内部调用 |
|---|---|---|---|---|
| I1 | `paytech-acquiring-app` | Apache HttpClient 5 | Spring Boot RestClient 传输（`spring.http.clients.imperative.factory: http-components`），`HttpClientConfiguration` 只关闭自动重试 | `spring.http.serviceclient.*.base-url` 全部为 `lb://` 内部服务（payment-service、card-service 等），经服务发现与私网调用，不访问外部第三方 |
| I2 | `paytech-counterparty-app` | Apache HttpClient 5 | 同 I1 | 同 I1 |

I1、I2 若改为访问外部第三方，不能沿用本条目，须改经 channel-connector 或按对外出口例外重新登记。

## 唯一直接发送位置（规则二）

| 模块 | 包 | 类 | 说明 |
|---|---|---|---|
| `paytech-channel-connector-protocol` | `com.paytech.channel.connector.protocol.ext.outbound` | `ChannelHttpClient` | 支付渠道与服务渠道的原子 API 出站请求；两类渠道分执行舱、出站地址防护与访问日志见技术方案第 6.3–6.5 节 |

## R15 盘点（2026-10-22，`paytech/**` 当前源码）

| 位置 | 命中 | 处理 |
|---|---|---|
| `platform-support/paytech-platform-support-delivery` POM 与生产类（`integration/provider`、`integration/webhook`） | AWS SDK、Apache HttpClient 5 | 白名单 O3、O4 |
| `platform-support/paytech-platform-support-file-document` POM 与生产类（`integration/storage/s3`、`integration/storage/minio`） | AWS SDK、MinIO、OkHttp（`MinioStorageConfiguration` 引用 `okhttp3.OkHttpClient`） | 白名单 O5 |
| `acquiring/paytech-acquiring-app` POM、`configuration/HttpClientConfiguration` | Apache HttpClient 5 | 白名单 I1（内部 RestClient 传输） |
| `counterparty/paytech-counterparty-app` POM、`configuration/HttpClientConfiguration` | Apache HttpClient 5 | 白名单 I2（内部 RestClient 传输） |
| `build/paytech-dependencies` | `dependencyManagement` 中的 MinIO 版本与 AWS SDK BOM | 不在范围：版本管理不引入依赖 |
| `vault/paytech-vault-test` | Enforcer `bannedDependencies` 文本中的 `software.amazon.awssdk:kms` | 不在范围：禁止规则文本，不是依赖 |
| `platform-support/paytech-platform-support-test` `StorageObjectMocker` | MinIO / AWS SDK 引用 | 不在范围：`-test` 模块，只连本机测试对象存储 |
| `channel-connector/paytech-channel-connector-protocol` `ext/outbound/ChannelHttpClient` | `HttpClient#send` | 规则二唯一允许位置 |
| client-master、payment、commercial、platform-support-app、vault-client、channel-connector-app、operations-console-bff、client-console-bff 的 `*Configuration` | 构造 `java.net.http.HttpClient` 交给 `JdkClientHttpRequestFactory` / RestClient | 不命中：只构造传输，不直接调用 send；目标为内部服务 |
| foundation（test-support、mvc-web-starter、messaging、http-client-support）、各服务 `-test` 模块共约 50 个测试类 | `HttpClient#send` / `sendAsync` | 不在范围：测试代码（`target/test-classes`）调用本机被测服务或本机 Broker，不进入制品 |

Gateway JWKS、Vault（本地 AES，未直接使用 KMS）、Nacos、MySQL、Redis、RabbitMQ、XXL-Job 均只访问内部，不在出口范围内（技术方案第 1.1 节）。
