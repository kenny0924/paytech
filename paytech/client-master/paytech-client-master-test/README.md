# CCT Checkout 解析测试

当前只保留 [`CheckoutContextApi#resolve`](../client-contract-api/src/main/java/com/paytech/client/contract/api/checkout/CheckoutContextApi.java) 的真实 HTTP 测试。测试遵循工作区[测试规范](../../agent-docs/UNIT_TEST_GUIDE.md)，通过正式应用、Controller、Processor、Service 和 Repository 访问本地 MySQL。

## 场景和辅助

源码位于 `src/test/java/com/paytech/client/contract`：

| 路径 | 内容 |
| --- | --- |
| `auto/ext/http/checkout/ResolveCheckoutHttpTest` | 13 项商业事实、拒绝、配置变化及只读场景 |
| `auto/ext/http/checkout/ResolveCheckoutValidationTest` | 3 项 JSON、调用范围和参数校验场景 |
| `test/base/checkout` | 正式应用装配、固定时点、入口请求与调用器 |
| `test/mocker/local/checkout` | 本例数据准备、只读检查及后置清理 |
| `test/dal/checkout` | 场景所需的本地事实差异 |
| `test/checker/checkout` | 公共 Problem 和商业信息泄露断言 |

全局 BeforeEach 检查前例清理结果，业务场景的类级 BeforeEach 准备自身数据；Foundation `TestBase` 在 AfterEach 聚合清理本例成功插入的行。已有同键数据直接失败，不在初始化阶段删除。校验场景不插入业务数据。

Risk 仍使用正式不可用端口，测试验证相应 503 拒绝，不注入批准替身；因此这些测试不证明成功解析的多 Owner 闭环。

## 执行

在 CCT 仓库根目录运行，使用[本地环境规范](../../agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md)规定的 Java 和仓库本地 MySQL。通过 `CCT_TEST_DB_PASSWORD` 安全注入密码；测试不读取 `.env.local`，不创建 Schema，不需要入驻 Redis 或外部 Simulator。

```sh
# 构建并执行当前全部 16 项解析测试
./mvnw clean verify

# 单独执行 HTTP 输入校验
./mvnw -Dtest=ResolveCheckoutValidationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

2026-09-21 按用户要求删除其他入口、独立规则和启动测试，包括旧 precheck 地址检查，并清理其专用辅助和配置。此前 158 项集合的执行记录属于历史证据；当前测试通过仅证明上表保留的 16 项场景。执行记录见[测试整理与范围收缩记录](../docs/requirements/20260921-test-organization/verification.md)。
