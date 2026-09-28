package com.paytech.risk.api.decision;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.OperandRequestBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 决策流配置 HTTP 合同；所有 BIGINT、坐标和时间使用字符串，未生效配置不冒充运行事实。 */
public final class DecisionFlowModels {
    private DecisionFlowModels() { }

    /** 只保存业务节点和连线；不接受图形库内部 viewport/selection 状态。 */
    public static final class Graph {

        /**
         * 结构格式标识。
         * <li>固定为 1，不是 API 版本。</li>
         */
        @NotNull
        private Integer schemaVersion;

        /**
         * 配置节点。
         * <li>最多 50 个；数组顺序不表示执行顺序。</li>
         */
        @NotNull @Size(max = 50)
        private List<@Valid Node> nodes;

        /**
         * 配置连线。
         * <li>最多 100 条；执行顺序由出口类型和优先级表达。</li>
         */
        @NotNull @Size(max = 100)
        private List<@Valid Edge> edges;

        /** 供 JSON 普通属性绑定创建实例。 */
        public Graph() { }

        /** 根据完整字段构造协议对象。 */
        public Graph(Integer schemaVersion, List<Node> nodes, List<Edge> edges) {
            this.schemaVersion = schemaVersion;
            this.nodes = nodes;
            this.edges = edges;
        }

        /** 返回结构格式标识。 */
        public Integer getSchemaVersion() { return schemaVersion; }

        /** 设置结构格式标识。 */
        public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }

        /** 返回配置节点。 */
        public List<Node> getNodes() { return nodes; }

        /** 设置配置节点。 */
        public void setNodes(List<Node> nodes) { this.nodes = nodes; }

        /** 返回配置连线。 */
        public List<Edge> getEdges() { return edges; }

        /** 设置配置连线。 */
        public void setEdges(List<Edge> edges) { this.edges = edges; }
    }

    /** 坐标为有符号整数字符串，范围 -100000..100000，不接受 -0。 */
    public static final class Position {

        /**
         * 画布横坐标。
         * <li>整像素十进制字符串，范围 -100000..100000，不接受 -0。</li>
         */
        @NotNull
        private String x;

        /**
         * 画布纵坐标。
         * <li>整像素十进制字符串，范围 -100000..100000，不接受 -0。</li>
         */
        @NotNull
        private String y;

        /** 供 JSON 普通属性绑定创建实例。 */
        public Position() { }

        /** 根据完整字段构造协议对象。 */
        public Position(String x, String y) {
            this.x = x;
            this.y = y;
        }

        /** 返回画布横坐标。 */
        public String getX() { return x; }

        /** 设置画布横坐标。 */
        public void setX(String x) { this.x = x; }

        /** 返回画布纵坐标。 */
        public String getY() { return y; }

        /** 设置画布纵坐标。 */
        public void setY(String y) { this.y = y; }
    }

    /** START/RULE_SET/END 的精确配置；不适用字段必须显式为 null。 */
    public static final class Node {

        /**
         * 稳定节点键。
         * <li>区分大小写，不是数据库 ID。</li>
         */
        @NotNull
        private String nodeKey;

        /**
         * 节点类型。
         * <li>START、RULE_SET 或 END。</li>
         */
        @NotNull
        private String type;

        /**
         * 保存时的显示名称。
         * <li>1–100 个 Unicode 码点。</li>
         */
        @NotNull
        private String name;

        /**
         * 画布整数坐标。
         */
        @NotNull @Valid
        private Position position;

        /**
         * 准确规则集版本 ID。
         * <li>正 BIGINT 字符串，仅 RULE_SET 节点提供，其他节点为 null。</li>
         */
        private String ruleSetVersionId;

        /**
         * 结束决定。
         * <li>仅 END 使用 ALLOW、REVIEW 或 DENY，校验草稿可为空。</li>
         */
        private String decisionCode;

        /**
         * 拒绝原因编码。
         * <li>仅 DENY 结束节点使用。</li>
         */
        private String reasonCode;

        /**
         * 保存时的内部拒绝文案。
         * <li>仅 DENY 结束节点使用，不随字典变化回写。</li>
         */
        private String reasonMessage;

        /** 供 JSON 普通属性绑定创建实例。 */
        public Node() { }

        /** 根据完整字段构造协议对象。 */
        public Node(String nodeKey, String type, String name, Position position, String ruleSetVersionId, String decisionCode, String reasonCode, String reasonMessage) {
            this.nodeKey = nodeKey;
            this.type = type;
            this.name = name;
            this.position = position;
            this.ruleSetVersionId = ruleSetVersionId;
            this.decisionCode = decisionCode;
            this.reasonCode = reasonCode;
            this.reasonMessage = reasonMessage;
        }

        /** 返回稳定节点键。 */
        public String getNodeKey() { return nodeKey; }

        /** 设置稳定节点键。 */
        public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }

        /** 返回节点、连线或旧字段来源的类型编码。 */
        public String getType() { return type; }

        /** 设置节点、连线或旧字段来源的类型编码。 */
        public void setType(String type) { this.type = type; }

        /** 返回保存时的显示名称。 */
        public String getName() { return name; }

        /** 设置保存时的显示名称。 */
        public void setName(String name) { this.name = name; }

        /** 返回画布整数坐标。 */
        public Position getPosition() { return position; }

        /** 设置画布整数坐标。 */
        public void setPosition(Position position) { this.position = position; }

        /** 返回准确规则集版本 ID。 */
        public String getRuleSetVersionId() { return ruleSetVersionId; }

        /** 设置准确规则集版本 ID。 */
        public void setRuleSetVersionId(String ruleSetVersionId) { this.ruleSetVersionId = ruleSetVersionId; }

        /** 返回结束决定。 */
        public String getDecisionCode() { return decisionCode; }

        /** 设置结束决定。 */
        public void setDecisionCode(String decisionCode) { this.decisionCode = decisionCode; }

        /** 返回拒绝原因编码。 */
        public String getReasonCode() { return reasonCode; }

        /** 设置拒绝原因编码。 */
        public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

        /** 返回保存时的内部拒绝文案。 */
        public String getReasonMessage() { return reasonMessage; }

        /** 设置保存时的内部拒绝文案。 */
        public void setReasonMessage(String reasonMessage) { this.reasonMessage = reasonMessage; }
    }

    /** 显式出线顺序；仅 CONDITIONAL 的 condition 非空。 */
    public static final class Edge {

        /**
         * 版本内稳定连线键。
         */
        @NotNull
        private String edgeKey;

        /**
         * 来源节点键。
         */
        @NotNull
        private String sourceNodeKey;

        /**
         * 目标节点键。
         */
        @NotNull
        private String targetNodeKey;

        /**
         * 连线类型。
         * <li>UNCONDITIONAL、CONDITIONAL 或 ELSE。</li>
         */
        @NotNull
        private String type;

        /**
         * 同源出口优先级。
         * <li>条件线从 1 连续递增；ELSE 和 UNCONDITIONAL 为 0。</li>
         */
        @NotNull
        private Integer priority;

        /**
         * 保存时的显示名称。
         * <li>1–100 个 Unicode 码点。</li>
         */
        @NotNull
        private String name;

        /**
         * 连线条件。
         * <li>仅 CONDITIONAL 提供，其他类型为 null。</li>
         */
        @Valid
        private Condition condition;

        /** 供 JSON 普通属性绑定创建实例。 */
        public Edge() { }

        /** 根据完整字段构造协议对象。 */
        public Edge(String edgeKey, String sourceNodeKey, String targetNodeKey, String type, Integer priority, String name, Condition condition) {
            this.edgeKey = edgeKey;
            this.sourceNodeKey = sourceNodeKey;
            this.targetNodeKey = targetNodeKey;
            this.type = type;
            this.priority = priority;
            this.name = name;
            this.condition = condition;
        }

        /** 返回版本内稳定连线键。 */
        public String getEdgeKey() { return edgeKey; }

        /** 设置版本内稳定连线键。 */
        public void setEdgeKey(String edgeKey) { this.edgeKey = edgeKey; }

        /** 返回来源节点键。 */
        public String getSourceNodeKey() { return sourceNodeKey; }

        /** 设置来源节点键。 */
        public void setSourceNodeKey(String sourceNodeKey) { this.sourceNodeKey = sourceNodeKey; }

        /** 返回目标节点键。 */
        public String getTargetNodeKey() { return targetNodeKey; }

        /** 设置目标节点键。 */
        public void setTargetNodeKey(String targetNodeKey) { this.targetNodeKey = targetNodeKey; }

        /** 返回节点、连线或旧字段来源的类型编码。 */
        public String getType() { return type; }

        /** 设置节点、连线或旧字段来源的类型编码。 */
        public void setType(String type) { this.type = type; }

        /** 返回同源出口优先级。 */
        public Integer getPriority() { return priority; }

        /** 设置同源出口优先级。 */
        public void setPriority(Integer priority) { this.priority = priority; }

        /** 返回保存时的显示名称。 */
        public String getName() { return name; }

        /** 设置保存时的显示名称。 */
        public void setName(String name) { this.name = name; }

        /** 返回连线条件。 */
        public Condition getCondition() { return condition; }

        /** 设置连线条件。 */
        public void setCondition(Condition condition) { this.condition = condition; }
    }

    /** 条件 schemaVersion=1 读取旧历史，2 使用统一变量操作数。 */
    public static final class Condition {

        /**
         * 结构格式标识。
         * <li>1 只读旧条件；2 使用统一变量操作数。</li>
         */
        @NotNull
        private Integer schemaVersion;

        /**
         * 条件表达式根。
         */
        @NotNull @Valid
        private Expression root;

        /** 供 JSON 普通属性绑定创建实例。 */
        public Condition() { }

        /** 根据完整字段构造协议对象。 */
        public Condition(Integer schemaVersion, Expression root) {
            this.schemaVersion = schemaVersion;
            this.root = root;
        }

        /** 返回结构格式标识。 */
        public Integer getSchemaVersion() { return schemaVersion; }

        /** 设置结构格式标识。 */
        public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }

        /** 返回条件表达式根。 */
        public Expression getRoot() { return root; }

        /** 设置条件表达式根。 */
        public void setRoot(Expression root) { this.root = root; }
    }

    /** kind 是 JSON 判别属性；不同变体不接受彼此的字段。 */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({@JsonSubTypes.Type(value = GroupExpression.class, name = "GROUP"),
        @JsonSubTypes.Type(value = PredicateExpression.class, name = "PREDICATE")})
    public sealed interface Expression permits GroupExpression, PredicateExpression { }

    /** AND/OR/NOT 分组；子项顺序保留。 */
    public static final class GroupExpression implements Expression {

        /**
         * 分组或比较操作符。
         */
        @NotNull
        private String operator;

        /**
         * 分组子表达式。
         * <li>保留原顺序。</li>
         */
        @NotNull
        private List<@Valid Expression> children;

        /** 供 JSON 普通属性绑定创建实例。 */
        public GroupExpression() { }

        /** 根据完整字段构造协议对象。 */
        public GroupExpression(String operator, List<Expression> children) {
            this.operator = operator;
            this.children = children;
        }

        /** 返回分组或比较操作符。 */
        public String getOperator() { return operator; }

        /** 设置分组或比较操作符。 */
        public void setOperator(String operator) { this.operator = operator; }

        /** 返回分组子表达式。 */
        public List<Expression> getChildren() { return children; }

        /** 设置分组子表达式。 */
        public void setChildren(List<Expression> children) { this.children = children; }
    }

    /** 比较字面量只接受字符串或 Boolean；NUMBER 也必须使用十进制字符串。 */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public static final class PredicateExpression implements Expression {

        /**
         * 旧格式字段来源。
         * <li>仅条件格式 1 使用，格式 2 省略。</li>
         */
        @Valid
        private Source source;

        /**
         * 分组或比较操作符。
         */
        @NotNull
        private String operator;

        /**
         * 旧格式比较字面量。
         * <li>仅条件格式 1 使用，格式 2 省略；仅字符串或 Boolean。</li>
         */
        private List<Object> values;

        /**
         * 统一变量操作数。
         * <li>仅条件格式 2 使用，格式 1 省略。</li>
         */
        private List<@Valid OperandRequestBody> operands;

        /** 供 JSON 普通属性绑定创建实例。 */
        public PredicateExpression() { }

        /** 根据完整字段构造协议对象。 */
        public PredicateExpression(Source source, String operator, List<Object> values, List<OperandRequestBody> operands) {
            this.source = source;
            this.operator = operator;
            this.values = values;
            this.operands = operands;
        }

        /** 原条件只使用 source/values，新增条件使用 operands，传输 Reader 按版本验证互斥。 */
        public PredicateExpression(Source source, String operator, List<Object> values) { this(source, operator, values, null); }

        /** 返回旧格式字段来源。 */
        public Source getSource() { return source; }

        /** 设置旧格式字段来源。 */
        public void setSource(Source source) { this.source = source; }

        /** 返回分组或比较操作符。 */
        public String getOperator() { return operator; }

        /** 设置分组或比较操作符。 */
        public void setOperator(String operator) { this.operator = operator; }

        /** 返回旧格式比较字面量。 */
        public List<Object> getValues() { return values; }

        /** 设置旧格式比较字面量。 */
        public void setValues(List<Object> values) { this.values = values; }

        /** 返回统一变量操作数。 */
        public List<OperandRequestBody> getOperands() { return operands; }

        /** 设置统一变量操作数。 */
        public void setOperands(List<OperandRequestBody> operands) { this.operands = operands; }
    }

    /** 旧 NODE_RESULT 来源仅供历史 Reader；新变量引用不使用此结构。 */
    public static final class Source {

        /**
         * 旧格式来源类型。
         * <li>CONTEXT 或 NODE_RESULT。</li>
         */
        @NotNull
        private String type;

        /**
         * 旧格式来源字段编码。
         */
        @NotNull
        private String fieldCode;

        /** 供 JSON 普通属性绑定创建实例。 */
        public Source() { }

        /** 根据完整字段构造协议对象。 */
        public Source(String type, String fieldCode) {
            this.type = type;
            this.fieldCode = fieldCode;
        }

        /** 返回节点、连线或旧字段来源的类型编码。 */
        public String getType() { return type; }

        /** 设置节点、连线或旧字段来源的类型编码。 */
        public void setType(String type) { this.type = type; }

        /** 返回旧格式来源字段编码。 */
        public String getFieldCode() { return fieldCode; }

        /** 设置旧格式来源字段编码。 */
        public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }
    }

    /** 可用决策点由 Risk 目录返回，Web 不自行穷举。 */
    public static final class SupportedPoint {

        /** 决策点编码。 */
        private String pointCode;

        /** 决策点名称。 */
        private String pointName;

        /** 供 JSON 属性绑定使用。 */
        public SupportedPoint() { }

        /** 根据完整查询事实构造响应。 */
        public SupportedPoint(String pointCode, String pointName) {
            this.pointCode = pointCode;
            this.pointName = pointName;
        }

        /** 返回决策点编码。 */
        public String getPointCode() { return pointCode; }

        /** 设置决策点编码。 */
        public void setPointCode(String pointCode) { this.pointCode = pointCode; }

        /** 返回决策点名称。 */
        public String getPointName() { return pointName; }

        /** 设置决策点名称。 */
        public void setPointName(String pointName) { this.pointName = pointName; }
    }

    /** 当前点可用于条件的字段及输入域，不包含任何运行原值。 */
    public static final class Field {

        /** 原系统来源字段编码。 */
        private String fieldCode;

        /** 目录中的可读字段名称。 */
        private String label;

        /** 变量库定义的值类型编码。 */
        private String valueType;

        /** 来源合同是否要求此字段。 */
        private boolean required;

        /** 此值类型支持的比较运算符。 */
        private List<String> operators;

        /** 来源变量的格式约束编码。 */
        private String literalDomain;

        /** 统一变量库中的准确编码。 */
        private String variableCode;

        /** 供 JSON 属性绑定使用。 */
        public Field() { }

        /** 根据完整查询事实构造响应。 */
        public Field(String fieldCode, String label, String valueType, boolean required, List<String> operators, String literalDomain, String variableCode) {
            this.fieldCode = fieldCode;
            this.label = label;
            this.valueType = valueType;
            this.required = required;
            this.operators = operators;
            this.literalDomain = literalDomain;
            this.variableCode = variableCode;
        }

        /** 返回原系统来源字段编码。 */
        public String getFieldCode() { return fieldCode; }

        /** 设置原系统来源字段编码。 */
        public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }

        /** 返回目录中的可读字段名称。 */
        public String getLabel() { return label; }

        /** 设置目录中的可读字段名称。 */
        public void setLabel(String label) { this.label = label; }

        /** 返回变量库定义的值类型编码。 */
        public String getValueType() { return valueType; }

        /** 设置变量库定义的值类型编码。 */
        public void setValueType(String valueType) { this.valueType = valueType; }

        /** 返回来源合同是否要求此字段。 */
        public boolean getRequired() { return required; }

        /** 设置来源合同是否要求此字段。 */
        public void setRequired(boolean required) { this.required = required; }

        /** 返回此值类型支持的比较运算符。 */
        public List<String> getOperators() { return operators; }

        /** 设置此值类型支持的比较运算符。 */
        public void setOperators(List<String> operators) { this.operators = operators; }

        /** 返回来源变量的格式约束编码。 */
        public String getLiteralDomain() { return literalDomain; }

        /** 设置来源变量的格式约束编码。 */
        public void setLiteralDomain(String literalDomain) { this.literalDomain = literalDomain; }

        /** 返回统一变量库中的准确编码。 */
        public String getVariableCode() { return variableCode; }

        /** 设置统一变量库中的准确编码。 */
        public void setVariableCode(String variableCode) { this.variableCode = variableCode; }
    }

    /** 系统节点输出定义的有限兼容投影，实际选择由变量目录提供。 */
    public static final class NodeOutput {

        /** 适用的节点类型编码。 */
        private String nodeType;

        /** 原系统来源字段编码。 */
        private String fieldCode;

        /** 目录中的可读字段名称。 */
        private String label;

        /** 变量库定义的值类型编码。 */
        private String valueType;

        /** 来源合同是否要求此字段。 */
        private boolean required;

        /** 此值类型支持的比较运算符。 */
        private List<String> operators;

        /** 统一变量库中的准确编码。 */
        private String variableCode;

        /** 供 JSON 属性绑定使用。 */
        public NodeOutput() { }

        /** 根据完整查询事实构造响应。 */
        public NodeOutput(String nodeType, String fieldCode, String label, String valueType, boolean required, List<String> operators, String variableCode) {
            this.nodeType = nodeType;
            this.fieldCode = fieldCode;
            this.label = label;
            this.valueType = valueType;
            this.required = required;
            this.operators = operators;
            this.variableCode = variableCode;
        }

        /** 返回适用的节点类型编码。 */
        public String getNodeType() { return nodeType; }

        /** 设置适用的节点类型编码。 */
        public void setNodeType(String nodeType) { this.nodeType = nodeType; }

        /** 返回原系统来源字段编码。 */
        public String getFieldCode() { return fieldCode; }

        /** 设置原系统来源字段编码。 */
        public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }

        /** 返回目录中的可读字段名称。 */
        public String getLabel() { return label; }

        /** 设置目录中的可读字段名称。 */
        public void setLabel(String label) { this.label = label; }

        /** 返回变量库定义的值类型编码。 */
        public String getValueType() { return valueType; }

        /** 设置变量库定义的值类型编码。 */
        public void setValueType(String valueType) { this.valueType = valueType; }

        /** 返回来源合同是否要求此字段。 */
        public boolean getRequired() { return required; }

        /** 设置来源合同是否要求此字段。 */
        public void setRequired(boolean required) { this.required = required; }

        /** 返回此值类型支持的比较运算符。 */
        public List<String> getOperators() { return operators; }

        /** 设置此值类型支持的比较运算符。 */
        public void setOperators(List<String> operators) { this.operators = operators; }

        /** 返回统一变量库中的准确编码。 */
        public String getVariableCode() { return variableCode; }

        /** 设置统一变量库中的准确编码。 */
        public void setVariableCode(String variableCode) { this.variableCode = variableCode; }
    }

    /** Body/图/单条件树的同时生效上限，字节数均为 UTF-8。 */
    public static final class Limits {

        /** 请求正文 UTF-8 字节数上限。 */
        private int bodyBytes;

        /** 完整图的节点数上限。 */
        private int nodes;

        /** 完整图的连线数上限。 */
        private int edges;

        /** 单个条件 UTF-8 字节数上限。 */
        private int conditionBytes;

        /** 单个条件树节点数上限。 */
        private int conditionNodes;

        /** 单个条件树深度上限。 */
        private int conditionDepth;

        /** 供 JSON 属性绑定使用。 */
        public Limits() { }

        /** 根据完整查询事实构造响应。 */
        public Limits(int bodyBytes, int nodes, int edges, int conditionBytes, int conditionNodes, int conditionDepth) {
            this.bodyBytes = bodyBytes;
            this.nodes = nodes;
            this.edges = edges;
            this.conditionBytes = conditionBytes;
            this.conditionNodes = conditionNodes;
            this.conditionDepth = conditionDepth;
        }

        /** 返回请求正文 UTF-8 字节数上限。 */
        public int getBodyBytes() { return bodyBytes; }

        /** 设置请求正文 UTF-8 字节数上限。 */
        public void setBodyBytes(int bodyBytes) { this.bodyBytes = bodyBytes; }

        /** 返回按原绑定顺序投影的只读节点。 */
        public int getNodes() { return nodes; }

        /** 设置按原绑定顺序投影的只读节点。 */
        public void setNodes(int nodes) { this.nodes = nodes; }

        /** 返回按原绑定顺序投影的只读连线。 */
        public int getEdges() { return edges; }

        /** 设置按原绑定顺序投影的只读连线。 */
        public void setEdges(int edges) { this.edges = edges; }

        /** 返回单个条件 UTF-8 字节数上限。 */
        public int getConditionBytes() { return conditionBytes; }

        /** 设置单个条件 UTF-8 字节数上限。 */
        public void setConditionBytes(int conditionBytes) { this.conditionBytes = conditionBytes; }

        /** 返回单个条件树节点数上限。 */
        public int getConditionNodes() { return conditionNodes; }

        /** 设置单个条件树节点数上限。 */
        public void setConditionNodes(int conditionNodes) { this.conditionNodes = conditionNodes; }

        /** 返回单个条件树深度上限。 */
        public int getConditionDepth() { return conditionDepth; }

        /** 设置单个条件树深度上限。 */
        public void setConditionDepth(int conditionDepth) { this.conditionDepth = conditionDepth; }
    }

    /** 可定位的静态问题，未关联坐标为 null。 */
    public static final class Issue {

        /**
         * 稳定问题代码。
         */
        private String code;

        /**
         * 稳定节点键。
         * <li>区分大小写，不是数据库 ID。</li>
         */
        private String nodeKey;

        /**
         * 版本内稳定连线键。
         */
        private String edgeKey;

        /**
         * 请求字段路径。
         * <li>无关联路径时为 null。</li>
         */
        private String field;

        /**
         * 安全的问题说明。
         */
        private String message;

        /** 供 JSON 普通属性绑定创建实例。 */
        public Issue() { }

        /** 根据完整字段构造协议对象。 */
        public Issue(String code, String nodeKey, String edgeKey, String field, String message) {
            this.code = code;
            this.nodeKey = nodeKey;
            this.edgeKey = edgeKey;
            this.field = field;
            this.message = message;
        }

        /** 返回稳定问题代码。 */
        public String getCode() { return code; }

        /** 设置稳定问题代码。 */
        public void setCode(String code) { this.code = code; }

        /** 返回稳定节点键。 */
        public String getNodeKey() { return nodeKey; }

        /** 设置稳定节点键。 */
        public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }

        /** 返回版本内稳定连线键。 */
        public String getEdgeKey() { return edgeKey; }

        /** 设置版本内稳定连线键。 */
        public void setEdgeKey(String edgeKey) { this.edgeKey = edgeKey; }

        /** 返回请求字段路径。 */
        public String getField() { return field; }

        /** 设置请求字段路径。 */
        public void setField(String field) { this.field = field; }

        /** 返回安全的问题说明。 */
        public String getMessage() { return message; }

        /** 设置安全的问题说明。 */
        public void setMessage(String message) { this.message = message; }
    }

    /** 引用节点的准确规则版本和当前资格，不新增规则处理模式或分类计数。 */
    public static final class RuleSetReference {

        /** 来源版本内的稳定节点键。 */
        private String nodeKey;

        /** 规则集管理根 ID。 */
        private String ruleSetId;

        /** 规则集不可变编码。 */
        private String ruleSetCode;

        /** 准确规则版本的名称。 */
        private String ruleSetName;

        /** 准确规则版本 ID。 */
        private String ruleSetVersionId;

        /** 所选版本的业务版本号。 */
        private int versionNo;

        /** 准确规则版本中的规则数量。 */
        private int ruleCount;

        /** 规则集当前管理状态，CONFIGURABLE 或 DELETED。 */
        private String ruleSetStatus;

        /** 准确规则版本状态，DRAFT、FROZEN 或 RETIRED。 */
        private String versionStatus;

        /** 供 JSON 属性绑定使用。 */
        public RuleSetReference() { }

        /** 根据完整查询事实构造响应。 */
        public RuleSetReference(String nodeKey, String ruleSetId, String ruleSetCode, String ruleSetName, String ruleSetVersionId, int versionNo, int ruleCount, String ruleSetStatus, String versionStatus) {
            this.nodeKey = nodeKey;
            this.ruleSetId = ruleSetId;
            this.ruleSetCode = ruleSetCode;
            this.ruleSetName = ruleSetName;
            this.ruleSetVersionId = ruleSetVersionId;
            this.versionNo = versionNo;
            this.ruleCount = ruleCount;
            this.ruleSetStatus = ruleSetStatus;
            this.versionStatus = versionStatus;
        }

        /** 返回来源版本内的稳定节点键。 */
        public String getNodeKey() { return nodeKey; }

        /** 设置来源版本内的稳定节点键。 */
        public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }

        /** 返回规则集管理根 ID。 */
        public String getRuleSetId() { return ruleSetId; }

        /** 设置规则集管理根 ID。 */
        public void setRuleSetId(String ruleSetId) { this.ruleSetId = ruleSetId; }

        /** 返回规则集不可变编码。 */
        public String getRuleSetCode() { return ruleSetCode; }

        /** 设置规则集不可变编码。 */
        public void setRuleSetCode(String ruleSetCode) { this.ruleSetCode = ruleSetCode; }

        /** 返回准确规则版本的名称。 */
        public String getRuleSetName() { return ruleSetName; }

        /** 设置准确规则版本的名称。 */
        public void setRuleSetName(String ruleSetName) { this.ruleSetName = ruleSetName; }

        /** 返回准确规则版本 ID。 */
        public String getRuleSetVersionId() { return ruleSetVersionId; }

        /** 设置准确规则版本 ID。 */
        public void setRuleSetVersionId(String ruleSetVersionId) { this.ruleSetVersionId = ruleSetVersionId; }

        /** 返回所选版本的业务版本号。 */
        public int getVersionNo() { return versionNo; }

        /** 设置所选版本的业务版本号。 */
        public void setVersionNo(int versionNo) { this.versionNo = versionNo; }

        /** 返回准确规则版本中的规则数量。 */
        public int getRuleCount() { return ruleCount; }

        /** 设置准确规则版本中的规则数量。 */
        public void setRuleCount(int ruleCount) { this.ruleCount = ruleCount; }

        /** 返回规则集当前管理状态，CONFIGURABLE 或 DELETED。 */
        public String getRuleSetStatus() { return ruleSetStatus; }

        /** 设置规则集当前管理状态，CONFIGURABLE 或 DELETED。 */
        public void setRuleSetStatus(String ruleSetStatus) { this.ruleSetStatus = ruleSetStatus; }

        /** 返回准确规则版本状态，DRAFT、FROZEN 或 RETIRED。 */
        public String getVersionStatus() { return versionStatus; }

        /** 设置准确规则版本状态，DRAFT、FROZEN 或 RETIRED。 */
        public void setVersionStatus(String versionStatus) { this.versionStatus = versionStatus; }
    }
}
