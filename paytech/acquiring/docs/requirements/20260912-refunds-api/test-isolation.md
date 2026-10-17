# Payment 模拟代码的测试模块隔离

用户明确禁止为了测试向业务源码加入测试代码。本修正只涉及 Commerce，在现有 Orders / Refunds 和 Processor 重构基础上增量执行。

- 删除业务模块中的 `LocalMockPaymentRefundClient`、`LocalMockPaymentTerminationClient` 及两个未接入实现的 `local-mock` Profile 条件。
- 业务保留真实本地端口和明确的未接入失败处理，不持有模拟状态、场景登记、结果安排、请求观察或清理方法。
- 模拟状态只在 `paytech-commerce-test/src/test/java/.../test/simulator`；`PaymentSimulationTestConfiguration` 只由需要的场景测试显式导入，不修改业务应用的装配分支，也不进入业务制品。
- 继续保留用户原先批准的外部 Mock + TODO 阶段。由于 Payment 仓库尚无可接入的 Java Owner API 契约，这里的测试是在本地端口模拟外部事实，证明范围限于 Commerce 编排、事务、幂等、金额和恢复。正式接入时必须改为真实生产 Integration Client 经 HTTP 连接独立 Simulator；本修正不会为测试在业务模块伪造一套 HTTP 契约。
- 新增生产源码隔离门禁与默认/遗留 Profile 启动装配检查；检查打包后的可执行 JAR，确认没有模拟类或测试装配。

本次本地验证记录：

- 隔离门禁先在旧业务 Profile 条件上失败，迁移后通过；默认和遗留 Profile 装配均只保留未接入实现。
- Orders / Refunds / 架构 / 运行配置定向测试 90 项通过。
- `./mvnw -q clean verify`：272 项测试全部通过，无跳过；生产代码行覆盖率 1872/2045（91.54%）。模拟器迁出后不再计入生产覆盖率，90% 门槛未调整。
- 清理构建后的普通 JAR 和可执行 JAR 均未包含 Commerce Mock、Simulator、测试包或测试配置类。
- `npm --prefix requirements-site test`：136/136 通过；`git diff --check` 通过。

未修改其他服务、Gateway、DDL 或 PDManer，也未提交、推送或部署。
