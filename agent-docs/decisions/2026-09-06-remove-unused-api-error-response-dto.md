# 移除无引用的旧 ApiErrorResponse Java DTO

- Decision status: Confirmed
- Implementation status: Complete
- Deployment status: Not deployed
- Decision date: 2026-09-06
- Scope: Foundation 旧四字段 Java DTO、其单元测试，以及工作区测试 fixture 对该 DTO 的编译依赖
- Superseded sections: `docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md` Phase 4 中把 `ApiErrorResponse` DTO 与 Legacy Decoder 绑定到同一删除门禁的部分

## 背景

工作区引用审计确认，`ApiErrorResponse(code,message,requestId,timestamp)` 已没有生产代码消费者。Foundation 的迁移 Reader 直接校验 JSON Tree，不依赖该 record；Operations BFF 只在测试 Simulator 中借用该类型生成旧格式 fixture。

继续公开该类型会让调用方误以为旧四字段响应仍是可采用的 Foundation 合同，也为一个没有运行时职责的类型保留额外 API 表面。

## 决定

1. 删除 Foundation 的 `ApiErrorResponse` record 及只验证 record accessor 的单元测试。
2. Operations BFF 的兼容测试直接生成四字段旧 Wire JSON，不再依赖共享 DTO；测试继续验证迁移 Reader 丢弃上游敏感 `message` 的行为。
3. 本决定不删除或放宽 Foundation、Operations Web/BFF、Gateway、Vault 的任何 Legacy Reader、显式迁移开关或协议兼容测试。它们仍须等全部已部署第一方 Producer/Consumer 切换并完成环境观测后再移除。
4. 旧四字段错误体仍不是任何新 Producer 或公共 API 可以输出的合同；删除 Java convenience type 不代表 Legacy Wire 协议已经完成下线。

历史实施计划中“当次任务暂不删除 DTO”的记录保持原样，因为它准确描述当时的执行边界。
