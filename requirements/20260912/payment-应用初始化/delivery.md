# Payment 应用初始化交付记录

## 用户指令与边界

2026-09-12：用户直接要求按规范初始化 Payment 应用，并明确 Commerce 暂时保持不变。本次实施限于应用基础工程及相关验证；未获得后续业务、数据库操作、制品发布、部署、提交或推送授权。

未另行记录不存在的分阶段确认。本目录保留该次基础初始化的需求、技术选择与交付证据，不扩大为支付/Card 需求实施。

## 验证记录

2026-09-12 本地验证：

- 先添加入口测试，确认编译失败原因仅为缺少 `PaymentApplication`，再实现启动类与配置。
- 日志验证进一步复现默认格式不输出键值元信息；补充公共日志格式后，完整 JDK 25 `./mvnw -B -ntp clean verify` **14/14** 通过，0 失败/错误/跳过。
- 使用真实 `main` 启动随机端口，验证健康、Liveness/Readiness、管理端点白名单、Prometheus、Info、统一 404/Request ID、正文与查询参数禁日志。JaCoCo 覆盖启动类 3/3 行；仅是基础入口范围，不代表支付业务覆盖。
- 打包后验证 `-exec.jar` 默认监听 `127.0.0.1:18103`、Context Path `/payment`，8 个路径 smoke 通过。首次核验脚本把 Health 的合法 `groups` 字段误判为失败，修正为校验 `UP` 且无组件详情后通过；生产代码未为此修改。核验进程均已停止。
- 制品包含应用入口，54 个运行依赖中没有测试、数据库、Nacos、Redis 或 MQ 库；Test 模块不生成 JAR。
- `npm --prefix requirements-site test` **136/136** 通过；POM XML、Wrapper Shell 语法和变更空白检查通过，原 Payment 数据库目录无差异。

本机日志归档于 `/Users/kenny/.codex/verification/payment-application-foundation-20260912/`。当前状态唯一入口为[项目台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-application-foundation)。

## 数据、部署与恢复

- 不涉及 SQL、PDManer 修改或数据库备份/恢复。
- 本次只在本机启动验证进程，核验后停止，不改变共享环境。
- 构建生成的 `target/` 由仓库忽略。业务模块、真实渠道、Card 与 Commerce 集成仍需后续用例确认。
