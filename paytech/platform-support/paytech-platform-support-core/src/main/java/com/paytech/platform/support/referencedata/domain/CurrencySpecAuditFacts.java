package com.paytech.platform.support.referencedata.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationChanged;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;

/** 在 Repository 修改原对象之前冻结币种规则审计值。 */
public final class CurrencySpecAuditFacts {

    /** 审计资源类型。 */
    public static final String TARGET_TYPE = "CURRENCY_SPEC";

    private CurrencySpecAuditFacts() {
    }

    /** 新建的原值为空，版本从 0 到初始版本。 */
    public static ConfigurationChanged created(String actor, long tenantId, CurrencySpec spec, String reason) {
        return fact(actor, tenantId, spec, null, spec.getMinorUnit(), spec.getRoundingMode(), spec.getStatus(),
            spec.getVersion(), reason, spec.getCreateTime());
    }

    /** 变更先冻结旧值；调用方只在保存成功后登记该事实。 */
    public static ConfigurationChanged changed(String actor, long tenantId, CurrencySpec spec, int minorUnit,
        CurrencyRoundingMode roundingMode, CurrencySpecStatus status, String reason, Instant now) {
        return fact(actor, tenantId, spec, spec, minorUnit, roundingMode, status, spec.getVersion() + 1, reason, now);
    }

    /** 组装币种、小数位、舍入方式与状态的前后值。 */
    static ConfigurationChanged fact(String actor, long tenantId, CurrencySpec target, CurrencySpec before,
        int minorUnit, CurrencyRoundingMode roundingMode, CurrencySpecStatus status, int version, String reason,
        Instant now) {
        var changes = List.of(
            new ConfigurationFieldChange(ConfigurationField.CODE, before == null ? null : before.getCurrency(),
                target.getCurrency()),
            new ConfigurationFieldChange(ConfigurationField.MINOR_UNIT,
                before == null ? null : Integer.toString(before.getMinorUnit()), Integer.toString(minorUnit)),
            new ConfigurationFieldChange(ConfigurationField.ROUNDING_MODE,
                before == null ? null : before.getRoundingMode().getCode(), roundingMode.getCode()),
            new ConfigurationFieldChange(ConfigurationField.STATUS,
                before == null ? null : before.getStatus().getCode(), status.getCode()));
        return new ConfigurationChanged(new Identity(actor, tenantId), new Target(TARGET_TYPE, target.getCurrency()),
            before == null ? 0 : before.getVersion(), version, changes, reason.strip(), now);
    }
}
