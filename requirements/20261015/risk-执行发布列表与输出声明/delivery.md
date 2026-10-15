# 交付记录：Risk 执行发布列表与附加输出声明

## 1. 登记与方案确认（2026-10-15）

Operations 会话（code-mor-3e）请求两项 Risk 接口；Risk 会话回复方案，Operations 同意字段与语义（列表 pointCode 必填、未知状态容错、回滚只做新建发布、附加输出先写死后切换）。用户回复“可以”，按[需求](./requirement.md)直接进入研发。

## 2. 研发（2026-10-15）

- **执行发布列表：** `ExecutionReleaseManagementApi#listExecutions`（`GET /decision-releases/v1/executions`）；只读查询 Repository `ExecutionReleaseQueryRepository`（关联决策点、流程版本、流程，激活人取进入 ACTIVE 的状态审计），`ListExecutionReleasesProcessor` 在可重复读只读事务中读取总数与当前页。发布接口的 Query 白名单补登记 `/executions`。
- **附加输出声明：** 决策点合同的附加输出增加名称、可选值名称与可配置结束决定（ALLOW），流程保存校验与创作上下文同源；`FlowAuthoringContextResponseBody` 顶层新增 `outputs`，没有声明时不输出该键（结账点响应不变，旧版 BFF 兼容）。
- **测试：** 新增 `ExecutionReleaseListApiAutoTest`（8 项：三种状态与倒序、生效标记、流程坐标、激活人、状态筛选、分页、空页与结账候选不出现、6 类参数错误）；`FlowEndOutputApiAutoTest` 增加创作上下文声明用例。Risk 全量 auto **2001/2001**。
- **与 Operations 的约定：** 部署顺序为先 Operations BFF 新版、后 Risk（R-Q5）；Risk 只记元数据已覆盖 `/risk-lists` Query（R-Q4）。R-Q3（变量详情回显 Feature 参数、变量创作上下文返回可选值）用户决定暂不做（2026-10-15），Operations 按标准目录写死。

## 3. 提交与 mor-test 部署（2026-10-15）

用户回复“提交和部署”。代码提交 `b26e857`（不推送；用临时索引只提交 Risk 与本需求文件，Payment 会话已暂存的改动未带入）。mor-test 只替换 Risk 为 `fcfc9457dab6`，无 SQL；结账接口响应前后一致，新能力验收通过，见 [部署与验收记录](./release/test-acceptance.md)。部署顺序因兼容性证据调整为先 Risk，已通知 Operations。
