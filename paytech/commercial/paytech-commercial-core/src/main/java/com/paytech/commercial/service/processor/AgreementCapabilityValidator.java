package com.paytech.commercial.service.processor;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import com.paytech.commercial.api.enums.CapabilityLimitCode;
import com.paytech.commercial.service.processor.request.AgreementCapabilityTermsRequest;
import com.paytech.commercial.service.processor.request.AgreementCapabilityTermsRequest.Limit;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;

/**
 * 开通项新增与修改共用的输入规则（database.md §2.5 / 2.6、§5.2）；无副作用，只依赖请求
 * <li>生效区间合法；保证金比例与天数成组且比例在 0–1；约束行不重复、值按类别规范化，REQUIRE_3DS 只能有一行且值为 1。</li>
 */
final class AgreementCapabilityValidator {
    /** 纯静态工具。 */
    private AgreementCapabilityValidator() {
    }

    /**
     * 校验开通项条款
     *
     * @param terms 开通项条款
     */
    static void checkTerms(AgreementCapabilityTermsRequest terms) {
        if (terms.getEffectiveTo() != null && !terms.getEffectiveTo().isAfter(terms.getEffectiveFrom())) {
            throw new BizCheckFailException("开通项生效终点必须晚于起点");
        }
        BigDecimal rate = terms.getRollingReserveRate();
        if ((rate == null) != (terms.getRollingReserveDays() == null)) {
            throw new BizCheckFailException("滚动保证金比例与天数必须同时提供或同时为空");
        }
        if (rate != null && (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0 || rate.scale() > 10)) {
            throw new BizCheckFailException("滚动保证金比例必须在 0 到 1 之间且最多 10 位小数: " + rate.toPlainString());
        }
        checkLimits(terms);
    }

    /** 约束行规范化与唯一性。 */
    static void checkLimits(AgreementCapabilityTermsRequest terms) {
        Set<String> seen = new HashSet<>();
        for (Limit limit : terms.getLimits()) {
            if (!seen.add(limit.getLimitCode().getCode() + ":" + limit.getLimitValue())) {
                throw new InvalidParameterException("limits 不得重复: " + limit.getLimitCode().getCode());
            }
            if (!validValue(limit.getLimitCode(), limit.getLimitValue())) {
                throw new BizCheckFailException("约束值不符合类别格式: " + limit.getLimitCode().getCode() + "="
                    + limit.getLimitValue());
            }
        }
    }

    /** CURRENCY 三位大写、CONSUMER_COUNTRY 两位大写、REQUIRE_3DS 固定为 1。 */
    static boolean validValue(CapabilityLimitCode code, String value) {
        return switch (code) {
            case CURRENCY -> value.matches("[A-Z]{3}");
            case CONSUMER_COUNTRY -> value.matches("[A-Z]{2}");
            case REQUIRE_3DS -> "1".equals(value);
        };
    }
}
