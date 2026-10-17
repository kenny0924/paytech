# CCT 自动测试整理验证记录

本文前半部分保留首次整理的执行证据；随后按用户要求仅保留 `CheckoutContextApi#resolve`，见文末“同日后续：仅保留 resolve 测试”。

执行日期：2026-09-21。范围为 CCT 测试源码和测试入口说明；未修改生产代码、构建依赖、数据库结构或其他系统。

## 整理内容

- `auto` 根目录的 15 个混合测试类按入口和能力整理为 20 个场景类，使用 `*Test` 后缀。
- 入驻 HTTP 大类拆为起始查询、草稿、人员关系、材料、文件目标授权五类；原有 59 个方法及参数组合保留。
- Checkout 分开 HTTP 业务场景、HTTP 输入校验、内部解析、领域规则及未接入 Risk 时的资格拒绝行为，统一调用器和响应 Checker。
- 入驻 HTTP、投影 Handler、补偿持久化的公共装配与资源清理移入 `test/base`；SQL 差异移入测试侧 Mapper，`auto` 不再承载整套环境初始化或清理实现。
- 入驻类共享上下文的外部监听器由 Spring 关闭，每例等待已提交补偿结束并清理自身数据、外部响应、计数和故障设置；失败时禁止继续复用。

目录入口及维护方式见[测试模块说明](../../../paytech-client-master-test/README.md)。本次没有新增或修改同步业务接口，不涉及 Processor、Context、本地业务事务或事务外调用的生产实现变更。

## 场景保全检查

使用 JDK AST 读取整理前后测试方法及其注解，确认 **137 个方法名称、测试注解和参数化数据完全一致**。抽象基类与共享辅助不含测试方法。

比较整理前后的 Surefire XML，以测试方法及参数化调用名称逐项核对：发现的 **158 项用例、失败方法集合和错误类型一致**，没有新增跳过或排除。

| 整理后的分组 | 执行数 | 本次结果 |
| --- | ---: | --- |
| Checkout HTTP、内部解析、规则与资格适配 | 43 | 通过 |
| 入驻 HTTP 五组场景 | 80 | 通过 |
| Client Tenant 投影 Handler | 17 | 通过 |
| 补偿持久化 | 5 | 通过 |
| 配置约束 | 6 | 通过 |
| 入驻投影快照 | 3 | 通过 |
| 无数据源的启动、健康、Problem Profile | 4 | 与整理前相同的启动错误 |
| 合计 | 158 | 154 通过，4 错误，0 断言失败，0 跳过 |

## 执行环境与结果边界

使用 JDK 25、仓库已有本地 MySQL/Redis。凭据按本任务已有授权仅注入测试进程，不打印、不写入仓库。没有执行 Schema 初始化或迁移。

本机缓存的 Platform API 与当前 CCT 引用不匹配，验证沿用临时 reactor，以当前 Platform API 源码副本提供构建依赖，再构建当前 CCT；没有修改 Platform 仓库或发布依赖。因此本记录不声称脱离该依赖准备后的 CCT 独立构建成功。

实际执行了整理前基线及整理后 `clean verify`，并在整理期间分别运行入驻 HTTP、Checkout 分组。最后一次完整构建的退出码为 1，来自以下四个原有错误：

- `ClientContractApplicationTest.test_applicationStartsWithoutPlaceholderBusinessRoute`
- `ClientContractApplicationTest.test_draftRoutesRemainAbsentWhenNotExplicitlyEnabled`
- `ClientContractHealthTest.test_healthEndpointsReportUpWithoutDetails`
- `ClientContractProblemProfileTest.test_clientContractInheritsTheCanonicalMvcProblemProfile`

共同原因是无数据源场景仍装配 `CheckoutQueryController`，但缺少 `CheckoutResolutionProcessor`。整理前已经复现；本次保留测试和生产装配行为，没有加入替身、禁用测试或放宽断言。

本机执行日志（临时文件，不作为可移植构建依赖）：

- 基线：`/tmp/cct-auto-organization-baseline.log`
- 入驻分组：`/tmp/cct-auto-organization-onboarding.log`
- Checkout 分组：`/tmp/cct-auto-organization-checkout.log`
- 最终全量：`/tmp/cct-auto-organization-verified.log`

静态核对包含目录与 package 对应、测试发现集合、辅助类不含测试方法、文档相对链接及差异空白检查。历史 Checkout 验证记录保留其当时类名和执行结果，不用本次目录整理改写历史。

## 同日后续：仅保留 resolve 测试

根据用户明确要求，删除 `CheckoutContextApi#resolve` 之外的全部测试。保留 `ResolveCheckoutHttpTest` 的 13 项及 `ResolveCheckoutValidationTest` 的 3 项 HTTP 场景；两类仍引用正式 API 方法并沿真实应用链路执行。

删除 18 个其他测试类、旧 precheck 地址检查、14 个不再使用的辅助类及 `application-onboarding-auto.yml`。同时删除仅供独立领域/Service 测试使用的 Fixture 方法、退役入口 Checker、直接测试依赖和重复的 Enforcer 配置；依赖门禁继续继承父 POM。生产代码没有修改。

沿用上述临时 Platform API reactor 和已授权凭据注入方式执行 `clean verify`，退出码 **0**。Surefire 实际发现 **16 项，全部通过，0 失败、0 错误、0 跳过**；日志为 `/tmp/cct-resolve-only-verify.log`。静态检查确认没有其他测试注解、失效的测试辅助引用或当前执行说明中的已删除类名，`git diff --check` 通过。

此前的 158 项缩减为 16 项是本次授权删除的结果，包含原有四项启动检查；本次通过不表示那四项对应的生产装配问题已修复，也不代表其他能力的验证结果。
