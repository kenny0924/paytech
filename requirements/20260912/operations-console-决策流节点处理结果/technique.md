# 节点处理结果条件：下一期分析入口

本次只登记已识别影响，不冻结技术方案，不启动研发。

| 系统 | 已识别影响 |
|---|---|
| Risk | 在原 matched 以外扩展节点输出目录、结果条件校验及保存/历史读取合同；明确节点结果与流程终态的区别 |
| Operations BFF | 同步节点输出元数据与枚举、请求/响应转发和校验 |
| Operations Web | 保留“是否命中”，增加“本节点处理结果”；结果选项消费后端合同 |

现有 `t_decision_flow_edge.condition_json` 可承载字符串结果条件，目前没有已证明需要新增 API、表或字段的理由；完整规模待结果汇总、空动作/未命中和历史兼容方案确认后确定。未生成或执行 DDL/DML。

当前实现会校验 NODE_RESULT 只能为 matched + Boolean；Web 的该选项固定添加。具体源码入口：

- [Risk 条件校验](../../../risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/flow/DecisionFlowValidatorImpl.java)
- [Risk 能力目录投影](../../../risk-compliance-service/risk-web/src/main/java/com/paytech/risk/ext/rest/decision/DecisionFlowWireConverter.java)
- [Web 条件编辑器](../../../operations-console-web/src/risk/FlowConditionEditor.tsx)

本期已经保存的示例继续保留 matched，不在登记时转换。下一期按[交付流程](../../../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md)逐阶段确认，避免将配置更新误记为执行器已经支持。
