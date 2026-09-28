# 单元测试规范

用户要求先生成本地规范，使其他 AI 能按相同方式编写测试。参考 TransactionFacade payment 的测试模块、Builder、Mocker、Checker，明确允许外部接口直接配置响应 DTO，并确认 MockTestBase 全局 BeforeEach、测试类 BeforeEach 和每例全局 AfterEach。

本期仅整理规范、可操作示例、AI 入口和旧规则取代说明。完整约束由 [UNIT_TEST_GUIDE.md](../../../agent-docs/UNIT_TEST_GUIDE.md)维护；不新增测试框架、业务代码、数据库结构或运行环境。

验收：其他 AI 能据文档区分真实本地链路与外部 DTO Mock，照示例安排两级准备和失败后清理，明确禁止为测试改业务逻辑，并找到同一现行 Owner 与自查清单。确认和交付范围见[交付记录](delivery.md)。
