# RCO-17 阶段 4：研发与开发检查

2026-09-12 原阶段 4 交付证据，随后用户已确认进入阶段 5；本文件保留精简前的验证快照。后续三表精简修订的证据见 [修订开发检查](./simplification-development-checks.md)，其阶段 4 开发回归已完成，阶段 5 待用户确认恢复。实现与环境状态的唯一台账为 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。本记录不表示 test 部署、Platform 数据库变更、共享制品发布、Git 提交或推送。

一句话规模：修改 Risk、Operations BFF、Operations Web 3 个应用，新增 10 个业务 API、调整 4 个原 API；Risk 新增 4 表、44 字段、11 索引，旧表结构不变；Platform 唯一初始化 SQL 合入 118 行注册和 1 行角色版本更新，涉及 7 张现有表；Gateway 无生产代码改动。

## 1. 交付范围

| 系统 | 已交付内容 | 边界 |
|---|---|---|
| Risk | 流程目录、单行列表、完整保存、静态校验、当前详情、历史列表及准确版本、整体删除、持久引用下的规则详情、旧顺序只读投影 | 只配置，不执行图，不发布或激活；不写 Evaluation、ActionEffect 等运行事实 |
| Risk 原规则/字典 | CONFIGURATION 规则动作可空，LEGACY 仍要求终态；空动作不能冻结；原因引用统一查询 RULE_SET/FLOW_END，支持当前、历史、删除范围及混合分页 | 无模式字段、无字典回执/适用关系表；旧 Hash 和 Candidate 路径保留 |
| Operations BFF | 10 个操作的入口授权、请求/响应形状与身份校验、单次 Owner 转发、Problem 适配；同步原因引用与动作可选 Reader | BFF 不接数据库、不判断图业务规则；DELETE 使用 Query CAS，无请求体 |
| Operations Web | React Flow 受控画布、三类节点、条件组合与出口排序、准确规则版本选择、显式结束、校验定位、历史同页只读、删除菜单、字典来源跳转 | 锁定 @xyflow/react 12.11.6；没有后端流程框架或真实执行入口 |
| Platform | 已审核注册逐值合入唯一初始化 SQL，检查每条只出现一次 | 未应用到数据库；Route/Resource 仍为 DISABLED，写角色授权为 REVOKED |

接口继续使用 `/decision-flows/v1` 及既有 v1 路径；新增类型名不带 V1/V2。精确方法、字段和权限见 [api.md](./api.md)。分页沿用项目已有 Foundation 合同：page 1..100000，size 10/20/50/100/200，默认 1/10；修正了设计稿误写的 size 1..100，没有增加自定义分页协议。

Risk 写入由资源级 Redis 锁、事务、主键锁和 CAS/唯一索引共同保护，复用 Risk 现有幂等机制。完整图以节点/连线结构保存，条件 JSON 采用受限合同和严格读取；Jackson 依赖为 Risk 决策模块直接声明的既有 BOM 版本，用于实际存储 JSON 消费者。列表当前页节点数批量查询，不为每行单查。生成源按原 manifest 体系新增 09-decision-flow，保留原 74 个实体，提案内部模型 ID 不充当业务身份。

## 2. 开发检查证据

以下数字对应本次工作区实际运行，不表示完整验收矩阵、覆盖率或 test 环境已经通过。

| 检查 | 结果 | 证据 |
|---|---|---|
| Risk 完整 reactor test | 205/205，0 失败/错误/跳过 | `/tmp/rco17-risk-reactor-tests.log` |
| BFF 完整 reactor test | 1,046/1,046，0 失败/错误/跳过 | `/tmp/rco17-bff-reactor-tests.log` |
| Web 全部测试 | 894/894 | `/tmp/rco17-web-tests.log` |
| Web TypeScript 与生产构建 | 通过 | `/tmp/rco17-web-build.log` |
| Risk 模型/Schema | 29/29；原 74 个实体与本期前基线逐项相等 | `/tmp/rco17-model-tests.log` |
| 设计/注册一致性 | 4 表/44 字段/11 索引；118 插入/1 更新；Schema 合法及 4 个非法示例、本地链接 | `/tmp/rco17-design-check.log` |
| 需求站构建及测试 | 136/136；旧 74 表断言同步为已核对的 78 表 | `/tmp/rco17-requirements-tests.log` |

Risk 通过仓库根 Maven Wrapper、Java 25 运行 `-f risk-compliance-service/pom.xml -pl risk-test -am -Dsurefire.failIfNoSpecifiedTests=false test`。仅从本机已运行容器将测试凭据注入子进程，不写入日志或源码；MySQL/Redis 为 local-environment 的 13306/16379。risk-api 仅安装到本机 Maven 仓库供 BFF 编译；没有共享发布。Web 使用 `npm --prefix operations-console-web test` 和 `npm --prefix operations-console-web run build`。需求站使用 `npm --prefix requirements-site test`；注册检查使用本目录 `check-design.mjs`。

新增关键行为测试入口：

- [DecisionFlowConfigurationHttpAutoTest](../../../paytech/risk-decision/paytech-risk-decision-test/src/test/java/com/paytech/risk/auto/ext/http)：真实 MySQL/Redis/HTTP 的 10 项流程场景。
- 纯图校验 7 项及规则动作可选 HTTP 5 项包含在 Risk 总数中，不重复累加；旧字典、Hash、回放与其余原回归一起运行。
- [RiskFlowBrowserBoundaryAutoTest](../../../paytech/operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/RiskFlowBrowserBoundaryAutoTest.java) 覆盖 10 操作、读写/删除权限分离、授权失败零 Owner 调用、无体 DELETE、损坏成功响应与 Owner 错误适配。
- [decisionFlow.test.ts](../../../operations-console-web/src/risk/decisionFlow.test.ts) 和 [decisionFlowApi.test.ts](../../../operations-console-web/src/risk/decisionFlowApi.test.ts)：无隐式默认决定、图序列化、删除关联线及连续优先级、URL 版本坐标、严格响应身份/CAS、无体删除、静态校验无命令键。

## 3. 本期验收映射与浏览器检查

| 对应需求 | 本阶段证据 | 阶段 5 补齐 |
|---|---|---|
| F01/F02/F07 | 保存回读、当前列表、不可变历史、编码永久占用；编辑并发一胜一冲突；本机页面列表/画布/保存更新 | 列表边界过滤及完整前后端串联 |
| F03/F04/F05 | 图/条件校验、422 字段定位、动作可空往返及冻结拒绝；Web 出口上移与删除后连续编号 | 完整节点/条件极限和操作矩阵 |
| F06/F08 | RETIRED 同节点保留/新节点拒绝；删除引用阻断新保存、历史仍可读；旧顺序真实绑定投影；原因混合分页 | 多引用变更并发、所有来源浏览器跳转 |
| F07/F09 | 同键稳定回放；保存/删除竞争仅一方成功；删除不清除历史，无孤立版本/明细 | Lease 失效和更多故障注入 |
| F10 | 保存/校验不产生运行事实；BFF 权限分离与失败零调用；保留既有运行合同 | 真正 Gateway/Snapshot/PLT-I02 集成与完整门禁 |
| U01–U04 | Pencil 方案对应的首轮本机桌面布局与代表性交互检查 | 全部视觉状态、窄宽度、键盘、拖动/重连和端到端验收 |

浏览器使用 `127.0.0.1:18311` 隔离开发预览，运行实际 Web 源码，HTTP 返回本机进程内的合成场景；没有连接 test 或通过浏览器访问业务数据库。已观察：列表详情/编辑入口；详情和历史同一画布无保存按钮；节点/连线配置摘要与字段中文标签；条件上移；Back 触发未保存提示，取消保留排序；校验提示与保存后的版本更新；历史版本入口及浏览器返回列表；首次新建不默认放行，保存后 URL 切到该流程的 edit，刷新仍读取它；模拟 409 冲突保留本地内容并显示重新加载，取消保留、确认才替换。

开发检查修复了条件删除后的优先级空洞、冲突提示按业务错误码判断、首次保存后的 URL 更新，以及已删除流程从准确版本进入历史列表的读取路径。临时浏览器标签页和预览进程已关闭。浏览器日志只有原 Ant Design Spin `tip` 弃用告警；构建保留既有公共 chunk 大于 500 KiB 的提醒，不视为本期功能失败，也未进行无关拆包改动。

## 4. 数据与下一确认点

本机 4 表 DDL 已按用户单独授权，在 Schema 备份后人工应用。执行范围、备份目录和结构核对见 [local-application.md](./db/risk-compliance-service/local-application.md)。测试创建的合成数据按测试身份清理；早期失败遗留的一条测试拒绝原因，在核对精确 ID/代码/操作员且无规则/流程引用后清除。未改动旧业务数据。

当前等待用户接受阶段 4 结果后进入阶段 5：完成约定的综合验证、页面视觉/交互及必要故障场景，整理可复查证据。该阶段确认不包含 test 部署或提交；若真实入口验证需要新的数据库准备或外部配置，会先交付具体对象再按已有流程取得该动作授权。真实执行器仍是下一期。
