package com.paytech.operations.console.bff.integration.impl;

import com.paytech.risk.decision.api.DecisionReleaseManagementModels;
import java.math.BigInteger;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskDecisionConfigurationClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.*;
import com.paytech.operations.console.bff.service.model.RiskTestCandidateModels.*;
import com.paytech.risk.decision.api.DecisionRuleSetManagementApi;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.AdditionalAction;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.ActionParameter;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.ActionParameterDescriptor;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.TargetBindingDescriptor;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.TargetFieldDescriptor;
import com.paytech.risk.decision.api.DecisionReleaseManagementApi;

/** 唯一 Owner DTO 投影边界；请求不解释 AST，响应验证完整后才公开本地模型。 */
@Component
public final class RiskDecisionConfigurationClientImpl implements RiskDecisionConfigurationClient {
    private final DecisionRuleSetManagementApi ruleSetApi;
    private final DecisionReleaseManagementApi releaseApi;

    public RiskDecisionConfigurationClientImpl(DecisionRuleSetManagementApi ruleSetApi,
        DecisionReleaseManagementApi releaseApi) {
        this.ruleSetApi = ruleSetApi;
        this.releaseApi = releaseApi;
    }

    @Override
    public RuleAuthoringContextResponseBody authoringContext(OperatorContext context, String pointCode) {
        var owner = ruleSetApi.getAuthoringContext(context.accountId(), context.sessionId(), pointCode);
        Validator.require(owner != null && owner.getPoint() != null);
        var p = owner.getPoint();
        var point = new DecisionPointDescriptorResponseBody(p.getPointCode(), p.getPointName(), p.getBusinessDomain(),
            p.getInputContractCode(), Validator.integer(p.getInputContractVersion()), p.getOutputContractCode(), p.getDefaultDecision(),
            p.getEvaluationStrategy(), p.getFailureStrategy(), Validator.integer(p.getTimeoutMillis()), p.getStatus());
        Validator.require(owner.getFields() != null && owner.getActions() != null);
        var fields = owner.getFields().stream().map(this::field).toList();
        var actions = owner.getActions().stream().map(this::action).toList();
        Validator.require(owner.getActionParameters() != null && owner.getTargetBindings() != null);
        var parameters = owner.getActionParameters().stream().map(value -> new ActionParameterDescriptor(value.getActionCode(),
            value.getActionVersionNo(), value.getParameterCode(), value.getValueType(), value.getRequiredFlag(), value.getAllowedSourceType(),
            value.getTargetParameterFlag(), value.getMaxValues(), value.getMinimumValue(), value.getMaximumValue(), value.getValuePattern())).toList();
        var targets = owner.getTargetBindings().stream().map(value -> new TargetBindingDescriptor(value.getBindingCode(),
            value.getDisplayName(), value.getSubjectType(), value.getRequiredFields().stream().map(field ->
                new TargetFieldDescriptor(field.getRole(), field.getFieldCode())).toList(), value.getSubjectSemanticRole())).toList();
        Validator.authoring(owner.getDescriptorCode(), point, fields, owner.getOperators(), actions,
            owner.getFeatures(), parameters, owner.getObligations(), owner.getLimitBindings(), targets);
        Validator.equal(pointCode, point.pointCode());
        Validator.equal("VARIABLE_REFERENCE", owner.getOperandFormat());
        var query = owner.getVariableQueryContext();
        Validator.require(query != null);
        Validator.equal(pointCode, query.getPointCode());
        Validator.equal(point.inputContractCode(), query.getInputContractCode());
        Validator.equal(point.inputContractVersion(), query.getInputContractVersion());
        Validator.equal("RULE_CONDITION", query.getUsage());
        return new RuleAuthoringContextResponseBody(owner.getDescriptorCode(), point, fields, owner.getOperators(), actions,
            owner.getFeatures(), parameters, owner.getObligations(), owner.getLimitBindings(), targets, owner.getOperandFormat(),
            RiskVariableConverter.local(owner.getVariableQueryContext()));
    }

    private DecisionInputFieldDescriptorResponseBody field(DecisionRuleSetManagementModels.DecisionInputFieldDescriptorResponseBody value) {
        Validator.require(value != null);
        return new DecisionInputFieldDescriptorResponseBody(value.getFieldCode(), value.getValueType(), Validator.flag(value.getRequiredFlag()),
            value.getDataClassification(), Validator.flag(value.getRuleReferenceFlag()), Validator.flag(value.getActionParameterFlag()), value.getVariableCode());
    }

    private DecisionActionDescriptorResponseBody action(DecisionRuleSetManagementModels.DecisionActionDescriptorResponseBody value) {
        Validator.require(value != null);
        // 指纹必须通过完整验证再删除，不能让错误 Handler 静默成为可编辑目录。
        if ("CONFIGURATION_ONLY".equals(value.getStatus())) {
            Validator.require(value.getHandlerCode() == null && value.getImplementationVersion() == null && value.getImplementationHash() == null);
        } else {
            Validator.code(value.getHandlerCode());
            Validator.text(value.getImplementationVersion(), Integer.MAX_VALUE);
            Validator.hash(value.getImplementationHash());
        }
        return new DecisionActionDescriptorResponseBody(value.getActionCode(), value.getActionName(), value.getActionCategory(),
            Validator.integer(value.getVersionNo()), Validator.flag(value.getTerminalFlag()), value.getDecisionCode(),
            value.getMergePolicy(), Validator.flag(value.getShadowSupportedFlag()), value.getStatus());
    }

    @Override
    public PagingResponseBody<RuleSetListItemResponseBody> ruleSets(OperatorContext context, int page, int size,
        RuleSetQueryRequestBody query) {
        var owner = ruleSetApi.listRuleSets(context.accountId(), context.sessionId(), page, size,
            query.pointCode(), query.ruleSetCode(), query.status());
        Validator.require(owner != null && owner.items() != null);
        var result = owner.map(this::ruleSetItem);
        Validator.page(result, page, size, RuleSetListItemResponseBody::ruleSetVersionId,
            RuleSetListItemResponseBody::createTime);
        Validator.unique(result.items(), RuleSetListItemResponseBody::ruleSetId);
        Validator.unique(result.items(), item -> item.pointCode() + ":" + item.ruleSetCode());
        for (var item : result.items()) {
            Validator.ruleSet(item);
            Validator.id(item.ruleSetId());
            Validator.id(item.managementVersion());
            Validator.oneOf(item.status(), Set.of("DRAFT", "FROZEN"));
            Validator.filter(query.pointCode(), item.pointCode());
            Validator.filter(query.ruleSetCode(), item.ruleSetCode());
            Validator.filter(query.status(), item.status());
        }
        return result;
    }

    private RuleSetListItemResponseBody ruleSetItem(com.paytech.risk.decision.api.response.RuleSetListItemResponseBody r) {
        Validator.require(r != null);
        return new RuleSetListItemResponseBody(r.getRuleSetVersionId(), r.getPointVersionId(), r.getPointCode(), r.getRuleSetCode(),
            r.getRuleSetName(), Validator.integer(r.getVersionNo()), r.getStatus(), r.getContentHash(), r.getCreatedBy(), r.getChangeReason(),
            r.getCreateTime(), r.getFrozenBy(), r.getFrozenTime(), r.getRuleSetId(), r.getManagementVersion(), Validator.flag(r.getInUse()));
    }

    @Override
    public RuleSetDetailResponseBody ruleSet(OperatorContext context, String ruleSetVersionId) {
        var r = ruleSetApi.getRuleSet(context.accountId(), context.sessionId(), ruleSetVersionId);
        return ruleSetDetail(r, ruleSetVersionId);
    }

    /** 已保存来源的规则详情与普通规则详情使用同一严格转换器。 */
    static RuleSetDetailResponseBody ruleSetDetail(com.paytech.risk.decision.api.response.RuleSetDetailResponseBody r,
        String ruleSetVersionId) {
        Validator.require(r != null && r.getRules() != null);
        var rules = r.getRules().stream().map(RiskDecisionConfigurationClientImpl::rule).toList();
        var detail = new RuleSetDetailResponseBody(r.getRuleSetVersionId(), r.getPointVersionId(), r.getPointCode(), r.getRuleSetCode(),
            r.getRuleSetName(), Validator.integer(r.getVersionNo()), r.getStatus(), r.getContentHash(), r.getCreatedBy(), r.getChangeReason(),
            r.getCreateTime(), r.getFrozenBy(), r.getFrozenTime(), rules, r.getOperandFormat(),
            r.getCurrentVariables().stream().map(RiskVariableConverter::local).toList(), r.getValuesAsOf(),
            r.getVariableIssues().stream().map(RiskVariableConverter::local).toList());
        Validator.ruleSet(detail);
        Validator.rules(rules);
        Validator.equal(ruleSetVersionId, r.getRuleSetVersionId());
        return detail;
    }

    private static RuleRequestBody rule(DecisionRuleSetManagementModels.RuleRequestBody r) {
        Validator.require(r != null && r.getConditionNodes() != null && r.getAdditionalActions() != null);
        return new RuleRequestBody(r.getRuleCode(), r.getRuleName(), r.getPriorityNo(), r.getEnabledFlag(), r.getReasonCode(),
            r.getConditionNodes().stream().map(RiskDecisionConfigurationClientImpl::node).toList(),
            r.getAction() == null ? null : new RuleActionRequestBody(r.getAction().getActionCode(), r.getAction().getActionVersionNo()),
            r.getReasonMessage(), r.getAdditionalActions().stream().map(action -> new AdditionalAction(action.getSequenceNo(),
                action.getActionCode(), action.getActionVersionNo(), action.getTargetBindingCode(), action.getParameters().stream()
                    .map(value -> new ActionParameter(value.getParameterCode(), value.getValueSequence(), value.getSourceType(),
                        value.getFieldCode(), value.getValueType(), value.getLiteralValue())).toList(), action.getSubjectVariableCode())).toList());
    }

    private static ConditionNodeRequestBody node(DecisionRuleSetManagementModels.ConditionNodeRequestBody n) {
        Validator.require(n != null && n.getOperands() != null);
        return new ConditionNodeRequestBody(n.getNodeKey(), n.getParentNodeKey(), n.getSequenceNo(), n.getNodeType(),
            n.getGroupOperator(), n.getComparisonOperator(), n.getOperands().stream().map(RiskDecisionConfigurationClientImpl::operand).toList());
    }

    private static OperandRequestBody operand(DecisionRuleSetManagementModels.OperandRequestBody o) {
        Validator.require(o != null);
        Validator.require(o.getLiteralValue() == null || o.getLiteralValue() instanceof String || o.getLiteralValue() instanceof Boolean);
        return new OperandRequestBody(o.getOperandRole(), o.getValueSequence(), o.getSourceType(), o.getFieldCode(), o.getValueType(), o.getLiteralValue(),
            o.getVariableCode(), o.getNumericUnit(), o.getCurrencyCode(), o.getCurrencyVariableCode());
    }

    @Override
    public CreateRuleSetVersionResponseBody createRuleSetVersion(OperatorContext context, CreateRuleSetVersionRequestBody request) {
        var body = new com.paytech.risk.decision.api.request.CreateRuleSetVersionRequestBody(request.pointCode(), request.ruleSetCode(), request.ruleSetName(),
            request.baseRuleSetVersionId(), request.baseContentHash(), request.changeReason(),
            request.rules().stream().map(this::ownerRule).toList(), request.operandFormat());
        var r = ruleSetApi.createRuleSetVersion(context.accountId(), context.sessionId(), body);
        Validator.require(r != null);
        var result = new CreateRuleSetVersionResponseBody(r.getRuleSetVersionId(), r.getPointVersionId(), r.getRuleSetCode(),
            Validator.integer(r.getVersionNo()), r.getStatus(), r.getContentHash(), r.getCreatedBy(), r.getCreateTime());
        Validator.ruleCommand(result.ruleSetVersionId(), result.pointVersionId(), result.ruleSetCode(), result.versionNo(),
            result.contentHash(), result.createdBy(), result.createTime());
        Validator.equal("DRAFT", result.status());
        Validator.equal(request.ruleSetCode(), result.ruleSetCode());
        return result;
    }

    private DecisionRuleSetManagementModels.RuleRequestBody ownerRule(RuleRequestBody r) {
        return new DecisionRuleSetManagementModels.RuleRequestBody(r.ruleCode(), r.ruleName(), r.priorityNo(), r.enabledFlag(), r.reasonCode(),
            r.conditionNodes().stream().map(this::ownerNode).toList(),
            r.action() == null ? null : new DecisionRuleSetManagementModels.RuleActionRequestBody(r.action().actionCode(), r.action().actionVersionNo()),
            r.reasonMessage(), r.additionalActions() == null ? null : r.additionalActions().stream().map(action ->
                new DecisionRuleSetManagementModels.AdditionalAction(action.sequenceNo(), action.actionCode(), action.actionVersionNo(),
                    action.targetBindingCode(), action.parameters().stream().map(value -> new DecisionRuleSetManagementModels.ActionParameter(
                        value.parameterCode(), value.valueSequence(), value.sourceType(), value.fieldCode(), value.valueType(),
                        value.literalValue())).toList(), action.subjectVariableCode())).toList());
    }

    private DecisionRuleSetManagementModels.ConditionNodeRequestBody ownerNode(ConditionNodeRequestBody n) {
        return new DecisionRuleSetManagementModels.ConditionNodeRequestBody(n.nodeKey(), n.parentNodeKey(), n.sequenceNo(), n.nodeType(), n.groupOperator(),
            n.comparisonOperator(), n.operands().stream().map(o -> new DecisionRuleSetManagementModels.OperandRequestBody(o.operandRole(), o.valueSequence(),
                o.sourceType(), o.fieldCode(), o.valueType(), o.literalValue(),
                o.variableCode(), o.numericUnit(), o.currencyCode(), o.currencyVariableCode())).toList());
    }

    /** 验证整体删除回执身份和终态，不在 BFF 判断业务使用状态或编排事务。 */
    @Override
    public DeleteRuleSetResponseBody deleteRuleSet(OperatorContext context, String ruleSetId, DeleteRuleSetRequestBody request) {
        var r = ruleSetApi.deleteRuleSet(context.accountId(), context.sessionId(), ruleSetId,
            new com.paytech.risk.decision.api.request.DeleteRuleSetRequestBody(request.expectedVersion()));
        Validator.require(r != null);
        Validator.id(r.getRuleSetId()); Validator.id(r.getVersion()); Validator.id(r.getDeletedBy()); Validator.time(r.getDeletedTime());
        Validator.equal(ruleSetId, r.getRuleSetId()); Validator.equal("DELETED", r.getStatus());
        Validator.equal(context.accountId(), r.getDeletedBy());
        Validator.require(new BigInteger(r.getVersion()).equals(new BigInteger(request.expectedVersion()).add(BigInteger.ONE)));
        return new DeleteRuleSetResponseBody(r.getRuleSetId(), r.getStatus(), r.getVersion(), r.getDeletedBy(), r.getDeletedTime());
    }

    @Override
    public FreezeRuleSetVersionResponseBody freezeRuleSetVersion(OperatorContext context, String ruleSetVersionId, FreezeRuleSetVersionRequestBody request) {
        var r = ruleSetApi.freezeRuleSetVersion(context.accountId(), context.sessionId(), ruleSetVersionId, new com.paytech.risk.decision.api.request.FreezeRuleSetVersionRequestBody(request.expectedContentHash()));
        Validator.require(r != null);
        var result = new FreezeRuleSetVersionResponseBody(r.getRuleSetVersionId(), r.getPointVersionId(), r.getRuleSetCode(),
            Validator.integer(r.getVersionNo()), r.getStatus(), r.getContentHash(), r.getFrozenBy(), r.getFrozenTime());
        Validator.ruleCommand(result.ruleSetVersionId(), result.pointVersionId(), result.ruleSetCode(), result.versionNo(),
            result.contentHash(), result.frozenBy(), result.frozenTime());
        Validator.equal("FROZEN", result.status());
        Validator.equal(ruleSetVersionId, result.ruleSetVersionId());
        Validator.equal(request.expectedContentHash(), result.contentHash());
        return result;
    }

    @Override
    public PagingResponseBody<TestCandidateListItemResponseBody> candidates(OperatorContext context, int page, int size,
        CandidateQueryRequestBody query) {
        var owner = releaseApi.listCandidates(context.accountId(), context.sessionId(), page, size, query.pointCode(), query.status());
        Validator.require(owner != null && owner.items() != null);
        var result = owner.map(this::candidateItem);
        Validator.page(result, page, size, TestCandidateListItemResponseBody::releaseId, TestCandidateListItemResponseBody::createTime);
        for (var item : result.items()) {
            Validator.candidate(item);
            Validator.filter(query.pointCode(), item.pointCode());
            Validator.filter(query.status(), item.status());
        }
        return result;
    }

    private TestCandidateListItemResponseBody candidateItem(com.paytech.risk.decision.api.response.TestReleaseListItemResponseBody r) {
        Validator.require(r != null);
        return new TestCandidateListItemResponseBody(r.getReleaseId(), Validator.integer(r.getReleaseNo()), r.getDecisionPointId(),
            r.getPointCode(), r.getPointVersionId(), r.getRuleSetVersionId(), r.getEnvironmentCode(), r.getStatus(), Validator.integer(r.getVersion()),
            r.getContentHash(), r.getCreatedBy(), r.getCreateTime(), r.getAllStaticChecksPassed());
    }

    @Override
    public TestCandidateDetailResponseBody candidate(OperatorContext context, String releaseId) {
        var r = releaseApi.getCandidate(context.accountId(), context.sessionId(), releaseId);
        Validator.require(r != null && r.getFrozenComposition() != null && r.getStateChanges() != null && r.getStaticChecks() != null);
        var item = new TestCandidateListItemResponseBody(r.getReleaseId(), Validator.integer(r.getReleaseNo()), r.getDecisionPointId(),
            r.getPointCode(), r.getPointVersionId(), r.getRuleSetVersionId(), r.getEnvironmentCode(), r.getStatus(), Validator.integer(r.getVersion()),
            r.getContentHash(), r.getCreatedBy(), r.getCreateTime(), r.getAllStaticChecksPassed());
        Validator.candidate(item);
        var f = r.getFrozenComposition();
        var frozen = new FrozenCompositionResponseBody(f.getDecisionPointId(), f.getPointCode(), f.getPointVersionId(),
            Validator.integer(f.getPointVersionNo()), f.getPointVersionContentHash(), f.getRuleSetVersionId(), f.getRuleSetCode(),
            Validator.integer(f.getRuleSetVersionNo()), f.getRuleSetContentHash());
        var states = r.getStateChanges().stream().map(this::stateChange).toList();
        var checks = r.getStaticChecks().stream().map(this::staticCheck).toList();
        boolean waiting = Validator.flag(r.getWaitingForShadow());
        Validator.detail(item, waiting, frozen, states, checks);
        Validator.equal(releaseId, r.getReleaseId());
        return new TestCandidateDetailResponseBody(r.getReleaseId(), r.getReleaseNo(), r.getDecisionPointId(), r.getPointCode(), r.getPointVersionId(),
            r.getRuleSetVersionId(), r.getEnvironmentCode(), r.getStatus(), r.getVersion(), r.getContentHash(), r.getCreatedBy(), r.getCreateTime(),
            r.getAllStaticChecksPassed(), waiting, frozen, states, checks);
    }

    private ReleaseStateChangeResponseBody stateChange(DecisionReleaseManagementModels.ReleaseStateChangeResponseBody s) {
        Validator.require(s != null);
        return new ReleaseStateChangeResponseBody(s.getAuditEventId(), s.getEventTime(), s.getFromStatus(), s.getToStatus(), s.getReasonCode(),
            s.getTriggerType(), s.getTriggerId(), s.getActorId(), Validator.integer(s.getAggregateVersion()));
    }

    private ReleaseStaticCheckResponseBody staticCheck(DecisionReleaseManagementModels.ReleaseStaticCheckResponseBody c) {
        Validator.require(c != null);
        return new ReleaseStaticCheckResponseBody(c.getAuditEventId(), c.getEventTime(), c.getCheckCode(), c.getCheckStatus(),
            c.getSeverity(), c.getErrorCode(), c.getErrorSummary());
    }

    @Override
    public CreateTestCandidateResponseBody createCandidate(OperatorContext context,
        CreateTestCandidateRequestBody request) {
        var r = releaseApi.createCandidate(context.accountId(), context.sessionId(),
            new com.paytech.risk.decision.api.request.CreateTestReleaseRequestBody(request.ruleSetVersionId(), request.expectedRuleSetContentHash()));
        Validator.require(r != null && r.getInitialStateChange() != null);
        var state = r.getInitialStateChange();
        var initial = new InitialStateChangeResponseBody(state.getAuditEventId(), state.getEventTime(), state.getFromStatus(),
            state.getToStatus(), state.getReasonCode(), state.getTriggerType(), state.getActorId(), Validator.integer(state.getAggregateVersion()));
        var result = new CreateTestCandidateResponseBody(r.getReleaseId(), Validator.integer(r.getReleaseNo()), r.getDecisionPointId(),
            r.getPointCode(), r.getPointVersionId(), r.getRuleSetVersionId(), r.getEnvironmentCode(), r.getStatus(), Validator.integer(r.getVersion()),
            r.getContentHash(), r.getCreatedBy(), r.getCreateTime(), initial);
        Validator.created(result);
        Validator.equal(request.ruleSetVersionId(), result.ruleSetVersionId());
        return result;
    }

    @Override
    public ValidateTestCandidateResponseBody validateCandidate(OperatorContext context,
        String releaseId, ValidateTestCandidateRequestBody request) {
        var r = releaseApi.validateCandidate(context.accountId(), context.sessionId(), releaseId,
            new com.paytech.risk.decision.api.request.ValidateTestReleaseRequestBody(request.expectedVersion()));
        Validator.require(r != null && r.getChecks() != null);
        var checks = r.getChecks().stream().map(this::validationCheck).toList();
        boolean passed = Validator.flag(r.getAllStaticChecksPassed());
        Validator.validated(r.getReleaseId(), r.getReleaseNo(), r.getEnvironmentCode(), r.getStatus(), r.getVersion(), r.getContentHash(), passed, checks);
        Validator.equal(releaseId, r.getReleaseId());
        Validator.require(request.expectedVersion() != null
            && (long) r.getVersion() == (long) request.expectedVersion() + (passed ? 1 : 2));
        return new ValidateTestCandidateResponseBody(r.getReleaseId(), r.getReleaseNo(), r.getEnvironmentCode(), r.getStatus(), r.getVersion(),
            r.getContentHash(), passed, checks);
    }

    private ValidationCheckResponseBody validationCheck(DecisionReleaseManagementModels.ValidationCheckResponseBody c) {
        Validator.require(c != null);
        return new ValidationCheckResponseBody(c.getCheckCode(), c.getCheckStatus(), c.getSeverity(), c.getErrorCode(), c.getErrorSummary());
    }

    /** 只消费本地值，不读取或构造 Owner DTO；拒绝信息永远不携带策略数据。 */
    private static final class Validator {
        private static final Pattern ID = Pattern.compile("[1-9][0-9]{0,18}");
        private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
        private static final Pattern FIELD = Pattern.compile("[a-z][a-z0-9_]{0,63}");
        private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
        private static final Pattern TIME = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{6}Z");
        private static final DateTimeFormatter INSTANT = new DateTimeFormatterBuilder().appendInstant(6).toFormatter();
        private static final List<String> OPERATORS = List.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN", "BETWEEN", "IS_NULL", "IS_NOT_NULL");
        private static final List<String> CHECKS = List.of("REFERENCE_INTEGRITY", "AST_SHAPE", "AST_TYPES", "FEATURE_DEPENDENCY",
            "HANDLER_HASH", "ACTION_PARAMETERS", "TERMINAL_ACTION", "SYSTEM_OBLIGATION");
        private static final Set<String> TYPES = Set.of("STRING", "NUMBER", "BOOLEAN", "DATETIME", "DURATION");
        private static final Pattern SUMMARY = Pattern.compile("first=([A-Z][A-Z0-9_]{0,63})@(-|actionParameters|businessCoordinates|closure|duplicates|features|ids|inputFields|nodes|obligations|operands|persistedActions|point|release|ruleActions|rules|ruleSetBindings|ruleSets|node[._][1-9][0-9]*|inputFields/[a-z][a-z0-9_]{0,63}|(?:actionVersions|nodes|operands|rules)/[1-9][0-9]*); violation_count=([1-9][0-9]*)");

        private static void require(boolean valid) {
            if (!valid) throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }

        private static void equal(Object expected, Object actual) { require(Objects.equals(expected, actual)); }
        private static void filter(String expected, String actual) { if (expected != null) equal(expected, actual); }
        private static void match(Pattern pattern, String value) { require(value != null && pattern.matcher(value).matches()); }
        private static void id(String value) { match(ID, value); }
        private static void code(String value) { match(CODE, value); }
        private static void hash(String value) { match(HASH, value); }
        private static void text(String value, int max) { require(value != null && !value.isBlank() && value.length() <= max); }
        private static int integer(Integer value) { require(value != null && value > 0); return value; }
        private static boolean flag(Boolean value) { require(value != null); return value; }
        private static void oneOf(String value, Set<String> values) { require(value != null && values.contains(value)); }
        private static void time(String value) {
            match(TIME, value);
            try { equal(value, INSTANT.format(Instant.parse(value))); }
            catch (DateTimeParseException invalid) { throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE); }
        }

        private static void empty(List<?> values) { require(values != null && values.isEmpty()); }
        private static <T> void list(List<T> values) { require(values != null && values.stream().noneMatch(Objects::isNull)); }
        private static <T> void unique(List<T> values, Function<T, ?> key) {
            list(values);
            Set<Object> seen = new HashSet<>();
            for (T value : values) require(seen.add(key.apply(value)));
        }

        private static <T> void ordered(List<T> values, Comparator<T> order) {
            for (int i = 1; i < values.size(); i++) require(order.compare(values.get(i - 1), values.get(i)) <= 0);
        }

        private static void authoring(String descriptor, DecisionPointDescriptorResponseBody p,
            List<DecisionInputFieldDescriptorResponseBody> fields, List<String> operators,
            List<DecisionActionDescriptorResponseBody> actions, List<Void> features, List<ActionParameterDescriptor> parameters,
            List<Void> obligations, List<Void> bindings, List<TargetBindingDescriptor> targets) {
            equal("RISK_VARIABLE_AUTHORING", descriptor);
            code(p.pointCode()); code(p.inputContractCode()); integer(p.inputContractVersion());
            text(p.pointName(), 255); code(p.outputContractCode()); code(p.businessDomain());
            oneOf(p.defaultDecision(), Set.of("ALLOW", "REVIEW", "DENY")); code(p.evaluationStrategy()); code(p.failureStrategy()); oneOf(p.status(), Set.of("ACTIVE", "INACTIVE")); integer(p.timeoutMillis());
            unique(fields, DecisionInputFieldDescriptorResponseBody::variableCode);
            for (var field : fields) {
                match(FIELD, field.fieldCode()); code(field.variableCode()); oneOf(field.valueType(), TYPES);
                equal("INTERNAL", field.dataClassification());
                require(field.ruleReferenceFlag() || field.actionParameterFlag());
            }

            unique(actions, a -> List.of(a.actionCode(), a.versionNo()));
            for (var a : actions) {
                code(a.actionCode()); text(a.actionName(), 255); integer(a.versionNo());
                if (!a.terminalFlag()) {
                    equal("CONFIGURATION_ONLY", a.status()); require(a.decisionCode() == null && !a.shadowSupportedFlag());
                    oneOf(a.actionCategory(), Set.of("RESTRICTION", "TAG"));
                    oneOf(a.mergePolicy(), Set.of("MAX", "UNION"));
                    continue;
                }
                equal("DECISION", a.actionCategory()); equal("MOST_RESTRICTIVE", a.mergePolicy()); equal("ACTIVE", a.status());
                require(a.terminalFlag() && a.shadowSupportedFlag());
                // 终态动作按风控点命名（ALLOW_CHECKOUT、DENY_PAYMENT 等），编码前缀须与其决定一致。
                String decision = a.actionCode().substring(0, Math.max(a.actionCode().indexOf('_'), 0));
                oneOf(decision, Set.of("ALLOW", "REVIEW", "DENY")); equal(decision, a.decisionCode());
            }
            ordered(actions, Comparator.comparing(DecisionActionDescriptorResponseBody::actionCode)
                .thenComparingInt(DecisionActionDescriptorResponseBody::versionNo));
            equal(OPERATORS, operators);
            empty(features); empty(obligations); empty(bindings);
            configurationMetadata(fields, actions, parameters, targets);
        }

        private static void configurationMetadata(List<DecisionInputFieldDescriptorResponseBody> fields,
            List<DecisionActionDescriptorResponseBody> actions, List<ActionParameterDescriptor> parameters,
            List<TargetBindingDescriptor> targets) {
            unique(targets, TargetBindingDescriptor::bindingCode);
            unique(parameters, value -> List.of(value.actionCode(), value.actionVersionNo(), value.parameterCode()));
            for (var target : targets) {
                code(target.bindingCode()); code(target.subjectType()); text(target.displayName(), 255);
                unique(target.requiredFields(), TargetFieldDescriptor::role); code(target.subjectSemanticRole());
                for (var field : target.requiredFields()) {
                    code(field.role()); match(FIELD, field.fieldCode());
                    require(fields.stream().anyMatch(value -> value.fieldCode().equals(field.fieldCode()) && value.actionParameterFlag()));
                }
            }
            for (var parameter : parameters) {
                code(parameter.actionCode()); code(parameter.parameterCode()); integer(parameter.actionVersionNo());
                oneOf(parameter.valueType(), TYPES); oneOf(parameter.allowedSourceType(), Set.of("CONTEXT", "VARIABLE", "LITERAL"));
                flag(parameter.requiredFlag()); flag(parameter.targetParameterFlag()); integer(parameter.maxValues());
                require(actions.stream().anyMatch(action -> action.actionCode().equals(parameter.actionCode())
                    && action.versionNo() == parameter.actionVersionNo() && !action.terminalFlag()));
                if (parameter.targetParameterFlag()) equal("VARIABLE", parameter.allowedSourceType());
                if (parameter.minimumValue() != null || parameter.maximumValue() != null) {
                    require(parameter.minimumValue() != null && parameter.maximumValue() != null);
                    match(ID, parameter.minimumValue()); match(ID, parameter.maximumValue());
                    require(new BigInteger(parameter.minimumValue()).compareTo(new BigInteger(parameter.maximumValue())) <= 0);
                }
            }
            for (var action : actions) if (!action.terminalFlag()) {
                require(!targets.isEmpty());
                require(parameters.stream().anyMatch(value -> value.actionCode().equals(action.actionCode())
                    && value.actionVersionNo() == action.versionNo() && value.targetParameterFlag()));
            }
        }

        private static void ruleCommand(String ruleId, String pointId, String ruleCode, Integer version,
            String contentHash, String actor, String timestamp) {
            id(ruleId); if (pointId != null) id(pointId); code(ruleCode); integer(version); hash(contentHash); id(actor); time(timestamp);
        }

        private static void ruleSet(RuleSetVersionFields r) {
            ruleCommand(r.ruleSetVersionId(), r.pointVersionId(), r.ruleSetCode(), r.versionNo(), r.contentHash(), r.createdBy(), r.createTime());
            code(r.pointCode()); text(r.ruleSetName(), 255); text(r.changeReason(), 500);
            oneOf(r.status(), Set.of("DRAFT", "FROZEN", "RETIRED"));
            if (r.status().equals("FROZEN")) { id(r.frozenBy()); time(r.frozenTime()); }
            else require(r.frozenBy() == null && r.frozenTime() == null);
        }

        private static <T> void page(PagingResponseBody<T> result, int page, int size,
            Function<T, String> primaryId, Function<T, String> createTime) {
            require(page >= 1 && page <= 100000 && Set.of(10, 20, 50, 100, 200).contains(size));
            require(result.page() == page && result.size() == size && result.total() >= 0);
            list(result.items());
            long offset = (long) (page - 1) * size;
            require(result.items().size() <= size);
            if (offset >= result.total()) require(result.items().isEmpty());
            else require(!result.items().isEmpty() && offset + result.items().size() <= result.total());
            unique(result.items(), primaryId);
            for (var item : result.items()) { id(primaryId.apply(item)); time(createTime.apply(item)); }
            ordered(result.items(), Comparator.comparing(createTime).reversed()
                .thenComparing(item -> new BigInteger(primaryId.apply(item)), Comparator.reverseOrder()));
        }

        private static void rules(List<RuleRequestBody> rules) {
            unique(rules, RuleRequestBody::ruleCode);
            require(!rules.isEmpty() && rules.size() <= 200);
            for (var r : rules) {
                code(r.ruleCode()); text(r.ruleName(), 255); integer(r.priorityNo()); flag(r.enabledFlag());
                if (r.reasonCode() != null) code(r.reasonCode());
                if (r.reasonMessage() != null) text(r.reasonMessage(), 500);
                list(r.additionalActions()); require(r.additionalActions().size() <= 16);
                if (r.action() != null) { code(r.action().actionCode()); integer(r.action().actionVersionNo()); }
                int sequence = 1;
                for (var action : r.additionalActions()) {
                    require(action.sequenceNo() == sequence++); code(action.actionCode()); integer(action.actionVersionNo());
                    code(action.targetBindingCode()); list(action.parameters()); require(!action.parameters().isEmpty());
                    for (var parameter : action.parameters()) {
                        code(parameter.parameterCode()); integer(parameter.valueSequence()); oneOf(parameter.valueType(), TYPES);
                        oneOf(parameter.sourceType(), Set.of("CONTEXT", "VARIABLE", "LITERAL"));
                        require(parameter.literalValue() == null || parameter.literalValue() instanceof String || parameter.literalValue() instanceof Boolean);
                    }
                }
                nodes(r.conditionNodes());
            }
            ordered(rules, Comparator.comparingInt((RuleRequestBody r) -> r.priorityNo()).thenComparing(RuleRequestBody::ruleCode));
        }

        private static void nodes(List<ConditionNodeRequestBody> nodes) {
            unique(nodes, ConditionNodeRequestBody::nodeKey);
            require(!nodes.isEmpty() && nodes.size() <= 2000);
            List<ConditionNodeRequestBody> ancestry = new ArrayList<>();
            Map<String, ConditionNodeRequestBody> lastSibling = new HashMap<>();
            for (var n : nodes) {
                id(n.nodeKey()); integer(n.sequenceNo());
                if (n.parentNodeKey() != null) id(n.parentNodeKey());
                oneOf(n.nodeType(), Set.of("GROUP", "PREDICATE"));
                if (n.nodeType().equals("GROUP")) {
                    oneOf(n.groupOperator(), Set.of("AND", "OR", "NOT")); require(n.comparisonOperator() == null); empty(n.operands());
                } else {
                    require(n.groupOperator() == null);
                    require(n.comparisonOperator() != null && OPERATORS.contains(n.comparisonOperator()));
                    operands(n.operands());
                }
                if (ancestry.isEmpty()) require(n.parentNodeKey() == null);
                else {
                    require(n.parentNodeKey() != null);
                    while (!ancestry.isEmpty() && !ancestry.getLast().nodeKey().equals(n.parentNodeKey())) ancestry.removeLast();
                    require(!ancestry.isEmpty() && ancestry.getLast().nodeType().equals("GROUP"));
                }
                var previous = lastSibling.put(n.parentNodeKey(), n);
                if (previous != null) require(previous.sequenceNo() < n.sequenceNo()
                    || previous.sequenceNo().equals(n.sequenceNo()) && new BigInteger(previous.nodeKey()).compareTo(new BigInteger(n.nodeKey())) < 0);
                ancestry.add(n);
            }
        }

        private static void operands(List<OperandRequestBody> operands) {
            list(operands); require(operands.size() <= 101);
            Set<String> keys = new HashSet<>();
            for (var o : operands) {
                oneOf(o.operandRole(), Set.of("LEFT", "RIGHT")); integer(o.valueSequence());
                require(keys.add(o.operandRole() + ":" + o.valueSequence()));
                oneOf(o.sourceType(), Set.of("CONTEXT", "VARIABLE", "LITERAL")); oneOf(o.valueType(), TYPES);
                if (o.sourceType().equals("CONTEXT")) { match(FIELD, o.fieldCode()); require(o.literalValue() == null); }
                else if (o.sourceType().equals("VARIABLE")) {
                    code(o.variableCode()); require(o.fieldCode() == null && o.literalValue() == null);
                } else {
                    require(o.fieldCode() == null && o.variableCode() == null);
                    require(o.literalValue() == null || o.valueType().equals("BOOLEAN") && o.literalValue() instanceof Boolean
                        || !o.valueType().equals("BOOLEAN") && o.literalValue() instanceof String);
                }
            }
            ordered(operands, Comparator.comparing(OperandRequestBody::operandRole).thenComparingInt(o -> o.valueSequence()));
        }

        private static void candidate(TestCandidateListItemResponseBody c) {
            candidateScalars(c.releaseId(), c.releaseNo(), c.decisionPointId(), c.pointCode(), c.pointVersionId(),
                c.ruleSetVersionId(), c.environmentCode(), c.contentHash(), c.createdBy(), c.createTime());
            switch (c.status() == null ? "" : c.status()) {
                case "DRAFT" -> require(c.version() == 1 && c.allStaticChecksPassed() == null);
                case "VALIDATING" -> require(c.version() == 2 && !Boolean.FALSE.equals(c.allStaticChecksPassed()));
                case "REJECTED" -> require(c.version() == 3 && Boolean.FALSE.equals(c.allStaticChecksPassed()));
                default -> require(false);
            }
        }

        private static void candidateScalars(String releaseId, Integer releaseNo, String decisionPointId, String pointCode,
            String pointVersionId, String ruleSetVersionId, String environmentCode, String contentHash, String createdBy, String createTime) {
            id(releaseId); integer(releaseNo); id(decisionPointId); code(pointCode); id(pointVersionId); id(ruleSetVersionId);
            equal("TEST", environmentCode); hash(contentHash); id(createdBy); time(createTime);
        }

        private static void detail(TestCandidateListItemResponseBody c, boolean waiting, FrozenCompositionResponseBody frozen,
            List<ReleaseStateChangeResponseBody> states, List<ReleaseStaticCheckResponseBody> checks) {
            id(frozen.decisionPointId()); code(frozen.pointCode()); id(frozen.pointVersionId()); integer(frozen.pointVersionNo());
            hash(frozen.pointVersionContentHash()); id(frozen.ruleSetVersionId()); code(frozen.ruleSetCode());
            integer(frozen.ruleSetVersionNo()); hash(frozen.ruleSetContentHash());
            equal(c.decisionPointId(), frozen.decisionPointId()); equal(c.pointCode(), frozen.pointCode());
            equal(c.pointVersionId(), frozen.pointVersionId()); equal(c.ruleSetVersionId(), frozen.ruleSetVersionId());
            list(states); list(checks); require(states.size() == c.version());
            Set<String> auditIds = new HashSet<>();
            for (int i = 0; i < states.size(); i++) {
                var s = states.get(i);
                state(s.auditEventId(), s.eventTime(), s.fromStatus(), s.toStatus(), s.reasonCode(), s.triggerType(), s.actorId(), s.aggregateVersion(), i + 1);
                hash(s.triggerId()); require(auditIds.add(s.auditEventId()));
            }
            equal(c.createdBy(), states.getFirst().actorId()); equal(c.createTime(), states.getFirst().eventTime());
            if (c.status().equals("REJECTED")) {
                equal(states.get(1).actorId(), states.get(2).actorId()); equal(states.get(1).triggerId(), states.get(2).triggerId());
            }
            List<ValidationCheckResponseBody> values = new ArrayList<>();
            for (var check : checks) {
                id(check.auditEventId()); time(check.eventTime()); require(auditIds.add(check.auditEventId()));
                values.add(new ValidationCheckResponseBody(check.checkCode(), check.checkStatus(), check.severity(), check.errorCode(), check.errorSummary()));
            }
            if (c.allStaticChecksPassed() == null) { empty(checks); require(!waiting); }
            else { equal(c.allStaticChecksPassed(), checks(values)); equal(c.allStaticChecksPassed(), waiting); }
        }

        private static void state(String auditId, String eventTime, String from, String to, String reason, String trigger,
            String actor, int aggregateVersion, int expectedVersion) {
            id(auditId); time(eventTime); id(actor); equal("OPERATIONS_COMMAND", trigger); require(aggregateVersion == expectedVersion);
            equal(switch (expectedVersion) { case 1 -> null; case 2 -> "DRAFT"; default -> "VALIDATING"; }, from);
            equal(switch (expectedVersion) { case 1 -> "DRAFT"; case 2 -> "VALIDATING"; default -> "REJECTED"; }, to);
            equal(switch (expectedVersion) { case 1 -> "CANDIDATE_CREATED"; case 2 -> "STATIC_VALIDATION_STARTED"; default -> "STATIC_VALIDATION_FAILED"; }, reason);
        }

        private static void created(CreateTestCandidateResponseBody c) {
            candidateScalars(c.releaseId(), c.releaseNo(), c.decisionPointId(), c.pointCode(), c.pointVersionId(),
                c.ruleSetVersionId(), c.environmentCode(), c.contentHash(), c.createdBy(), c.createTime());
            equal("DRAFT", c.status()); require(c.version() == 1);
            var s = c.initialStateChange();
            state(s.auditEventId(), s.eventTime(), s.fromStatus(), s.toStatus(), s.reasonCode(), s.triggerType(), s.actorId(), s.aggregateVersion(), 1);
            equal(c.createdBy(), s.actorId()); equal(c.createTime(), s.eventTime());
        }

        private static void validated(String releaseId, Integer releaseNo, String environment, String status, Integer version,
            String contentHash, boolean passed, List<ValidationCheckResponseBody> values) {
            id(releaseId); integer(releaseNo); equal("TEST", environment); hash(contentHash);
            equal(passed ? "VALIDATING" : "REJECTED", status); equal(passed ? 2 : 3, version);
            equal(passed, checks(values));
        }

        private static boolean checks(List<ValidationCheckResponseBody> values) {
            list(values); require(values.size() == CHECKS.size());
            boolean allPassed = true;
            for (int i = 0; i < values.size(); i++) {
                var c = values.get(i); equal(CHECKS.get(i), c.checkCode());
                if ("PASSED".equals(c.checkStatus())) {
                    equal("INFO", c.severity()); require(c.errorCode() == null && c.errorSummary() == null);
                } else {
                    equal("FAILED", c.checkStatus()); equal("ERROR", c.severity()); code(c.errorCode()); summary(c.errorCode(), c.errorSummary());
                    allPassed = false;
                }
            }
            return allPassed;
        }

        private static void summary(String errorCode, String value) {
            text(value, 1000);
            var matcher = SUMMARY.matcher(value);
            require(matcher.matches()); equal(errorCode, matcher.group(1));
            try { require(Integer.parseInt(matcher.group(3)) > 0); }
            catch (NumberFormatException overflow) { throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE); }
        }
    }
}
