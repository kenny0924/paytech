# RCO-19 发布材料

本次用户明确“发布”，授权完成阶段 6 发布准备及阶段 7 部署到既有本机 Docker `mor-test`，包含本需求 SQL、变量菜单/权限/路由启用，以及先前确认的两个 test 示例迁移。验收、提交与推送继续等待用户各自指令。当前部署状态以[台账 RCO-19](../../../agent-docs/CURRENT_IMPLEMENTATION.md#rco19-variables-20260912)为准。

## 发布范围与材料

- 入口：`http://localhost:28080`，沿用现有测试账号；菜单为“风控管理 → 变量管理”。
- 应用：Risk、Operations Console BFF、Operations Console Web。Gateway 和 Platform 使用现有制品；Platform 仅应用本需求配置 DML，Gateway 自动拉取数据库配置。
- Risk SQL：[DDL](./db/risk-compliance-service/ddl.sql)、[DML](./db/risk-compliance-service/dml.sql)，2 新表 / 41 字段，4 张旧表增加 5 列并调整 4 列注释；17 个变量及 9 个集合成员。
- Platform SQL：[113 行注册](./db/platform-support-service/dml.sql)、[入口启用](./assets/stage7/enable.sql)、[成员路由大小修正](./assets/stage7/member-body-limit.sql)。初始化文件已同步最终值，已应用环境不得重新执行初始化 SQL。
- 最终镜像均以 `rco19-variables-20260913-body-limit` 标记。Risk API 只在本机 Maven 缓存安装供 BFF 编译，没有向 Nexus 或远程镜像库发布。

## 发布时的修正

原成员路由 256 KiB 超出现有 Platform/Gateway 的 64 KiB 绝对上限，Platform 拒绝输出 Snapshot，Gateway 继续使用原 61 路由配置。定位后单行 CAS 将本需求 Route 调整为 65,536 字节，Web/Risk/BFF、管理上下文和合同同步为 64 KiB。集合仍限制最多 1,000 项，同时必须满足总字节上限。没有扩大 Gateway 的全局安全上限。

新增 Risk 用例证明 65,537 字节在修复前被接受、修复后返回 413，65,536 字节仍能保存；新增 BFF 用例证明超限请求修复前到达 Owner、修复后在转发前拒绝。最终 Risk 针对性用例 1/1、BFF 变量边界 7/7、两个 JAR 打包和 Web 生产构建通过。test 入口的超限请求返回 413，原集合成员未变化；Gateway 最终 70 路由、刷新 SUCCESS，无旧配置回退。

首次发布脚本还遇到本机 Python 3.9 不支持 `zip(strict=True)`，发生在启用 UPDATE 执行前；改为显式长度校验后从未提交步骤继续，没有重跑 Schema 或初始化。一次 Risk 本机检查缺少环境密码而未启动，补充既有环境变量后取得失败/通过证据；这些过程均未记为通过。

## 示例迁移与恢复约束

遵循[迁移方案](./test-demo-migration.md)，通过真实登录及原 v1 管理 API 各保存一个新版本：

| 示例 | 新版本 | 保存身份 |
|---|---|---|
| `DEMO_FLOW_AMOUNT_CHECK` | 第 2 版 | `224761038741016576` |
| `DEMO_CHECKOUT_BRANCH_FLOW` | 第 2 版 | `224761039558905856` |

金额条件使用 `INPUT_NET_AMOUNT > DEMO_AMOUNT_THRESHOLD`，动作仍为空；流程的 matched 条件保留，币种分支使用 `INPUT_CURRENCY != DEMO_STANDARD_CURRENCY`。原 5 节点/4 连线及分支顺序保留，原第 1 版仍可读取。

专用示例常量以 CAS 临时改为 10000/EUR，读回规则和流程已显示新值且保存版本/规则 Hash/图未改变；随后以本次修改的准确 CAS 恢复 8000/USD。常量当前维护版本为 3，未写真实决策执行数据。

发布只做了一次受影响 Schema/管理员角色元数据的定向备份，位置和 Hash 在发布证据中，未进行整库或其他服务备份。新增变量格式已有消费者，发生问题时应关闭相关写入口并前滚修复；不能将这些数据交给旧 Reader 或直接 UPDATE 不可变历史。需要恢复示例业务条件时通过管理 API 保存新的明确版本。

## 可核验的证据

- [发布前基线与 SQL Hash](./assets/stage7/preflight.json)、[初始镜像](./assets/stage7/images.json)、[最终镜像及增量检查](./assets/stage7/images-body-limit.json)。
- [SQL/替换/修正检查点](./assets/stage7/deployment.json)、[示例迁移及恢复回执](./assets/stage7/demo-migration.json)。
- [最终真实入口检查](./assets/stage7/final-checks.json)：11 容器健康，3 应用为最终镜像，其他 8 容器身份/镜像/启动时间不变；17 变量/9 成员、38 资源/70 路由；6 个公开页面资源与最终 dist Hash 一致；验证会话已退出。

本次没有重复全量自动测试或浏览器人工验收，先前已通过的阶段 4/5 检查只用于未受改动影响的部分。真实执行不在本期，页面验收由用户继续完成。

## 验收期间的旧规则详情兼容修复

用户反馈 `DEMO_USD_ALLOW` 详情报“服务暂时不可用”，Request ID 为 `89f1b178-f8d9-4d18-853a-da2ee01d66e0`。BFF 日志和真实入口查询均为 HTTP 200；旧规则返回 `operandFormat=LEGACY`，Web 解码器只接受 `VARIABLE_REFERENCE`，因此在成功响应解析时误报服务不可用。

Web 在保留旧操作数语义校验的前提下接受 `LEGACY`；未知格式及混入变量操作数的旧格式仍会拒绝。新增失败回归后，259 项相关 Web 检查及生产构建通过。真实 `DEMO_USD_ALLOW` 响应已能解码并生成含两条规则的只读编辑模型，原内容、版本和 Hash 保持不变。

沿用本轮 test 发布授权，只替换 Web，镜像标记为 `rco19-legacy-detail-20260913`，其余 10 个容器不变，无 SQL 或规则数据修改。见[部署证据](./assets/stage7/rule-detail-compatibility/deployment.json)及[验证证据](./assets/stage7/rule-detail-compatibility/verification.json)。原生 Chrome 在刷新后仅提供菜单可访问性树，本轮不据此声称完成视觉验收。
