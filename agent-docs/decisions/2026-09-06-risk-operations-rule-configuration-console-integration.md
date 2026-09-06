# Risk Operations Rule Configuration Console 集成决策

- Decision status: Confirmed
- Implementation status: Not started
- Deployment status: Not deployed
- Decision date: 2026-09-06
- Scope: Operations Web、Operations BFF、Platform Support 与 API Gateway 接入 Risk `CHECKOUT_PREPARE` Rule Set 和 TEST Candidate 私有管理 API
- Supersedes:
  - `2026-09-05-risk-operations-rule-configuration-backend-design.md` 第 6.3 节中未来 Gateway/BFF 保存 Route 使用 1 MiB Body Limit 的建议
- Relationship: 本记录是独立的后继集成切片；不反向改变后端设计排除 Web/BFF/Gateway/Platform 的历史实施范围
- Superseded sections: 仅上述传输限额建议；Risk AST、不可变版本、Freeze、Candidate、静态检查、幂等、并发和数据库合同全部继续有效

> 当前实现、验证和部署事实只以 [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md) 为准。
> 本记录确认设计，不表示五组件实现、测试环境数据变更或部署已经完成。

## 已确认决定

1. 浏览器固定通过 `Operations Web -> API Gateway -> operations-console-bff -> Risk private API`。Gateway
   继续只指向现有 Operations BFF Target，不新增直达 Risk 的公共 Target 或 Route。
2. Operations Web 提供结构化规则构建器，以“规则、条件组、字段、运算符、值、动作”呈现配置；不向运营人员
   暴露 JSON、AST、Node Key 或 Sequence。页面内编辑模型只在提交时一对一转换成 Risk 现有强类型请求，
   不创建第二套持久规则 DSL。
3. BFF 使用现有 `/decision-rule-sets/v1` 和 `/decision-releases/v1` 两个窄路径根，发布九条精确浏览器接口。
   每条请求先执行 Platform PLT-I02，再调用一个 Risk 命令或查询；BFF 不复制 Risk 状态机、Hash、AST 校验、
   幂等或事务，也不为简单转发增加空 Processor。
4. Platform 新增五个独立权限：读取、保存 Rule Set、冻结 Rule Set、创建 TEST Candidate、验证 TEST
   Candidate。五条 GET 绑定读取权限，四条 POST 分别绑定自己的写权限；目录和按钮显隐不替代服务端授权。
5. 保存 Rule Set Route 的 Body Limit 固定为 65,536 UTF-8 Bytes；Freeze、创建 Candidate、Validate 固定为
   16,384 Bytes，五条 GET 固定为零 Body。Platform 与 Gateway 的可配置绝对上限只提升到 65,536，所有既有
   Route 保持原配置。Nginx 外层安全包络为 128 KiB，使已进入 Gateway 的 65,537 Bytes 请求由 Gateway
   统一返回 413 Problem；128 KiB 不是业务 Route 限额。
6. Validate 使用 `risk-validate-v1-{releaseId}-{expectedVersion}` 形式的确定性幂等键。Web 生成该 Key，BFF
   根据 Path `releaseId` 与 Body `expectedVersion` 独立重建并精确校验；不一致时返回 400 且 Risk 零调用。
   它不存储 AST、Literal 或完整请求，并使原操作员在刷新或重新登录后仍可恢复
   `VALIDATING + zero checks`。Risk 现有 `caller_scope` 继续隔离不同操作员；其他三个原子命令使用随机
   幂等键。
7. 未保存规则只保留在页面内存，不进入 Local Storage、Session Storage、URL、Console、普通日志或遥测。
   页面离开前提醒。规则 AST、Literal、阈值与 Handler 继续按机密风控策略处理，所有相关 HTTP 路径使用
   Metadata-only Logging。BFF 严格校验完整 Risk Authoring Context，但浏览器投影删除
   `handlerCode/implementationVersion/implementationHash`，前端展示目录也不持有这些实现指纹。
8. 首期只允许从当前最新 Rule Set Version 继续编辑。每次保存创建新的不可变 DRAFT；历史 Version 只读。
   只有最新 DRAFT 可冻结，只有 FROZEN 可创建 TEST Candidate。`200 + REJECTED` 是静态校验业务结果，
   不是请求错误。
9. 本切片不加入 Shadow、审批、激活、PRODUCTION Release、运行期 Checkout 评估、自动保存、Server-side
   Draft、规则导入导出或部署。

## 权威与后继

- 完整产品体验、接口、权限、Body Limit、失败处理和验收合同由
  [`2026-09-06-risk-operations-rule-configuration-console-integration-design.md`](../../docs/superpowers/specs/2026-09-06-risk-operations-rule-configuration-console-integration-design.md)
  持有。
- Risk 后端合同继续由
  [`2026-09-05-risk-operations-rule-configuration-backend-design.md`](../../docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md)
  持有，只有本文明确列出的 1 MiB 建议被取代。
- 后继实施计划必须覆盖 Operations Web/BFF、Platform、Gateway、Nginx、Requirements 与文档同步；未获得
  单独部署授权时只能修改和验证本地工作区。
