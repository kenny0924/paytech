package com.paytech.commercial.test.builder;

import java.util.ArrayList;
import java.util.List;

import com.paytech.commercial.api.agreement.request.AddAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.request.CapabilityLimitEntry;
import com.paytech.commercial.api.agreement.request.UpdateAgreementCapabilityRequestBody;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.api.enums.CapabilityLimitCode;

/**
 * 开通项新增 / 修改请求体 Builder：默认 AE993 上的 CARD_VISA，2026-09-01 起，USD 与 AED 两个币种约束、无保证金
 * <li>不访问数据库；build() 不修复负例。</li>
 */
public final class AgreementCapabilityBodyBuilder {
    private String entityNo = AgreementBuilder.ENTITY_NO;
    private String capabilityCode = CommercialCodes.Capability.CARD_VISA;
    private String status = AgreementCapabilityStatus.ENABLED.getCode();
    private String effectiveFrom = "2026-09-01T00:00:00Z";
    private String effectiveTo;
    private String rollingReserveRate;
    private Integer rollingReserveDays;
    private final List<CapabilityLimitEntry> limits = new ArrayList<>(List.of(limit(CapabilityLimitCode.CURRENCY, "USD"),
        limit(CapabilityLimitCode.CURRENCY, "AED")));

    private AgreementCapabilityBodyBuilder() {
    }

    /** 合法默认值。 */
    public static AgreementCapabilityBodyBuilder template() {
        return new AgreementCapabilityBodyBuilder();
    }

    /** 一条约束。 */
    public static CapabilityLimitEntry limit(CapabilityLimitCode code, String value) {
        CapabilityLimitEntry entry = new CapabilityLimitEntry();
        entry.setLimitCode(code.getCode());
        entry.setLimitValue(value);
        return entry;
    }

    /** 设置 OE。 */
    public AgreementCapabilityBodyBuilder entityNo(String value) {
        entityNo = value;
        return this;
    }

    /** 设置可开通项代码。 */
    public AgreementCapabilityBodyBuilder capabilityCode(String value) {
        capabilityCode = value;
        return this;
    }

    /** 设置修改的目标状态。 */
    public AgreementCapabilityBodyBuilder status(AgreementCapabilityStatus value) {
        status = value.getCode();
        return this;
    }

    /** 设置生效终点。 */
    public AgreementCapabilityBodyBuilder effectiveTo(String value) {
        effectiveTo = value;
        return this;
    }

    /** 设置保证金比例与天数。 */
    public AgreementCapabilityBodyBuilder reserve(String rate, Integer days) {
        rollingReserveRate = rate;
        rollingReserveDays = days;
        return this;
    }

    /** 整体替换约束行。 */
    public AgreementCapabilityBodyBuilder limits(CapabilityLimitEntry... values) {
        limits.clear();
        limits.addAll(List.of(values));
        return this;
    }

    /** 新增请求体。 */
    public AddAgreementCapabilityRequestBody buildAdd(long expectedVersion) {
        AddAgreementCapabilityRequestBody body = new AddAgreementCapabilityRequestBody();
        body.setExpectedChangeVersion(expectedVersion);
        body.setEntityNo(entityNo);
        body.setCapabilityCode(capabilityCode);
        body.setEffectiveFrom(effectiveFrom);
        body.setEffectiveTo(effectiveTo);
        body.setRollingReserveRate(rollingReserveRate);
        body.setRollingReserveDays(rollingReserveDays);
        body.setLimits(new ArrayList<>(limits));
        return body;
    }

    /** 修改请求体。 */
    public UpdateAgreementCapabilityRequestBody buildUpdate(long expectedVersion) {
        UpdateAgreementCapabilityRequestBody body = new UpdateAgreementCapabilityRequestBody();
        body.setExpectedChangeVersion(expectedVersion);
        body.setStatus(status);
        body.setEffectiveFrom(effectiveFrom);
        body.setEffectiveTo(effectiveTo);
        body.setRollingReserveRate(rollingReserveRate);
        body.setRollingReserveDays(rollingReserveDays);
        body.setLimits(new ArrayList<>(limits));
        return body;
    }
}
