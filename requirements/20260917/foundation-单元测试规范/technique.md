# 技术说明

采用一个现行 Owner：[测试规范](../../../agent-docs/UNIT_TEST_GUIDE.md)。[公共示例](../../../agent-docs/examples/UNIT_TEST_EXAMPLE.md)承载类间协作和生命周期写法，不代表框架已实现。

[本次决定](../../../agent-docs/decisions/2026-09-17-test-dto-mock-lifecycle.md)窄范围取代外部强制有状态 Simulator、禁止响应 DTO 等旧约束；[旧版归档](../../../agent-docs/UNIT_TEST_GUIDE_BEFORE_20260917.md)保留历史证据。

同步 AGENTS、Cursor 测试规则、开发/工程规范、文档路由与治理入口。测试专用环境才能全局清理；真实本服务及出站 Client 保留，外部 HTTP 替身独立装配；只读 Checker、未配置调用失败、两级初始化、清理聚合和环境串行均有明确规则。

不涉及 API、SQL、PDManer 或部署。验证范围为文档链接、规则投影和示例一致性阅读；不执行 Java 构建或业务测试，不声称代码已迁移。
