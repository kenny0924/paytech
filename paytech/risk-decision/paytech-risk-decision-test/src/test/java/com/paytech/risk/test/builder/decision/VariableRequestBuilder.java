package com.paytech.risk.test.builder.decision;

import java.util.Arrays;
import java.util.List;

import com.paytech.risk.decision.api.RiskVariableModels.MemberRequest;
import com.paytech.risk.decision.api.request.CreateVariableRequestBody;
import com.paytech.risk.decision.api.request.ReplaceMembersRequestBody;
import com.paytech.risk.decision.api.request.UpdateVariableRequestBody;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.test.builder.decision.VariableBuilder.MemberSpec;

/**
 * 变量创建、更新与成员替换请求的测试 Builder
 * <li>{@link #create(String, VariableBuilder)} 按期望规格生成完整合法的创建请求，不适用字段显式为 null。</li>
 * <li>覆盖方法接受原始 Wire 字符串，build() 不修复非法值，负例保持用例设置的原样。</li>
 *
 * @author Kenny
 */
public final class VariableRequestBuilder {

    private final CreateVariableRequestBody body = new CreateVariableRequestBody();

    private VariableRequestBuilder() {}

    /** 与期望规格一致的创建请求；规格中的当前值即请求值。 */
    public static VariableRequestBuilder create(String code, VariableBuilder spec) {
        VariableRequestBuilder builder = new VariableRequestBuilder();
        CreateVariableRequestBody body = builder.body;
        body.setVariableCode(code);
        body.setVariableName(spec.variableName());
        body.setDescription(spec.description());
        body.setSourceType(spec.sourceType().getCode());
        body.setValueType(spec.valueType().getCode());
        body.setPointCode(spec.pointCode());
        body.setSourceContractCode(spec.sourceContractCode());
        body.setSourceContractVersion(spec.sourceContractVersion());
        body.setSourceField(spec.sourceField());
        body.setUsages(spec.usages().stream().map(VariableUsage::getCode).sorted().toList());
        body.setFormatCode(spec.formatCode().getCode());
        body.setNumericUnit(spec.numericUnit() == null ? null : spec.numericUnit().getCode());
        body.setCurrencyCode(spec.currencyCode());
        body.setCurrencyVariableCode(spec.currencyVariableCode());
        body.setAllowedValuesVariableCode(spec.allowedValuesVariableCode());
        body.setCurrentValue(spec.currentValue());
        body.setStatus(spec.status().getCode());
        return builder;
    }

    /** 更新请求：期望根版本及全部可变字段。 */
    public static UpdateVariableRequestBody update(String expectedVersion, String name, String description, Object value,
        String status) {
        return new UpdateVariableRequestBody(expectedVersion, name, description, value, status);
    }

    /** 成员替换请求：成员按给定顺序。 */
    public static ReplaceMembersRequestBody members(String expectedVersion, MemberRequest... members) {
        return new ReplaceMembersRequestBody(expectedVersion, Arrays.asList(members));
    }

    /** 由期望成员生成替换请求项。 */
    public static MemberRequest member(Object value, String displayName) {
        return new MemberRequest(value, displayName);
    }

    /** 由夹具成员规格生成替换请求项。 */
    public static MemberRequest member(MemberSpec spec) {
        return new MemberRequest(spec.value(), spec.displayName());
    }

    public VariableRequestBuilder variableCode(String value) {
        body.setVariableCode(value);
        return this;
    }

    public VariableRequestBuilder variableName(String value) {
        body.setVariableName(value);
        return this;
    }

    public VariableRequestBuilder description(String value) {
        body.setDescription(value);
        return this;
    }

    public VariableRequestBuilder sourceType(String value) {
        body.setSourceType(value);
        return this;
    }

    public VariableRequestBuilder valueType(String value) {
        body.setValueType(value);
        return this;
    }

    public VariableRequestBuilder pointCode(String value) {
        body.setPointCode(value);
        return this;
    }

    public VariableRequestBuilder sourceContractCode(String value) {
        body.setSourceContractCode(value);
        return this;
    }

    public VariableRequestBuilder sourceContractVersion(Integer value) {
        body.setSourceContractVersion(value);
        return this;
    }

    public VariableRequestBuilder sourceField(String value) {
        body.setSourceField(value);
        return this;
    }

    public VariableRequestBuilder usages(List<String> value) {
        body.setUsages(value);
        return this;
    }

    public VariableRequestBuilder formatCode(String value) {
        body.setFormatCode(value);
        return this;
    }

    public VariableRequestBuilder numericUnit(String value) {
        body.setNumericUnit(value);
        return this;
    }

    public VariableRequestBuilder currencyCode(String value) {
        body.setCurrencyCode(value);
        return this;
    }

    public VariableRequestBuilder currencyVariableCode(String value) {
        body.setCurrencyVariableCode(value);
        return this;
    }

    public VariableRequestBuilder allowedValuesVariableCode(String value) {
        body.setAllowedValuesVariableCode(value);
        return this;
    }

    public VariableRequestBuilder currentValue(Object value) {
        body.setCurrentValue(value);
        return this;
    }

    public VariableRequestBuilder status(String value) {
        body.setStatus(value);
        return this;
    }

    public CreateVariableRequestBody build() {
        return body;
    }
}
