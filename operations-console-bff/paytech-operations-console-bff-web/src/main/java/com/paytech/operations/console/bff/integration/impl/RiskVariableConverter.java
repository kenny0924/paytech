package com.paytech.operations.console.bff.integration.impl;

import com.paytech.risk.api.decision.RiskVariableModels;

/** 变量 Owner DTO 的明确投影；当前值不进入 BFF 持久化或缓存。 */
final class RiskVariableConverter {
    private RiskVariableConverter() { }
    /** 转换 VariableSummary，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableSummary local(RiskVariableModels.VariableSummary r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableSummary(r.getVariableCode(), r.getVariableName(), r.getSourceType(), r.getValueType(), r.getPointCode(), r.getUsages(), r.getSystemManaged(), r.getStatus(), r.getVersion(), r.getNumericUnit(), r.getCurrencyCode(), r.getCurrencyVariableCode(), r.getFormatCode(), r.getAllowedValuesVariableCode(), r.getCurrentValue(), r.getMemberCount(), r.getPreviewMembers() == null ? null : r.getPreviewMembers().stream().map(RiskVariableConverter::local).toList(), r.getUpdateTime());
    }
    /** 转换 VariableDetailResponseBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableDetailResponseBody local(com.paytech.risk.api.decision.response.VariableDetailResponseBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableDetailResponseBody(r.getVariableCode(), r.getVariableName(), r.getSourceType(), r.getValueType(), r.getPointCode(), r.getUsages(), r.getSystemManaged(), r.getStatus(), r.getVersion(), r.getNumericUnit(), r.getCurrencyCode(), r.getCurrencyVariableCode(), r.getFormatCode(), r.getAllowedValuesVariableCode(), r.getCurrentValue(), r.getMemberCount(), r.getPreviewMembers() == null ? null : r.getPreviewMembers().stream().map(RiskVariableConverter::local).toList(), r.getUpdateTime(), r.getDescription(), r.getSourceContractCode(), r.getSourceContractVersion(), r.getSourceField(), r.getCreateBy(), r.getUpdateBy(), r.getCreateTime(), r.getValuesAsOf(), local(r.getAllowedValues()));
    }
    /** 转换 VariableListResponseBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableListResponseBody local(com.paytech.risk.api.decision.response.VariableListResponseBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableListResponseBody(r.getItems() == null ? null : r.getItems().stream().map(RiskVariableConverter::local).toList(), r.getPage(), r.getSize(), r.getTotal(), r.getMissingCodes(), r.getValuesAsOf());
    }
    /** 转换 Member，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.Member local(RiskVariableModels.Member r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.Member(r.getValue(), r.getDisplayName());
    }
    /** 转换 OrderedMember，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.OrderedMember local(RiskVariableModels.OrderedMember r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.OrderedMember(r.getValue(), r.getDisplayName(), r.getSortOrder());
    }
    /** 转换 AllowedValues，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.AllowedValues local(RiskVariableModels.AllowedValues r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.AllowedValues(r.getVariableCode(), r.getVariableName(), r.getVersion(), r.getMemberCount(), r.getPreviewMembers() == null ? null : r.getPreviewMembers().stream().map(RiskVariableConverter::local).toList());
    }
    /** 转换 VariableMembersResponseBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableMembersResponseBody local(com.paytech.risk.api.decision.response.VariableMembersResponseBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableMembersResponseBody(r.getVariableCode(), r.getVersion(), r.getItems() == null ? null : r.getItems().stream().map(RiskVariableConverter::local).toList(), r.getPage(), r.getSize(), r.getTotal(), r.getValuesAsOf());
    }
    /** 转换 ReplaceMembersResponseBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.ReplaceMembersResponseBody local(com.paytech.risk.api.decision.response.ReplaceMembersResponseBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.ReplaceMembersResponseBody(r.getVariableCode(), r.getVersion(), r.getMemberCount(), r.getUpdateTime());
    }
    /** 转换 DeleteVariableResponseBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.DeleteVariableResponseBody local(com.paytech.risk.api.decision.response.DeleteVariableResponseBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.DeleteVariableResponseBody(r.getVariableCode(), r.getStatus(), r.getVersion());
    }
    /** 转换 VariableReference，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableReference local(RiskVariableModels.VariableReference r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableReference(r.getConsumerType(), r.getRootId(), r.getVersionId(), r.getVersionNo(), r.getCode(), r.getName(), r.getScope(), r.getLocation(), r.getVariableCode());
    }
    /** 转换 VariableReferencesResponseBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableReferencesResponseBody local(com.paytech.risk.api.decision.response.VariableReferencesResponseBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableReferencesResponseBody(r.getItems() == null ? null : r.getItems().stream().map(RiskVariableConverter::local).toList(), r.getNextCursor(), r.getHasMore(), r.getScannedRows());
    }
    /** 转换 VariableManagementContextResponseBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableManagementContextResponseBody local(com.paytech.risk.api.decision.response.VariableManagementContextResponseBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableManagementContextResponseBody(r.getSupportedPoints() == null ? null : r.getSupportedPoints().stream().map(RiskVariableConverter::local).toList(), r.getSources() == null ? null : r.getSources().stream().map(RiskVariableConverter::local).toList(), r.getValueTypes(), r.getFormatProfiles() == null ? null : r.getFormatProfiles().stream().map(RiskVariableConverter::local).toList(), r.getNumericUnits(), local(r.getLimits()), r.getContracts() == null ? null : r.getContracts().stream().map(RiskVariableConverter::local).toList());
    }
    /** 转换 SupportedPoint，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.SupportedPoint local(RiskVariableModels.SupportedPoint r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.SupportedPoint(r.getPointCode(), r.getPointName());
    }
    /** 转换 SourceCapability，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.SourceCapability local(RiskVariableModels.SourceCapability r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.SourceCapability(r.getSourceType(), r.getCreateAllowed());
    }
    /** 转换 FormatProfile，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.FormatProfile local(RiskVariableModels.FormatProfile r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.FormatProfile(r.getFormatCode(), r.getLabel(), r.getHelp());
    }
    /** 转换 Limits，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.Limits local(RiskVariableModels.Limits r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.Limits(r.getMemberCount(), r.getMemberBodyBytes(), r.getLiteralSetCount());
    }
    /** 转换 SourceContract，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.SourceContract local(RiskVariableModels.SourceContract r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.SourceContract(r.getContractCode(), r.getContractVersion(), r.getPointCode(), r.getSourceType(), r.getNodeType(), r.getFields() == null ? null : r.getFields().stream().map(RiskVariableConverter::local).toList());
    }
    /** 转换 SourceField，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.SourceField local(RiskVariableModels.SourceField r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.SourceField(r.getFieldKey(), r.getValueType(), r.getRequired(), r.getDataClassification(), r.getAllowedUsages(), r.getNumericUnit(), r.getCurrencyFieldKey(), r.getSemanticRole());
    }
    /** 转换 VariableQueryContext，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableQueryContext local(RiskVariableModels.VariableQueryContext r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableQueryContext(r.getPointCode(), r.getInputContractCode(), r.getInputContractVersion(), r.getUsage());
    }
    /** 转换 VariableIssue，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableIssue local(RiskVariableModels.VariableIssue r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableIssue(r.getVariableCode(), r.getCode(), r.getMessage());
    }
    /** 转换 CreateVariableRequestBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.CreateVariableRequestBody local(com.paytech.risk.api.decision.request.CreateVariableRequestBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.CreateVariableRequestBody(r.getVariableCode(), r.getVariableName(), r.getDescription(), r.getSourceType(), r.getValueType(), r.getPointCode(), r.getSourceContractCode(), r.getSourceContractVersion(), r.getSourceField(), r.getUsages(), r.getFormatCode(), r.getNumericUnit(), r.getCurrencyCode(), r.getCurrencyVariableCode(), r.getAllowedValuesVariableCode(), r.getCurrentValue(), r.getStatus());
    }
    /** 转换 UpdateVariableRequestBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.UpdateVariableRequestBody local(com.paytech.risk.api.decision.request.UpdateVariableRequestBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.UpdateVariableRequestBody(r.getExpectedVersion(), r.getVariableName(), r.getDescription(), r.getCurrentValue(), r.getStatus());
    }
    /** 转换 ReplaceMembersRequestBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.ReplaceMembersRequestBody local(com.paytech.risk.api.decision.request.ReplaceMembersRequestBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.ReplaceMembersRequestBody(r.getExpectedVersion(), r.getMembers() == null ? null : r.getMembers().stream().map(RiskVariableConverter::local).toList());
    }
    /** 转换 MemberRequest，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.MemberRequest local(RiskVariableModels.MemberRequest r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.MemberRequest(r.getValue(), r.getDisplayName());
    }
    /** 转换 CreateVariableRequestBody，保留 null 与准确 Wire 值。 */
    static com.paytech.risk.api.decision.request.CreateVariableRequestBody owner(com.paytech.operations.console.bff.service.model.RiskVariableModels.CreateVariableRequestBody r) {
        if (r == null) return null;
        var value = new com.paytech.risk.api.decision.request.CreateVariableRequestBody(r.variableCode(), r.variableName(), r.description(), r.sourceType(), r.valueType(), r.pointCode(), r.sourceContractCode(), r.sourceContractVersion(), r.sourceField(), r.usages(), r.formatCode(), r.numericUnit(), r.currencyCode(), r.currencyVariableCode(), r.allowedValuesVariableCode(), r.currentValue(), r.status());
        value.setVelocity(owner(r.velocity()));
        value.setList(owner(r.list()));
        return value;
    }
    /** 转换实时累计参数；非累计来源为 null。 */
    static com.paytech.risk.api.decision.request.VelocityFeatureParameters owner(com.paytech.operations.console.bff.service.model.RiskVariableModels.VelocityParameters r) {
        if (r == null) return null;
        var value = new com.paytech.risk.api.decision.request.VelocityFeatureParameters();
        value.setEventCode(r.eventCode());
        value.setAnchorSubjectType(r.anchorSubjectType());
        value.setMetric(r.metric());
        value.setDistinctSubjectType(r.distinctSubjectType());
        value.setWindowSeconds(r.windowSeconds());
        value.setIncludeCurrent(Boolean.TRUE.equals(r.includeCurrent()));
        return value;
    }
    /** 转换名单命中参数；非名单来源为 null。 */
    static com.paytech.risk.api.decision.request.ListFeatureParameters owner(com.paytech.operations.console.bff.service.model.RiskVariableModels.ListParameters r) {
        if (r == null) return null;
        var value = new com.paytech.risk.api.decision.request.ListFeatureParameters();
        value.setListCode(r.listCode());
        value.setSubjectType(r.subjectType());
        return value;
    }
    /** 转换 UpdateVariableRequestBody，保留 null 与准确 Wire 值。 */
    static com.paytech.risk.api.decision.request.UpdateVariableRequestBody owner(com.paytech.operations.console.bff.service.model.RiskVariableModels.UpdateVariableRequestBody r) {
        if (r == null) return null;
        return new com.paytech.risk.api.decision.request.UpdateVariableRequestBody(r.expectedVersion(), r.variableName(), r.description(), r.currentValue(), r.status());
    }
    /** 转换 ReplaceMembersRequestBody，保留 null 与准确 Wire 值。 */
    static com.paytech.risk.api.decision.request.ReplaceMembersRequestBody owner(com.paytech.operations.console.bff.service.model.RiskVariableModels.ReplaceMembersRequestBody r) {
        if (r == null) return null;
        return new com.paytech.risk.api.decision.request.ReplaceMembersRequestBody(r.expectedVersion(), r.members() == null ? null : r.members().stream().map(RiskVariableConverter::owner).toList());
    }
    /** 转换 MemberRequest，保留 null 与准确 Wire 值。 */
    static RiskVariableModels.MemberRequest owner(com.paytech.operations.console.bff.service.model.RiskVariableModels.MemberRequest r) {
        if (r == null) return null;
        return new RiskVariableModels.MemberRequest(r.value(), r.displayName());
    }
}
