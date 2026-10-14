package com.paytech.risk.ext.rest.decision;

import static com.paytech.risk.ext.rest.decision.DecisionWireValues.*;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.ActionParameter;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.ActionParameterDescriptor;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.AdditionalAction;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.ConditionNodeRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.DecisionActionDescriptorResponseBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.DecisionInputFieldDescriptorResponseBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.DecisionPointDescriptorResponseBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.OperandRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleActionRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.TargetBindingDescriptor;
import com.paytech.risk.api.decision.RiskVariableModels;
import com.paytech.risk.api.decision.request.CreateRuleSetVersionRequestBody;
import com.paytech.risk.api.decision.response.CreateRuleSetVersionResponseBody;
import com.paytech.risk.api.decision.response.DeleteRuleSetResponseBody;
import com.paytech.risk.api.decision.response.FreezeRuleSetVersionResponseBody;
import com.paytech.risk.api.decision.response.RuleAuthoringContextResponseBody;
import com.paytech.risk.api.decision.response.RuleSetDetailResponseBody;
import com.paytech.risk.api.decision.response.RuleSetListItemResponseBody;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor.DecisionActionDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor.InputFieldDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.ActionKey;
import com.paytech.risk.decision.domain.authoring.RuleActionConfigurationCatalog;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.GroupOperator;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.decision.domain.rule.ChangeReasonPolicy;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.NodeView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.OperandView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.RuleView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.BooleanLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.LiteralValue;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NodeDraft;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NullLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.OperandDraft;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.RuleDraft;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.RuleSetDraft;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.StringLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidator;
import com.paytech.risk.decision.domain.variable.DecisionSourceContracts;
import com.paytech.risk.decision.domain.variable.RiskVariable;
import com.paytech.risk.decision.domain.variable.VariableOperand;
import com.paytech.risk.decision.domain.variable.VariableRuleConfiguration;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.CreatedRuleSet;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.FrozenRuleSet;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.RuleAuthoringView;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.RuleSetPage;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.RuleSetSummary;
import java.util.Comparator;
import java.util.List;

/** Rule Set 与操作数领域模型和 HTTP 模型之间的协议转换，不查询数据库或执行业务规则。 */
final class DecisionRuleSetWireConverter {
    /** 仅提供固定边界转换。 */
    private DecisionRuleSetWireConverter() {
    }

    /** 将创建版本 Body 转换为规则集草稿，基础版本 ID 按正整数解析。 */
    static RuleSetDraft toDomain(CreateRuleSetVersionRequestBody value) {
        if (value == null) {
            throw invalid("requestBody");
        }
        return new RuleSetDraft(value.getPointCode(), value.getRuleSetCode(), value.getRuleSetName(),
            value.getBaseRuleSetVersionId() == null ? null : parsePositiveId(value.getBaseRuleSetVersionId()),
            value.getBaseContentHash(), ChangeReasonPolicy.normalize(value.getChangeReason()),
            value.getRules().stream().map(DecisionRuleSetWireConverter::toDomain).toList(), value.getOperandFormat());
    }

    /** 转换单条规则及其条件节点与附加动作。 */
    private static RuleDraft toDomain(RuleRequestBody value) {
        return new RuleDraft(value.getRuleCode(), value.getRuleName(), value.getPriorityNo(), value.getEnabledFlag(),
            value.getReasonCode(), value.getConditionNodes().stream().map(DecisionRuleSetWireConverter::toDomain).toList(),
            value.getAction() == null ? null : new ActionKey(value.getAction().getActionCode(), value.getAction().getActionVersionNo()),
            value.getReasonMessage(), value.getAdditionalActions() == null ? null : value.getAdditionalActions().stream()
                .map(DecisionRuleSetWireConverter::toDomain).toList());
    }

    /** 转换附加动作的目标绑定与主体变量。 */
    private static RuleAstValidator.AdditionalAction toDomain(AdditionalAction action) {
        return new RuleAstValidator.AdditionalAction(action.getSequenceNo(), new ActionKey(action.getActionCode(), action.getActionVersionNo()),
            action.getTargetBindingCode(), action.getParameters().stream().map(parameter -> new RuleAstValidator.ActionParameter(
                parameter.getParameterCode(), parameter.getValueSequence(), parseOperandSource(parameter.getSourceType()), parameter.getFieldCode(),
                parseValueType(parameter.getValueType()), parameter.getLiteralValue() == null ? NullLiteral.INSTANCE
                    : parameter.getLiteralValue() instanceof Boolean bool ? new BooleanLiteral(bool)
                    : new StringLiteral((String) parameter.getLiteralValue()))).toList(), action.getSubjectVariableCode());
    }

    /** 转换条件节点，节点类型与运算符按准确 Code 解析。 */
    private static NodeDraft toDomain(ConditionNodeRequestBody value) {
        return new NodeDraft(value.getNodeKey(), value.getParentNodeKey(), value.getSequenceNo(),
            parseNodeType(value.getNodeType()), parseGroupOperator(value.getGroupOperator()),
            parseComparisonOperator(value.getComparisonOperator()),
            value.getOperands().stream().map(DecisionRuleSetWireConverter::toDomain).toList());
    }

    /** 转换统一操作数；流程条件复用同一解析。 */
    static OperandDraft toDomain(OperandRequestBody value) {
        if (value == null) {
            throw invalid("operand");
        }
        LiteralValue literal = value.getLiteralValue() == null ? NullLiteral.INSTANCE
            : value.getLiteralValue() instanceof Boolean booleanValue ? new BooleanLiteral(booleanValue)
            : value.getLiteralValue() instanceof String stringValue ? new StringLiteral(stringValue)
            : throwInvalidLiteral();
        return new OperandDraft(parseOperandRole(value.getOperandRole()), value.getValueSequence(),
            parseOperandSource(value.getSourceType()), value.getFieldCode(), parseValueType(value.getValueType()), literal,
            value.getVariableCode(), value.getNumericUnit() == null ? null : NumericUnit.valueOf(value.getNumericUnit()), value.getCurrencyCode(), value.getCurrencyVariableCode());
    }

    /** 将规则编排能力转换为 Authoring Context 响应。 */
    static RuleAuthoringContextResponseBody toResponse(RuleAuthoringView context) {
        DecisionPointAuthoringDescriptor descriptor = require(context, "authoringContext").descriptor();
        var point = descriptor.point();
        return new RuleAuthoringContextResponseBody("RISK_VARIABLE_AUTHORING",
            new DecisionPointDescriptorResponseBody(point.pointCode(), point.pointName(), point.businessDomain(),
                point.inputContractCode(), point.inputContractVersion(), point.outputContractCode(),
                point.defaultDecision().getCode(), point.evaluationStrategy().getCode(),
                point.failureStrategy().getCode(), point.timeoutMillis(), point.status().getCode()),
            context.systemDefinitions().stream().filter(e -> e.definition().sourceType() == VariableSource.INPUT)
                .sorted(Comparator.comparing(e -> e.definition().sourceField()))
                .map(DecisionRuleSetWireConverter::systemInput).toList(),
            descriptor.operators().stream().map(ComparisonOperator::getCode).toList(),
            java.util.stream.Stream.concat(descriptor.actions().stream(), RuleActionConfigurationCatalog.actions().stream()
                .map(RuleActionConfigurationCatalog.ConfigurationAction::descriptor)).sorted(Comparator.comparing(DecisionActionDescriptor::actionCode)
                .thenComparingInt(DecisionActionDescriptor::versionNo))
                .map(DecisionRuleSetWireConverter::toResponse).toList(),
            List.of(), parameterDescriptors(), List.of(), List.of(), RuleActionConfigurationCatalog.targetBindings().stream()
                .map(target -> new TargetBindingDescriptor(target.bindingCode(), target.displayName(), target.subjectType(),
                    List.of(), target.subjectType() + "_ID")).toList(), VariableRuleConfiguration.OPERAND_FORMAT,
                new RiskVariableModels.VariableQueryContext(point.pointCode(), point.inputContractCode(), point.inputContractVersion(), "RULE_CONDITION"));
    }

    /** 兼容字段仅投影数据库登记的系统变量，不生成新保存候选。 */
    private static DecisionInputFieldDescriptorResponseBody systemInput(RiskVariable.Entry entry) {
        var d = entry.definition();
        var source = DecisionSourceContracts.find(d);
        var field = source.fields().stream().filter(f -> f.fieldKey().equals(d.sourceField())).findFirst().orElseThrow();
        return new DecisionInputFieldDescriptorResponseBody(d.sourceField(), d.valueType().getCode(), field.required(),
            field.dataClassification().getCode(), d.usages().contains(VariableUsage.RULE_CONDITION),
            d.usages().contains(VariableUsage.ACTION_PARAMETER), d.variableCode());
    }

    /** 动作参数的固定描述，供编排页面展示。 */
    private static List<ActionParameterDescriptor> parameterDescriptors() {
        return RuleActionConfigurationCatalog.actions().stream().flatMap(action -> java.util.stream.Stream.of(
            new ActionParameterDescriptor(action.descriptor().actionCode(), action.descriptor().versionNo(), "SUBJECT_ID", "STRING",
                true, "VARIABLE", true, 1, null, null, null),
            new ActionParameterDescriptor(action.descriptor().actionCode(), action.descriptor().versionNo(), action.parameterCode(),
                action.valueType().getCode(), true, "LITERAL", false, action.maxValues(),
                action.valueType() == DecisionValueType.DURATION ? "1" : null,
                action.valueType() == DecisionValueType.DURATION ? Long.toString(RuleActionConfigurationCatalog.MAX_DURATION_MILLIS) : null,
                action.valueType() == DecisionValueType.STRING ? RuleActionConfigurationCatalog.CODE_PATTERN : null))).toList();
    }

    /** 输入字段描述投影。 */
    private static DecisionInputFieldDescriptorResponseBody toResponse(InputFieldDescriptor field) {
        return new DecisionInputFieldDescriptorResponseBody(field.fieldCode(), field.valueType().getCode(),
            field.requiredFlag(), field.dataClassification().getCode(), field.ruleReferenceFlag(),
            field.actionParameterFlag());
    }

    /** 动作描述投影。 */
    private static DecisionActionDescriptorResponseBody toResponse(DecisionActionDescriptor action) {
        return new DecisionActionDescriptorResponseBody(action.actionCode(), action.actionName(),
            action.actionCategory().getCode(), action.versionNo(), action.handlerCode(),
            action.implementationVersion(), action.implementationHash(), action.terminalFlag(),
            nullableCode(action.decisionCode()), action.mergePolicy().getCode(), action.shadowSupportedFlag(),
            action.status().getCode());
    }

    /** 规则集分页结果转换为公共分页响应。 */
    static PagingResponseBody<RuleSetListItemResponseBody> toResponse(RuleSetPage page) {
        RuleSetPage value = require(page, "ruleSetPage");
        return new PagingResponseBody<>(value.items().stream().map(DecisionRuleSetWireConverter::toResponse).toList(),
            value.page(), value.size(), value.total());
    }

    /** 单个规则集列表项投影。 */
    private static RuleSetListItemResponseBody toResponse(RuleSetSummary value) {
        return new RuleSetListItemResponseBody(positiveId(value.ruleSetVersionId()),
            nullableId(value.pointVersionId()), value.pointCode(), value.ruleSetCode(), value.ruleSetName(),
            value.versionNo(), code(value.status()), value.contentHash(), positiveId(value.createdBy()),
            value.changeReason(), time(value.createTime()), nullableId(value.frozenBy()), nullableTime(value.frozenTime()),
            positiveId(value.ruleSetId()), positiveId(value.managementVersion()), value.inUse());
    }

    /** 转换不可变删除回执，所有 ID/版本使用十进制字符串。 */
    static com.paytech.risk.api.decision.response.DeleteRuleSetResponseBody toResponse(
        com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.DeletedRuleSet value) {
        return new com.paytech.risk.api.decision.response.DeleteRuleSetResponseBody(
            positiveId(value.ruleSetId()), "DELETED", positiveId(value.version()), positiveId(value.deletedBy()),
            time(value.deletedTime()));
    }

    /** 不带当前变量投影的规则集详情。 */
    static RuleSetDetailResponseBody toResponse(DecisionRuleSetAggregate aggregate) {
        return toResponse(aggregate, null);
    }

    /** 当前值只作为查询附加信息，不参与持久化内容或请求回执。 */
    static RuleSetDetailResponseBody toResponse(DecisionRuleSetAggregate aggregate, RiskVariable.Catalog catalog) {
        DecisionRuleSetAggregate value = require(aggregate, "ruleSetAggregate");
        var root = value.version();
        return new RuleSetDetailResponseBody(positiveId(root.ruleSetVersionId()),
            nullableId(value.companionId()), value.point().pointCode(), root.ruleSetCode(),
            root.ruleSetName(), root.versionNo(), code(root.status()), root.contentHash(),
            positiveId(root.createdBy()), root.changeReason(), time(root.createTime()), nullableId(root.frozenBy()),
            nullableTime(root.frozenTime()), value.rules().stream().map(DecisionRuleSetWireConverter::toResponse).toList(),
            VariableRuleConfiguration.CONTENT_FORMAT.equals(value.ruleSetProjection().contentFormat()) ? VariableRuleConfiguration.OPERAND_FORMAT : "LEGACY",
            catalog == null ? List.of() : catalog.variables().values().stream().sorted(Comparator.comparing(v -> v.entry().definition().variableCode()))
                .map(RiskVariableWireConverter::summary).toList(), catalog == null ? null : time(catalog.valuesAsOf()),
            catalog == null ? List.of() : catalog.issues().stream().map(i -> new RiskVariableModels.VariableIssue(i.variableCode(), i.code(), i.message())).toList());
    }

    /** 规则按原格式还原为请求结构。 */
    private static RuleRequestBody toResponse(RuleView rule) {
        return new RuleRequestBody(rule.ruleCode(), rule.ruleName(), rule.priorityNo(), rule.enabledFlag(),
            rule.reasonCode(), rule.conditionNodes().stream().map(DecisionRuleSetWireConverter::toResponse).toList(),
            rule.action() == null ? null : new RuleActionRequestBody(rule.action().action().actionCode(), rule.action().action().versionNo()),
            rule.reasonMessage(), rule.additionalActions().stream().map(value -> {
                var action = value.configuration();
                return new AdditionalAction(action.sequenceNo(), action.action().actionCode(), action.action().versionNo(),
                    action.targetBindingCode(), action.parameters().stream().map(parameter -> new ActionParameter(
                        parameter.parameterCode(), parameter.valueSequence(), parameter.sourceType().getCode(), parameter.fieldCode(),
                        parameter.valueType().getCode(), ((StringLiteral) parameter.literalValue()).value())).toList(), action.subjectVariableCode());
            }).toList());
    }

    /** 条件节点按原格式还原。 */
    private static ConditionNodeRequestBody toResponse(NodeView node) {
        return new ConditionNodeRequestBody(positiveId(node.nodeId()), nullableId(node.parentNodeId()),
            node.sequenceNo(), code(node.nodeType()), nullableCode(node.groupOperator()),
            nullableCode(node.comparisonOperator()), node.operands().stream().map(DecisionRuleSetWireConverter::toResponse)
            .toList());
    }

    /** 操作数按原格式还原，字面值按类型输出。 */
    private static OperandRequestBody toResponse(OperandView operand) {
        return new OperandRequestBody(code(operand.role()), operand.valueSequence(), code(operand.source()),
            operand.fieldCode(), code(operand.valueType()), literal(operand.literalValue()), operand.variableCode(),
            nullableCode(operand.numericUnit()), operand.currencyCode(), operand.currencyVariableCode());
    }

    /** 两个编辑器使用同一种操作数 Wire。 */
    static OperandRequestBody toResponse(VariableOperand operand) {
        return new OperandRequestBody(code(operand.operandRole()), operand.valueSequence(), code(operand.sourceType()),
            operand.fieldCode(), code(operand.valueType()), literal(operand.literalValue()), operand.variableCode(),
            nullableCode(operand.numericUnit()), operand.currencyCode(), operand.currencyVariableCode());
    }

    /** 创建版本结果投影。 */
    static CreateRuleSetVersionResponseBody toResponse(CreatedRuleSet value) {
        CreatedRuleSet created = require(value, "createdRuleSet");
        return new CreateRuleSetVersionResponseBody(positiveId(created.ruleSetVersionId()),
            nullableId(created.pointVersionId()), created.ruleSetCode(), created.versionNo(), code(created.status()),
            created.contentHash(), positiveId(created.createdBy()), time(created.createTime()));
    }

    /** 冻结版本结果投影。 */
    static FreezeRuleSetVersionResponseBody toResponse(FrozenRuleSet value) {
        FrozenRuleSet frozen = require(value, "frozenRuleSet");
        return new FreezeRuleSetVersionResponseBody(positiveId(frozen.ruleSetVersionId()),
            nullableId(frozen.pointVersionId()), frozen.ruleSetCode(), frozen.versionNo(), code(frozen.status()),
            frozen.contentHash(), positiveId(frozen.frozenBy()), time(frozen.frozenTime()));
    }

    /** 解析列表状态筛选，空值表示不筛选，未知 Code 拒绝。 */
    static RuleSetVersionStatus parseRuleSetStatus(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "DRAFT" -> RuleSetVersionStatus.DRAFT;
            case "FROZEN" -> RuleSetVersionStatus.FROZEN;
            case "RETIRED" -> RuleSetVersionStatus.RETIRED;
            default -> throw invalid("status");
        };
    }

    /** 解析正 BIGINT 十进制字符串，非法输入按参数错误拒绝。 */
    private static long parsePositiveId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw invalid("resourceId");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw invalid("resourceId");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid("resourceId");
        }
    }

    /** 按字面值类型输出 JSON 标量。 */
    private static Object literal(LiteralValue value) {
        if (value == null || value == NullLiteral.INSTANCE) {
            return null;
        }
        if (value instanceof StringLiteral stringValue) {
            return stringValue.value();
        }
        if (value instanceof BooleanLiteral booleanValue) {
            return booleanValue.value();
        }
        throw new ErrorException("内部 Literal 类型无效");
    }

    /** 字面值缺失或类型不符时拒绝。 */
    private static LiteralValue throwInvalidLiteral() {
        throw invalid("literalValue");
    }

    /** 解析条件节点类型 Code。 */
    private static NodeType parseNodeType(String value) {
        return switch (value) {
            case "GROUP" -> NodeType.GROUP;
            case "PREDICATE" -> NodeType.PREDICATE;
            default -> throw invalid("nodeType");
        };
    }

    /** 解析条件组运算符 Code。 */
    private static GroupOperator parseGroupOperator(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "AND" -> GroupOperator.AND;
            case "OR" -> GroupOperator.OR;
            case "NOT" -> GroupOperator.NOT;
            default -> throw invalid("groupOperator");
        };
    }

    /** 解析比较运算符 Code。 */
    private static ComparisonOperator parseComparisonOperator(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "EQ" -> ComparisonOperator.EQ;
            case "NE" -> ComparisonOperator.NE;
            case "GT" -> ComparisonOperator.GT;
            case "GTE" -> ComparisonOperator.GTE;
            case "LT" -> ComparisonOperator.LT;
            case "LTE" -> ComparisonOperator.LTE;
            case "IN" -> ComparisonOperator.IN;
            case "NOT_IN" -> ComparisonOperator.NOT_IN;
            case "BETWEEN" -> ComparisonOperator.BETWEEN;
            case "IS_NULL" -> ComparisonOperator.IS_NULL;
            case "IS_NOT_NULL" -> ComparisonOperator.IS_NOT_NULL;
            default -> throw invalid("comparisonOperator");
        };
    }

    /** 解析操作数角色 Code。 */
    private static OperandRole parseOperandRole(String value) {
        return switch (value) {
            case "LEFT" -> OperandRole.LEFT;
            case "RIGHT" -> OperandRole.RIGHT;
            default -> throw invalid("operandRole");
        };
    }

    /** 解析操作数来源 Code。 */
    private static OperandSource parseOperandSource(String value) {
        return switch (value) {
            case "CONTEXT" -> OperandSource.CONTEXT;
            case "VARIABLE" -> OperandSource.VARIABLE;
            case "LITERAL" -> OperandSource.LITERAL;
            default -> throw invalid("sourceType");
        };
    }

    /** 解析值类型 Code。 */
    private static DecisionValueType parseValueType(String value) {
        return switch (value) {
            case "STRING" -> DecisionValueType.STRING;
            case "NUMBER" -> DecisionValueType.NUMBER;
            case "BOOLEAN" -> DecisionValueType.BOOLEAN;
            case "DATETIME" -> DecisionValueType.DATETIME;
            case "DURATION" -> DecisionValueType.DURATION;
            default -> throw invalid("valueType");
        };
    }
}
