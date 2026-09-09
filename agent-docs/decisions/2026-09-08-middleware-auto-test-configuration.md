# 中间件 auto 测试启动与配置

日期：2026-09-08。状态：用户已确认。

## 决策

- 使用数据库、Redis 或 MQ 的测试必须启动被测服务的 Spring 应用上下文，复用生产自动配置、连接工厂和生命周期；HTTP 链路同时启动随机端口服务。不得在静态辅助类中独立创建中间件客户端或读取环境变量。
- 固定测试配置集中在测试模块的 `application-auto.yml`，由 Spring Profile 和配置绑定加载。环境覆盖通过 YAML 占位符表达；Java 只注入运行时产生的 Simulator 地址、随机端口、隔离标识或明确的场景变量。故障模式的固定连接参数同样放在 YAML。
- 纯 Java 算法和无中间件协作测试不强制启动 Spring。启动被测服务不等于同时启动其他业务系统；外部 HTTP 依赖仍使用有状态 Simulator。
- 中间件继续复用开发者已启动的仓库 `local-environment`。本决策不授权 Maven/JUnit 启停容器、修改共享服务密码或执行 Schema 初始化。
- 固定凭据仅适用于第 7.5 节批准的 Loopback-only、隔离、可重建测试账号；已有本地实例使用不同密码时通过安全环境覆盖，不读取或提交真实 `.env.local`。

## 权威与影响

完整测试规则归 [`UNIT_TEST_GUIDE.md`](../UNIT_TEST_GUIDE.md) 第 5、7、12 节；本地服务准备归 [`LOCAL_DEVELOPMENT_ENVIRONMENT.md`](../LOCAL_DEVELOPMENT_ENVIRONMENT.md)。本决策取代此前 Gateway 测试必须由 Java 直接读取 `PAYTECH_REDIS_PASSWORD` 的做法，不改变生产配置和安全边界。

本次实现范围为 API Gateway 测试设施及公共规则；其他系统按各自后续任务迁移。实现和实际验证结果只记录在 [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md)。
