package com.paytech.platform.support.referencedata.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationChanged;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCurrency;

/** 在 Repository 修改原对象之前冻结币种审计值。 */
public final class CurrencySpecAuditFacts {

    /** 审计资源类型。 */
    public static final String TARGET_TYPE = "CURRENCY_SPEC";

    private CurrencySpecAuditFacts() {
    }

    /** 新建的原值为空，版本从 0 到初始版本。 */
    public static ConfigurationChanged created(String actor, long tenantId, ReferenceCurrency currency,
        String reason) {
        return fact(actor, tenantId, currency.getCurrencyCode(), null, 0,
            CurrencyAttributes.of(currency), currency.getStatus(), currency.getVersion(), reason,
            currency.getCreateTime());
    }

    /** 修改先冻结旧值；调用方只在保存成功后登记该事实。 */
    public static ConfigurationChanged updated(String actor, long tenantId, UpdateCurrencySpecContext context,
        String reason) {
        ReferenceCurrency currency = context.getCurrency();
        return fact(actor, tenantId, currency.getCurrencyCode(), currency, currency.getVersion(),
            context.getAttributes(), currency.getStatus(), currency.getVersion() + 1, reason, context.getUpdateTime());
    }

    /** 启停先冻结旧值；属性不变，只有状态变化。 */
    public static ConfigurationChanged statusChanged(String actor, long tenantId,
        ChangeCurrencySpecStatusContext context, String reason) {
        ReferenceCurrency currency = context.getCurrency();
        return fact(actor, tenantId, currency.getCurrencyCode(), currency, currency.getVersion(),
            CurrencyAttributes.of(currency), context.getStatus(), currency.getVersion() + 1, reason,
            context.getUpdateTime());
    }

    /**
     * 组装币种代码、标准属性、logo 与状态的前后值
     *
     * @param before 原行；新建为空
     * @param beforeVersion 原版本；新建为 0
     * @param after 目标属性
     * @param status 目标状态
     * @param version 目标版本
     * @return 审计事实
     */
    static ConfigurationChanged fact(String actor, long tenantId, String currencyCode, ReferenceCurrency before,
        int beforeVersion, CurrencyAttributes after, CurrencySpecStatus status, int version, String reason,
        Instant now) {
        CurrencyAttributes old = before == null ? null : CurrencyAttributes.of(before);
        var changes = List.of(
            change(ConfigurationField.CODE, before == null ? null : currencyCode, currencyCode),
            change(ConfigurationField.NUMERIC_CODE, old == null ? null : old.getNumericCode(), after.getNumericCode()),
            change(ConfigurationField.DISPLAY_NAME, old == null ? null : old.getName(), after.getName()),
            change(ConfigurationField.SYMBOL, old == null ? null : old.getSymbol(), after.getSymbol()),
            change(ConfigurationField.MINOR_UNIT, old == null ? null : Integer.toString(old.getMinorUnit()),
                Integer.toString(after.getMinorUnit())),
            change(ConfigurationField.ROUNDING_MODE, old == null ? null : old.getRoundingMode().getCode(),
                after.getRoundingMode().getCode()),
            change(ConfigurationField.LOGO_FILE_ADDRESS, old == null ? null : old.getLogoFileAddress(),
                after.getLogoFileAddress()),
            change(ConfigurationField.STATUS, before == null ? null : before.getStatus().getCode(), status.getCode()));
        return new ConfigurationChanged(new Identity(actor, tenantId), new Target(TARGET_TYPE, currencyCode),
            beforeVersion, version, changes, reason.strip(), now);
    }

    /** 单个受控字段的前后值。 */
    static ConfigurationFieldChange change(ConfigurationField field, String before, String after) {
        return new ConfigurationFieldChange(field, before, after);
    }
}
