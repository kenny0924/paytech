# 决策流研发与验证计划

状态：原阶段 1–4 已确认；阶段 5 综合验证已交付，待用户确认。用户确认先精简重复逻辑，[精简修订及 SIM-01–08 验收](./simplification.md)与受影响研发已确认完成，综合验证结果及边界见 [综合验证记录](./acceptance.md)。本页保留原研发范围。原开发证据见 [开发交付报告](./development-checks.md)，确认与实现状态分别见 [delivery.md](./delivery.md) 和 [当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。

## 1. 研发顺序及职责

| 批次 | 修改位置与工作 | 完成证据 |
|---|---|---|
| 1. 模型与 HTTP 合同 | Risk 的既有模型生成源/策略/Oracle → 唯一 PDManer/完整 Schema；risk-api 定义 DecisionFlowManagementApi、DecisionFlowQueryApi、DecisionFlowModels、原因专用引用 DTO；记录 OpenAPI/错误/边界 | 生成差异只有本期 4 表；准确 Request/Response 序列化；原接口保持 v1，类型名不带版本 |
| 2. Risk 配置闭环 | risk-decision 增加 Flow 聚合、纯 Graph Validator、业务 Service/Repository 接口与 Impl、Mapper XML；risk-web 增加 Controller 和写命令 Processor；读接口复用现有查询事务模式 | 保存/回读/历史/删除、引用矩阵、完整事务和幂等；没有真实决策/外部动作调用 |
| 3. 既有规则及查询 | 调整动作可选的写入校验、聚合重建、Freeze 就绪检查；原因引用 UNION 查询；受真实流程/旧点绑定约束的规则只读读取 | LEGACY/CONFIGURATION 区分，原 Hash Golden 和回放不变，旧查询正常 |
| 4. Operations BFF | 注册 Owner @HttpExchange 客户端；请求/响应 Shape、标量类型、错误适配；新增流 Controller；沿用统一 PLT-I02 入口授权 | 10 操作正确转发、4 原操作新旧 Reader 一致；无数据库、图业务规则或跨服务写事务 |
| 5. Web | 安装并锁定 @xyflow/react；列表/画布/节点/条件侧栏/顺序/历史只读/准确引用；规则动作可选的统一编辑和字典来源跳转；浏览器导航与未保存保护 | Pencil 对照、后端 JSON 无损往返、错误定位、无返回按钮、只读行为 |
| 6. 注册与文档 | Platform 唯一初始化 DML 合入本期注册，更新必要注册断言；同步接口说明、需求站与台账入口 | 注册 JSON/SQL/接口清单一一对应；没有 Gateway 硬编码变化 |

不为 BFF 简单转发增加空 Processor；Risk 写用例因事务、幂等与资源锁确需 Processor。Service/DomainService/Repository 均按项目采用接口与 Impl，纯 Validator 不注入 Repository/Client。DataObject 仅在 Risk Owner 能力模块内可见，API 与跨模块返回应用模型。

前端采用受控 nodes/edges 状态，以显式 Converter 在 Risk Graph 与 React Flow 之间转换；坐标取整，业务字段不藏进不受控 data Map。官方提供了读取节点/连线状态并保存、恢复画布的方式，但不会替我们定义 Risk 规则或执行器。[React Flow 保存恢复示例](https://reactflow.dev/examples/interaction/save-and-restore)。本期不安装布局引擎或后端流程框架；依赖版本与兼容核对结果见开发交付报告及 Web 的 package-lock.json。

## 2. 先建立失败证据再实现

- 原规则公开 HTTP 的“无动作应拒绝”测试拆清 LEGACY 缺失、CONFIGURATION 合法空动作及损坏动作三种输入；先构造新能力当前不能通过的证据，再实现往返与冻结拒绝。
- 新 Flow 用最小完整图验证首次保存和 GET 对等，再逐步补分支顺序、准确引用和旧历史。纯拓扑/类型算法可以聚焦测试；数据库、Redis、幂等与引用状态必须通过真实入口闭环验证。
- 既有未提交 Risk 字典实现和 Web design.pen 必须保留，在研发开始重新检查各仓库状态。仅文档修改不触碰业务代码，不替用户提交或创建分支。

## 3. 风险对应的测试矩阵

| 组 | 需要验证的可观察行为 | 验收映射 |
|---|---|---|
| 流根与版本 | 一流一行，编码/名称过滤、LIKE 字面量、空页；同点多流；保存后图/名称/条件/坐标一致，旧版本不改写 | F01/F02/F07 |
| 静态图与条件 | 多开始、无结束、断线、重复/缺失优先级、无 ELSE、多 ELSE、自环/多节点环、不可达；合法 AND/OR/NOT、Boolean matched 与数值字符串；未知来源、未来/跨节点引用、错误类型；50/100/深度/字节边界 | F03/F04/F05 |
| 引用生命周期 | 同点不同合同拒绝；新 DRAFT/FROZEN 可选；原 nodeKey+准确版本 RETIRED 可保留，新节点不可复用；删除引用阻断新保存但历史可读；节点/流版本归属不可伪造 | F06/F08 |
| 原规则兼容 | 未配置动作规则的保存/回读；LEGACY 仍需终态；原附加动作不变；未配置动作规则不能 Freeze；旧 Candidate 单规则闭包与全部已有 Hash Golden/幂等回放保留 | F05/F08/F10 |
| 事务与并发 | 同命令同键回放、不同行为同键冲突；并发保存/保存删除只一方成功；不同流可并行；Redis Lease 失效后 CAS/唯一键仍守住；规则删除/退休、原因停用并发；失败无孤立版本/明细/回执 | F07/F09 |
| 原因引用 | 停用原因保留精确原 tuple，新选拒绝；默认文案变化不改历史；RULE_SET/FLOW_END 混合统一分页、CURRENT/HISTORY/DELETED/ALL；标签接口不变 | F02/F08 |
| 旧点投影 | 准确序号→只读链，保留原默认决定和 Hash；真实 binding 上下文才能读取已删除规则；不回填 flow，不允许编辑/保存旧投影 | F08 |
| BFF/入口 | READ 不能写、SAVE 不能删除、授权失败 Risk 零调用；未知 Body/Query、跨资源/响应损坏、Owner Problem/超时；无新增 Gateway 路由名单 | F10 |
| 前端 | 六张画板与状态表；详情/历史同一画布只读；点击线定位、出口排序、动作直接添加/编辑/清空、实际动作摘要；无规则模式切换；浏览器返回层级和过滤恢复、离开保护；失败不丢输入；删除确认与红色菜单 | F01–F10/U01–U04 |
| 无运行副作用 | 校验/保存/删除不创建 Release、Evaluation、ActionEffect、限制、标签事实，也不发送业务 MQ 或调用外部 Owner | F10 |

每个场景独立数据，不用一个顺序长测试覆盖所有分支。Risk HTTP 使用随机端口应用、生产 Service/Mapper、仓库 local-environment MySQL/Redis；BFF 通过协议 Simulator 验证 Risk/Platform 出入站，不 Stub 本服务 Service。所需新表缺失时快速失败并给出明确 SQL 准备提示，禁止测试自动 DDL、Flyway/Testcontainers 或借 test 库替代本机验证。

## 4. 阶段 4 / 5 的具体检查入口

在依赖和获准本机 SQL 已就绪后执行（以下是通用入口；本轮实际 wrapper、环境注入和结果见 development-checks.md）：

```sh
npm --prefix requirements-site test
mvn -f risk-compliance-service/pom.xml test
mvn -f operations-console-bff/pom.xml test
npm --prefix operations-console-web test
npm --prefix operations-console-web run build
```

涉及 risk-api 公共合同，Risk 完整 reactor 不能只以一个纯 Validator 测试代替；BFF 测试前使用已审查的同版本本机制品内容，不自动向共享制品库发布。Platform 如果只是注册数据，不跑不相关业务测试，执行既有注册/Schema静态检查及相关 Snapshot/PLT-I02 入口验证。Gateway 只在最终真实入口核对 Snapshot 和转发，除非发现确切代码问题，不扩大修改范围。

浏览器在桌面常用宽度及侧栏收起状态检查，保存后重新进入、历史节点/线切换、慢请求/失败、键盘/鼠标删除、浏览器 Back/Forward 与未保存离开。只读需同时禁用 drag/connect/reconnect/delete 和键盘修改，平移/缩放/选择仍可用。

## 5. 发布依赖与交付出口

未来制品：risk-api + Risk 应用、BFF、Web；Platform 为 SQL 注册，Gateway 不因本需求新增生产代码或 Target。先落获准结构与后端，再更新 BFF Reader/Web，最后开放路由/菜单/写授权；未更新旧 Reader 的浏览器标签页需刷新。严格 Reader 切换存在短暂不兼容窗口；阶段 6 安排受影响风控入口的短维护关闭，待 Risk/BFF/Web 对齐后恢复，不承诺滚动零中断。实际制品版本、环境连接、SQL状态、目标实例和开放版本由阶段 6 具体列出，不把这个顺序当作部署授权。

阶段 4 交付实现差异与开发检查；阶段 5 再交付综合验证/视觉证据。测试或部署完成才更新 CURRENT_IMPLEMENTATION 的相应事实，本设计稿不提前填写通过、已发布或已部署。
