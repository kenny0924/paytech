package com.paytech.operations.console.bff.service.model;

import java.util.List;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.OperandRequestBody;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.RuleSetDetailResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 决策流配置 HTTP 合同；所有 BIGINT、坐标和时间使用字符串，未生效配置不冒充运行事实。 */
public final class RiskFlowModels {
    private RiskFlowModels() { }

    /** 完整图保存/静态校验请求，两个基准键必须同时为空或同时指向当前版本。 */
    public record SaveFlowRequestBody(@NotNull String pointCode, @NotNull String flowCode,
        @NotNull String flowName, String baseFlowVersionId, String expectedVersion,
        @NotNull String changeReason, @NotNull @Valid Graph graph) { }

    /** 只保存业务节点和连线；不接受图形库内部 viewport/selection 状态。 */
    public record Graph(@NotNull Integer schemaVersion, @NotNull @Size(max = 50) List<@Valid Node> nodes,
        @NotNull @Size(max = 100) List<@Valid Edge> edges) { }

    /** 坐标为有符号整数字符串，范围 -100000..100000，不接受 -0。 */
    public record Position(@NotNull String x, @NotNull String y) { }

    /** START/RULE_SET/END 的精确配置；不适用字段必须显式为 null。 */
    public record Node(@NotNull String nodeKey, @NotNull String type, @NotNull String name,
        @NotNull @Valid Position position, String ruleSetVersionId, String decisionCode,
        String reasonCode, String reasonMessage) { }

    /** 显式出线顺序；仅 CONDITIONAL 的 condition 非空。 */
    public record Edge(@NotNull String edgeKey, @NotNull String sourceNodeKey, @NotNull String targetNodeKey,
        @NotNull String type, @NotNull Integer priority, @NotNull String name, @Valid Condition condition) { }

    /** 条件 schemaVersion=1 读取旧历史，2 使用统一变量操作数。 */
    public record Condition(@NotNull Integer schemaVersion, @NotNull @Valid Expression root) { }

    /** kind 是 JSON 判别属性；不同变体不接受彼此的字段。 */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({@JsonSubTypes.Type(value = GroupExpression.class, name = "GROUP"),
        @JsonSubTypes.Type(value = PredicateExpression.class, name = "PREDICATE")})
    public sealed interface Expression permits GroupExpression, PredicateExpression { }

    /** AND/OR/NOT 分组；子项顺序保留。 */
    public record GroupExpression(@NotNull String operator, @NotNull List<@Valid Expression> children)
        implements Expression { }

    /** 比较字面量只接受字符串或 Boolean；NUMBER 也必须使用十进制字符串。 */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record PredicateExpression(@Valid Source source, @NotNull String operator,
        List<Object> values, List<@Valid OperandRequestBody> operands) implements Expression {
        /** 原条件只使用 source/values，新增条件使用 operands，传输 Reader 按版本验证互斥。 */
        public PredicateExpression(Source source, String operator, List<Object> values) { this(source, operator, values, null); }
    }

    /** 旧 NODE_RESULT 来源仅供历史 Reader；新变量引用不使用此结构。 */
    public record Source(@NotNull String type, @NotNull String fieldCode) { }

    /** 可用决策点由 Risk 目录返回，Web 不自行穷举。 */
    public record SupportedPoint(String pointCode, String pointName) { }

    /** 当前点可用于条件的字段及输入域，不包含任何运行原值。 */
    public record Field(String fieldCode, String label, String valueType, boolean required,
        List<String> operators, String literalDomain, String variableCode) {
        public Field(String fieldCode, String label, String valueType, boolean required, List<String> operators, String literalDomain) {
            this(fieldCode, label, valueType, required, operators, literalDomain, null);
        }
    }

    /** 系统节点输出定义的有限兼容投影，实际选择由变量目录提供。 */
    public record NodeOutput(String nodeType, String fieldCode, String label, String valueType,
        boolean required, List<String> operators, String variableCode) {
        public NodeOutput(String nodeType, String fieldCode, String label, String valueType, boolean required, List<String> operators) {
            this(nodeType, fieldCode, label, valueType, required, operators, null);
        }
    }

    /** Body/图/单条件树的同时生效上限，字节数均为 UTF-8。 */
    public record Limits(int bodyBytes, int nodes, int edges, int conditionBytes, int conditionNodes,
        int conditionDepth) { }

    /** 本期配置能力目录，不表示执行器已就绪。 */
    public record FlowAuthoringContextResponseBody(List<SupportedPoint> supportedPoints, String pointCode,
        String inputContractCode, int inputContractVersion, int graphSchemaVersion, List<Field> fields,
        List<NodeOutput> nodeOutputs, Limits limits, String descriptorCode, int conditionSchemaVersion,
        RiskVariableModels.VariableQueryContext variableQueryContext) {
        public FlowAuthoringContextResponseBody(List<SupportedPoint> supportedPoints, String pointCode, String inputContractCode,
            int inputContractVersion, int graphSchemaVersion, List<Field> fields, List<NodeOutput> nodeOutputs, Limits limits) {
            this(supportedPoints, pointCode, inputContractCode, inputContractVersion, graphSchemaVersion, fields, nodeOutputs, limits, null, 1, null);
        }
    }

    /** 每个流程根一行；当前版本数不是 API 版本。 */
    public record FlowListItemResponseBody(String flowId, String pointCode, String flowCode, String flowName,
        String currentFlowVersionId, int currentVersionNo, String version, int ruleSetNodeCount,
        String updatedBy, String updateTime) { }

    /** 原保存的稳定结果；version 不跟随后续编辑变化。 */
    public record SaveFlowResponseBody(String flowId, String flowVersionId, int versionNo, String version,
        String savedBy, String savedTime) { }

    /** 可定位的静态问题，未关联坐标为 null。 */
    public record Issue(String code, String nodeKey, String edgeKey, String field, String message) { }

    /** 静态校验不预留版本，不产生运行事实。 */
    public record ValidationResponseBody(boolean valid, List<Issue> issues, boolean truncated) { }

    /** 引用节点的准确规则版本和当前资格，不新增规则处理模式或分类计数。 */
    public record RuleSetReference(String nodeKey, String ruleSetId, String ruleSetCode, String ruleSetName,
        String ruleSetVersionId, int versionNo, int ruleCount, String ruleSetStatus, String versionStatus) { }

    /** 当前根事实与查询版本事实分别返回；删除根仅可通过准确历史入口读取。 */
    public record FlowDetailResponseBody(String flowId, String pointCode, String flowCode, String flowStatus,
        String currentFlowVersionId, int currentVersionNo, String version, String flowVersionId, int versionNo,
        String flowName, String inputContractCode, int inputContractVersion, String createdBy, String createTime,
        String changeReason, Graph graph, List<RuleSetReference> ruleSetReferences,
        List<RiskVariableModels.VariableSummary> currentVariables, String valuesAsOf, List<RiskVariableModels.VariableIssue> variableIssues) {
        public FlowDetailResponseBody(String flowId, String pointCode, String flowCode, String flowStatus,
            String currentFlowVersionId, int currentVersionNo, String version, String flowVersionId, int versionNo,
            String flowName, String inputContractCode, int inputContractVersion, String createdBy, String createTime,
            String changeReason, Graph graph, List<RuleSetReference> ruleSetReferences) {
            this(flowId, pointCode, flowCode, flowStatus, currentFlowVersionId, currentVersionNo, version, flowVersionId,
                versionNo, flowName, inputContractCode, inputContractVersion, createdBy, createTime, changeReason, graph, ruleSetReferences,
                List.of(), null, List.of());
        }
    }

    /** 不可变版本历史条目。 */
    public record FlowHistoryItemResponseBody(String flowVersionId, int versionNo, String flowName,
        String createdBy, String createTime, String changeReason) { }

    /** 整体删除回执；status 固定 DELETED。 */
    public record DeleteFlowResponseBody(String flowId, String status, String version, String deletedBy,
        String deletedTime) { }

    /** 由真实已保存来源约束的规则只读结果，不能传任意规则版本读取已删除根。 */
    public record RuleSetReferenceResponseBody(String sourceType, String sourceVersionId, String nodeKey,
        String ruleSetStatus, RuleSetDetailResponseBody ruleSet) { }

    /** 旧顺序 Point Version 的只读投影；无 Flow 身份，不接收新图保存。 */
    public record LegacyPointResponseBody(String sourceType, String pointVersionId, String pointCode,
        String pointName, int versionNo, String inputContractCode, int inputContractVersion,
        String defaultDecision, String evaluationStrategy, String failureStrategy, List<Node> nodes,
        List<Edge> edges, List<RuleSetReference> ruleSetReferences) { }
}
