package com.paytech.risk.ext.rest.decision;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.api.decision.DecisionReleaseManagementModels.FrozenCompositionResponseBody;
import com.paytech.risk.api.decision.DecisionReleaseManagementModels.InitialStateChangeResponseBody;
import com.paytech.risk.api.decision.DecisionReleaseManagementModels.ReleaseStateChangeResponseBody;
import com.paytech.risk.api.decision.DecisionReleaseManagementModels.ReleaseStaticCheckResponseBody;
import com.paytech.risk.api.decision.DecisionReleaseManagementModels.ValidationCheckResponseBody;
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
import com.paytech.risk.api.decision.response.CreateTestReleaseResponseBody;
import com.paytech.risk.api.decision.response.DeleteRuleSetResponseBody;
import com.paytech.risk.api.decision.response.FreezeRuleSetVersionResponseBody;
import com.paytech.risk.api.decision.response.RuleAuthoringContextResponseBody;
import com.paytech.risk.api.decision.response.RuleSetDetailResponseBody;
import com.paytech.risk.api.decision.response.RuleSetListItemResponseBody;
import com.paytech.risk.api.decision.response.TestReleaseDetailResponseBody;
import com.paytech.risk.api.decision.response.TestReleaseListItemResponseBody;
import com.paytech.risk.api.decision.response.ValidateTestReleaseResponseBody;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor.DecisionActionDescriptor;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor.InputFieldDescriptor;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.ActionKey;
import com.paytech.risk.decision.domain.authoring.RuleActionConfigurationCatalog;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.GroupOperator;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate.ReleaseStateChange;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate.StaticCheckEvidence;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.CreatedCandidate;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.ReleasePageView;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.ReleaseSummaryView;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.ValidatedCandidate;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticCheckResult;
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

/** Risk Decision domain 与稳定 HTTP record 之间的唯一协议适配器。 */
final class RiskDecisionWireConverter {

    private static final DateTimeFormatter MICROSECOND_UTC = DateTimeFormatter
        .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
        .withZone(ZoneOffset.UTC);

    private RiskDecisionWireConverter() {
    }

    static RuleSetDraft toDomain(CreateRuleSetVersionRequestBody value) {
        if (value == null) {
            throw invalid("requestBody");
        }
        return new RuleSetDraft(value.getPointCode(), value.getRuleSetCode(), value.getRuleSetName(),
            value.getBaseRuleSetVersionId() == null ? null : parsePositiveId(value.getBaseRuleSetVersionId()),
            value.getBaseContentHash(), ChangeReasonPolicy.normalize(value.getChangeReason()),
            value.getRules().stream().map(RiskDecisionWireConverter::toDomain).toList(), value.getOperandFormat());
    }

    private static RuleDraft toDomain(RuleRequestBody value) {
        return new RuleDraft(value.getRuleCode(), value.getRuleName(), value.getPriorityNo(), value.getEnabledFlag(),
            value.getReasonCode(), value.getConditionNodes().stream().map(RiskDecisionWireConverter::toDomain).toList(),
            value.getAction() == null ? null : new ActionKey(value.getAction().getActionCode(), value.getAction().getActionVersionNo()),
            value.getReasonMessage(), value.getAdditionalActions() == null ? null : value.getAdditionalActions().stream()
                .map(RiskDecisionWireConverter::toDomain).toList());
    }

    private static RuleAstValidator.AdditionalAction toDomain(AdditionalAction action) {
        return new RuleAstValidator.AdditionalAction(action.getSequenceNo(), new ActionKey(action.getActionCode(), action.getActionVersionNo()),
            action.getTargetBindingCode(), action.getParameters().stream().map(parameter -> new RuleAstValidator.ActionParameter(
                parameter.getParameterCode(), parameter.getValueSequence(), parseOperandSource(parameter.getSourceType()), parameter.getFieldCode(),
                parseValueType(parameter.getValueType()), parameter.getLiteralValue() == null ? NullLiteral.INSTANCE
                    : parameter.getLiteralValue() instanceof Boolean bool ? new BooleanLiteral(bool)
                    : new StringLiteral((String) parameter.getLiteralValue()))).toList(), action.getSubjectVariableCode());
    }

    private static NodeDraft toDomain(ConditionNodeRequestBody value) {
        return new NodeDraft(value.getNodeKey(), value.getParentNodeKey(), value.getSequenceNo(),
            parseNodeType(value.getNodeType()), parseGroupOperator(value.getGroupOperator()),
            parseComparisonOperator(value.getComparisonOperator()),
            value.getOperands().stream().map(RiskDecisionWireConverter::toDomain).toList());
    }

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

    static RuleAuthoringContextResponseBody toResponse(RuleAuthoringView context) {
        CheckoutPrepareAuthoringDescriptor descriptor = require(context, "authoringContext").descriptor();
        var point = descriptor.point();
        return new RuleAuthoringContextResponseBody("RISK_VARIABLE_AUTHORING",
            new DecisionPointDescriptorResponseBody(point.pointCode(), point.pointName(), point.businessDomain(),
                point.inputContractCode(), point.inputContractVersion(), point.outputContractCode(),
                point.defaultDecision().getCode(), point.evaluationStrategy().getCode(),
                point.failureStrategy().getCode(), point.timeoutMillis(), point.status().getCode()),
            context.systemDefinitions().stream().filter(e -> e.definition().sourceType() == VariableSource.INPUT)
                .sorted(Comparator.comparing(e -> e.definition().sourceField()))
                .map(RiskDecisionWireConverter::systemInput).toList(),
            descriptor.operators().stream().map(ComparisonOperator::getCode).toList(),
            java.util.stream.Stream.concat(descriptor.actions().stream(), RuleActionConfigurationCatalog.actions().stream()
                .map(RuleActionConfigurationCatalog.ConfigurationAction::descriptor)).sorted(Comparator.comparing(DecisionActionDescriptor::actionCode)
                .thenComparingInt(DecisionActionDescriptor::versionNo))
                .map(RiskDecisionWireConverter::toResponse).toList(),
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

    private static DecisionInputFieldDescriptorResponseBody toResponse(InputFieldDescriptor field) {
        return new DecisionInputFieldDescriptorResponseBody(field.fieldCode(), field.valueType().getCode(),
            field.requiredFlag(), field.dataClassification().getCode(), field.ruleReferenceFlag(),
            field.actionParameterFlag());
    }

    private static DecisionActionDescriptorResponseBody toResponse(DecisionActionDescriptor action) {
        return new DecisionActionDescriptorResponseBody(action.actionCode(), action.actionName(),
            action.actionCategory().getCode(), action.versionNo(), action.handlerCode(),
            action.implementationVersion(), action.implementationHash(), action.terminalFlag(),
            nullableCode(action.decisionCode()), action.mergePolicy().getCode(), action.shadowSupportedFlag(),
            action.status().getCode());
    }

    static PagingResponseBody<RuleSetListItemResponseBody> toResponse(RuleSetPage page) {
        RuleSetPage value = require(page, "ruleSetPage");
        return new PagingResponseBody<>(value.items().stream().map(RiskDecisionWireConverter::toResponse).toList(),
            value.page(), value.size(), value.total());
    }

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
            nullableTime(root.frozenTime()), value.rules().stream().map(RiskDecisionWireConverter::toResponse).toList(),
            VariableRuleConfiguration.CONTENT_FORMAT.equals(value.ruleSetProjection().contentFormat()) ? VariableRuleConfiguration.OPERAND_FORMAT : "LEGACY",
            catalog == null ? List.of() : catalog.variables().values().stream().sorted(Comparator.comparing(v -> v.entry().definition().variableCode()))
                .map(RiskVariableWireConverter::summary).toList(), catalog == null ? null : time(catalog.valuesAsOf()),
            catalog == null ? List.of() : catalog.issues().stream().map(i -> new RiskVariableModels.VariableIssue(i.variableCode(), i.code(), i.message())).toList());
    }

    private static RuleRequestBody toResponse(RuleView rule) {
        return new RuleRequestBody(rule.ruleCode(), rule.ruleName(), rule.priorityNo(), rule.enabledFlag(),
            rule.reasonCode(), rule.conditionNodes().stream().map(RiskDecisionWireConverter::toResponse).toList(),
            rule.action() == null ? null : new RuleActionRequestBody(rule.action().action().actionCode(), rule.action().action().versionNo()),
            rule.reasonMessage(), rule.additionalActions().stream().map(value -> {
                var action = value.configuration();
                return new AdditionalAction(action.sequenceNo(), action.action().actionCode(), action.action().versionNo(),
                    action.targetBindingCode(), action.parameters().stream().map(parameter -> new ActionParameter(
                        parameter.parameterCode(), parameter.valueSequence(), parameter.sourceType().getCode(), parameter.fieldCode(),
                        parameter.valueType().getCode(), ((StringLiteral) parameter.literalValue()).value())).toList(), action.subjectVariableCode());
            }).toList());
    }

    private static ConditionNodeRequestBody toResponse(NodeView node) {
        return new ConditionNodeRequestBody(positiveId(node.nodeId()), nullableId(node.parentNodeId()),
            node.sequenceNo(), code(node.nodeType()), nullableCode(node.groupOperator()),
            nullableCode(node.comparisonOperator()), node.operands().stream().map(RiskDecisionWireConverter::toResponse)
            .toList());
    }

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

    static CreateRuleSetVersionResponseBody toResponse(CreatedRuleSet value) {
        CreatedRuleSet created = require(value, "createdRuleSet");
        return new CreateRuleSetVersionResponseBody(positiveId(created.ruleSetVersionId()),
            nullableId(created.pointVersionId()), created.ruleSetCode(), created.versionNo(), code(created.status()),
            created.contentHash(), positiveId(created.createdBy()), time(created.createTime()));
    }

    static FreezeRuleSetVersionResponseBody toResponse(FrozenRuleSet value) {
        FrozenRuleSet frozen = require(value, "frozenRuleSet");
        return new FreezeRuleSetVersionResponseBody(positiveId(frozen.ruleSetVersionId()),
            nullableId(frozen.pointVersionId()), frozen.ruleSetCode(), frozen.versionNo(), code(frozen.status()),
            frozen.contentHash(), positiveId(frozen.frozenBy()), time(frozen.frozenTime()));
    }

    static PagingResponseBody<TestReleaseListItemResponseBody> toResponse(ReleasePageView page) {
        ReleasePageView value = require(page, "releasePage");
        return new PagingResponseBody<>(value.items().stream().map(RiskDecisionWireConverter::toResponseRelease).toList(),
            value.page(), value.size(), value.total());
    }

    private static TestReleaseListItemResponseBody toResponseRelease(ReleaseSummaryView value) {
        return new TestReleaseListItemResponseBody(positiveId(value.releaseId()), positiveInt(value.releaseNo()),
            positiveId(value.decisionPointId()), value.pointCode(), positiveId(value.pointVersionId()),
            positiveId(value.ruleSetVersionId()), code(value.environment()), code(value.status()), value.version(),
            value.contentHash(), positiveId(value.createdBy()), time(value.createTime()), value.allStaticChecksPassed());
    }

    static TestReleaseDetailResponseBody toResponse(DecisionReleaseAggregate aggregate) {
        DecisionReleaseAggregate value = require(aggregate, "releaseAggregate");
        var root = value.release();
        var frozen = value.frozenComposition();
        return new TestReleaseDetailResponseBody(positiveId(root.releaseId()), positiveInt(root.releaseNo()),
            positiveId(root.decisionPointId()), root.pointCode(), positiveId(root.pointVersionId()),
            positiveId(root.ruleSetVersionId()), code(root.environment()), code(root.status()), root.version(),
            root.contentHash(), positiveId(root.createdBy()), time(root.createTime()), value.allStaticChecksPassed(),
            value.waitingForShadow(), new FrozenCompositionResponseBody(positiveId(frozen.decisionPointId()),
                frozen.pointCode(), positiveId(frozen.pointVersionId()), frozen.pointVersionNo(),
                frozen.pointVersionContentHash(), positiveId(frozen.ruleSetVersionId()), frozen.ruleSetCode(),
                frozen.ruleSetVersionNo(), frozen.ruleSetContentHash()),
            value.stateChanges().stream().map(RiskDecisionWireConverter::toResponse).toList(),
            value.staticChecks().stream().map(RiskDecisionWireConverter::toResponse).toList());
    }

    private static ReleaseStateChangeResponseBody toResponse(ReleaseStateChange value) {
        return new ReleaseStateChangeResponseBody(positiveId(value.auditEventId()), time(value.eventTime()),
            nullableCode(value.fromStatus()), code(value.toStatus()), value.reasonCode(), value.triggerType(),
            value.triggerId(), nullableId(value.actorId()), value.aggregateVersion());
    }

    private static ReleaseStaticCheckResponseBody toResponse(StaticCheckEvidence value) {
        return new ReleaseStaticCheckResponseBody(positiveId(value.auditEventId()), time(value.eventTime()),
            code(value.checkCode()), code(value.status()), code(value.severity()), value.errorCode(),
            value.errorSummary());
    }

    static CreateTestReleaseResponseBody toResponse(CreatedCandidate value) {
        CreatedCandidate created = require(value, "createdCandidate");
        ReleaseStateChange initial = created.initialStateChange();
        return new CreateTestReleaseResponseBody(positiveId(created.releaseId()), positiveInt(created.releaseNo()),
            positiveId(created.decisionPointId()), created.pointCode(), positiveId(created.pointVersionId()),
            positiveId(created.ruleSetVersionId()), code(created.environment()), code(created.status()),
            created.version(), created.contentHash(), positiveId(created.createdBy()), time(created.createTime()),
            new InitialStateChangeResponseBody(positiveId(initial.auditEventId()), time(initial.eventTime()),
                nullableCode(initial.fromStatus()), code(initial.toStatus()), initial.reasonCode(),
                initial.triggerType(), nullableId(initial.actorId()), initial.aggregateVersion()));
    }

    static ValidateTestReleaseResponseBody toResponse(ValidatedCandidate value) {
        ValidatedCandidate validated = require(value, "validatedCandidate");
        return new ValidateTestReleaseResponseBody(positiveId(validated.releaseId()),
            positiveInt(validated.releaseNo()), code(validated.environment()), code(validated.status()),
            validated.version(), validated.contentHash(), validated.allStaticChecksPassed(),
            validated.checks().stream().map(RiskDecisionWireConverter::toResponse).toList());
    }

    private static ValidationCheckResponseBody toResponse(StaticCheckResult value) {
        return new ValidationCheckResponseBody(code(value.checkCode()), code(value.checkStatus()),
            code(value.severity()), value.errorCode(), value.errorSummary());
    }

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

    static DecisionReleaseStatus parseReleaseStatus(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "DRAFT" -> DecisionReleaseStatus.DRAFT;
            case "VALIDATING" -> DecisionReleaseStatus.VALIDATING;
            case "REJECTED" -> DecisionReleaseStatus.REJECTED;
            default -> throw invalid("status");
        };
    }

    static String positiveId(long value) {
        if (value <= 0) {
            throw new ErrorException("内部资源标识无效");
        }
        return Long.toString(value);
    }

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

    private static Integer positiveInt(int value) {
        if (value <= 0) {
            throw new ErrorException("内部正整数无效");
        }
        return value;
    }

    private static String nullableId(Long value) {
        return value == null ? null : positiveId(value);
    }

    private static String time(Instant value) {
        if (value == null || value.getNano() % 1_000 != 0) {
            throw new ErrorException("内部 UTC 时间无效");
        }
        return MICROSECOND_UTC.format(value);
    }

    private static String nullableTime(Instant value) {
        return value == null ? null : time(value);
    }

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

    private static LiteralValue throwInvalidLiteral() {
        throw invalid("literalValue");
    }

    private static NodeType parseNodeType(String value) {
        return switch (value) {
            case "GROUP" -> NodeType.GROUP;
            case "PREDICATE" -> NodeType.PREDICATE;
            default -> throw invalid("nodeType");
        };
    }

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

    private static OperandRole parseOperandRole(String value) {
        return switch (value) {
            case "LEFT" -> OperandRole.LEFT;
            case "RIGHT" -> OperandRole.RIGHT;
            default -> throw invalid("operandRole");
        };
    }

    private static OperandSource parseOperandSource(String value) {
        return switch (value) {
            case "CONTEXT" -> OperandSource.CONTEXT;
            case "VARIABLE" -> OperandSource.VARIABLE;
            case "LITERAL" -> OperandSource.LITERAL;
            default -> throw invalid("sourceType");
        };
    }

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

    private static String code(com.paytech.foundation.common.enums.CodeEnum value) {
        if (value == null) {
            throw new ErrorException("内部 Code 无效");
        }
        return value.getCode();
    }

    private static String nullableCode(com.paytech.foundation.common.enums.CodeEnum value) {
        return value == null ? null : code(value);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new ErrorException(name + " 无效");
        }
        return value;
    }

    private static InvalidParameterException invalid(String name) {
        return new InvalidParameterException(name + " 格式无效");
    }
}
