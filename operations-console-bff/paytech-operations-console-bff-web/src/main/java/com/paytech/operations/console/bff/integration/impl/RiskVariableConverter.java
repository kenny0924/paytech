package com.paytech.operations.console.bff.integration.impl;

import com.paytech.risk.api.decision.RiskVariableModels;

/** 变量 Owner DTO 的明确投影；当前值不进入 BFF 持久化或缓存。 */
final class RiskVariableConverter {
    private RiskVariableConverter() { }
    /** 转换 VariableSummary，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableSummary local(RiskVariableModels.VariableSummary r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableSummary(r.variableCode(), r.variableName(), r.sourceType(), r.valueType(), r.pointCode(), r.usages(), r.systemManaged(), r.status(), r.version(), r.numericUnit(), r.currencyCode(), r.currencyVariableCode(), r.formatCode(), r.allowedValuesVariableCode(), r.currentValue(), r.memberCount(), r.previewMembers() == null ? null : r.previewMembers().stream().map(RiskVariableConverter::local).toList(), r.updateTime());
    }
    /** 转换 VariableDetailResponseBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableDetailResponseBody local(RiskVariableModels.VariableDetailResponseBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableDetailResponseBody(r.variableCode(), r.variableName(), r.sourceType(), r.valueType(), r.pointCode(), r.usages(), r.systemManaged(), r.status(), r.version(), r.numericUnit(), r.currencyCode(), r.currencyVariableCode(), r.formatCode(), r.allowedValuesVariableCode(), r.currentValue(), r.memberCount(), r.previewMembers() == null ? null : r.previewMembers().stream().map(RiskVariableConverter::local).toList(), r.updateTime(), r.description(), r.sourceContractCode(), r.sourceContractVersion(), r.sourceField(), r.createBy(), r.updateBy(), r.createTime(), r.valuesAsOf(), local(r.allowedValues()));
    }
    /** 转换 VariableListResponseBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableListResponseBody local(RiskVariableModels.VariableListResponseBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableListResponseBody(r.items() == null ? null : r.items().stream().map(RiskVariableConverter::local).toList(), r.page(), r.size(), r.total(), r.missingCodes(), r.valuesAsOf());
    }
    /** 转换 Member，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.Member local(RiskVariableModels.Member r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.Member(r.value(), r.displayName());
    }
    /** 转换 OrderedMember，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.OrderedMember local(RiskVariableModels.OrderedMember r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.OrderedMember(r.value(), r.displayName(), r.sortOrder());
    }
    /** 转换 AllowedValues，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.AllowedValues local(RiskVariableModels.AllowedValues r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.AllowedValues(r.variableCode(), r.variableName(), r.version(), r.memberCount(), r.previewMembers() == null ? null : r.previewMembers().stream().map(RiskVariableConverter::local).toList());
    }
    /** 转换 VariableMembersResponseBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableMembersResponseBody local(RiskVariableModels.VariableMembersResponseBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableMembersResponseBody(r.variableCode(), r.version(), r.items() == null ? null : r.items().stream().map(RiskVariableConverter::local).toList(), r.page(), r.size(), r.total(), r.valuesAsOf());
    }
    /** 转换 ReplaceMembersResponseBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.ReplaceMembersResponseBody local(RiskVariableModels.ReplaceMembersResponseBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.ReplaceMembersResponseBody(r.variableCode(), r.version(), r.memberCount(), r.updateTime());
    }
    /** 转换 DeleteVariableResponseBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.DeleteVariableResponseBody local(RiskVariableModels.DeleteVariableResponseBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.DeleteVariableResponseBody(r.variableCode(), r.status(), r.version());
    }
    /** 转换 VariableReference，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableReference local(RiskVariableModels.VariableReference r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableReference(r.consumerType(), r.rootId(), r.versionId(), r.versionNo(), r.code(), r.name(), r.scope(), r.location(), r.variableCode());
    }
    /** 转换 VariableReferencesResponseBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableReferencesResponseBody local(RiskVariableModels.VariableReferencesResponseBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableReferencesResponseBody(r.items() == null ? null : r.items().stream().map(RiskVariableConverter::local).toList(), r.nextCursor(), r.hasMore(), r.scannedRows());
    }
    /** 转换 VariableManagementContextResponseBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableManagementContextResponseBody local(RiskVariableModels.VariableManagementContextResponseBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableManagementContextResponseBody(r.supportedPoints() == null ? null : r.supportedPoints().stream().map(RiskVariableConverter::local).toList(), r.sources() == null ? null : r.sources().stream().map(RiskVariableConverter::local).toList(), r.valueTypes(), r.formatProfiles() == null ? null : r.formatProfiles().stream().map(RiskVariableConverter::local).toList(), r.numericUnits(), local(r.limits()), r.contracts() == null ? null : r.contracts().stream().map(RiskVariableConverter::local).toList());
    }
    /** 转换 SupportedPoint，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.SupportedPoint local(RiskVariableModels.SupportedPoint r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.SupportedPoint(r.pointCode(), r.pointName());
    }
    /** 转换 SourceCapability，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.SourceCapability local(RiskVariableModels.SourceCapability r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.SourceCapability(r.sourceType(), r.createAllowed());
    }
    /** 转换 FormatProfile，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.FormatProfile local(RiskVariableModels.FormatProfile r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.FormatProfile(r.formatCode(), r.label(), r.help());
    }
    /** 转换 Limits，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.Limits local(RiskVariableModels.Limits r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.Limits(r.memberCount(), r.memberBodyBytes(), r.literalSetCount());
    }
    /** 转换 SourceContract，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.SourceContract local(RiskVariableModels.SourceContract r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.SourceContract(r.contractCode(), r.contractVersion(), r.pointCode(), r.sourceType(), r.nodeType(), r.fields() == null ? null : r.fields().stream().map(RiskVariableConverter::local).toList());
    }
    /** 转换 SourceField，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.SourceField local(RiskVariableModels.SourceField r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.SourceField(r.fieldKey(), r.valueType(), r.required(), r.dataClassification(), r.allowedUsages(), r.numericUnit(), r.currencyFieldKey(), r.semanticRole());
    }
    /** 转换 VariableQueryContext，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableQueryContext local(RiskVariableModels.VariableQueryContext r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableQueryContext(r.pointCode(), r.inputContractCode(), r.inputContractVersion(), r.usage());
    }
    /** 转换 VariableIssue，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableIssue local(RiskVariableModels.VariableIssue r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableIssue(r.variableCode(), r.code(), r.message());
    }
    /** 转换 CreateVariableRequestBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.CreateVariableRequestBody local(RiskVariableModels.CreateVariableRequestBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.CreateVariableRequestBody(r.variableCode(), r.variableName(), r.description(), r.sourceType(), r.valueType(), r.pointCode(), r.sourceContractCode(), r.sourceContractVersion(), r.sourceField(), r.usages(), r.formatCode(), r.numericUnit(), r.currencyCode(), r.currencyVariableCode(), r.allowedValuesVariableCode(), r.currentValue(), r.status());
    }
    /** 转换 UpdateVariableRequestBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.UpdateVariableRequestBody local(RiskVariableModels.UpdateVariableRequestBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.UpdateVariableRequestBody(r.expectedVersion(), r.variableName(), r.description(), r.currentValue(), r.status());
    }
    /** 转换 ReplaceMembersRequestBody，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.ReplaceMembersRequestBody local(RiskVariableModels.ReplaceMembersRequestBody r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.ReplaceMembersRequestBody(r.expectedVersion(), r.members() == null ? null : r.members().stream().map(RiskVariableConverter::local).toList());
    }
    /** 转换 MemberRequest，保留 null 与准确 Wire 值。 */
    static com.paytech.operations.console.bff.service.model.RiskVariableModels.MemberRequest local(RiskVariableModels.MemberRequest r) {
        if (r == null) return null;
        return new com.paytech.operations.console.bff.service.model.RiskVariableModels.MemberRequest(r.value(), r.displayName());
    }
    /** 转换 CreateVariableRequestBody，保留 null 与准确 Wire 值。 */
    static RiskVariableModels.CreateVariableRequestBody owner(com.paytech.operations.console.bff.service.model.RiskVariableModels.CreateVariableRequestBody r) {
        if (r == null) return null;
        return new RiskVariableModels.CreateVariableRequestBody(r.variableCode(), r.variableName(), r.description(), r.sourceType(), r.valueType(), r.pointCode(), r.sourceContractCode(), r.sourceContractVersion(), r.sourceField(), r.usages(), r.formatCode(), r.numericUnit(), r.currencyCode(), r.currencyVariableCode(), r.allowedValuesVariableCode(), r.currentValue(), r.status());
    }
    /** 转换 UpdateVariableRequestBody，保留 null 与准确 Wire 值。 */
    static RiskVariableModels.UpdateVariableRequestBody owner(com.paytech.operations.console.bff.service.model.RiskVariableModels.UpdateVariableRequestBody r) {
        if (r == null) return null;
        return new RiskVariableModels.UpdateVariableRequestBody(r.expectedVersion(), r.variableName(), r.description(), r.currentValue(), r.status());
    }
    /** 转换 ReplaceMembersRequestBody，保留 null 与准确 Wire 值。 */
    static RiskVariableModels.ReplaceMembersRequestBody owner(com.paytech.operations.console.bff.service.model.RiskVariableModels.ReplaceMembersRequestBody r) {
        if (r == null) return null;
        return new RiskVariableModels.ReplaceMembersRequestBody(r.expectedVersion(), r.members() == null ? null : r.members().stream().map(RiskVariableConverter::owner).toList());
    }
    /** 转换 MemberRequest，保留 null 与准确 Wire 值。 */
    static RiskVariableModels.MemberRequest owner(com.paytech.operations.console.bff.service.model.RiskVariableModels.MemberRequest r) {
        if (r == null) return null;
        return new RiskVariableModels.MemberRequest(r.value(), r.displayName());
    }
}
