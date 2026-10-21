package com.paytech.billing.test.builder;

import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecResponseBody;

/**
 * platform-support 单条币种规则响应构建器
 * <li>template() 给出 Platform 种子同形的 USD：2 位、HALF_UP、ACTIVE、版本 1。</li>
 * <li>负例按需覆盖字段（非法代码、越界小数位、目录外舍入方式或状态），build() 不修复非法输入。</li>
 */
public final class CurrencySpecResponseBuilder {
    /** 标准币种，与 {@link BillingFactRequestBuilder#CURRENCY} 相同。 */
    public static final String USD = BillingFactRequestBuilder.CURRENCY;

    /** 第二个两位小数币种。 */
    public static final String EUR = "EUR";

    /** 零位小数币种。 */
    public static final String JPY = "JPY";

    /** 用作草稿币种。 */
    public static final String GBP = "GBP";

    /** 固定更新时间。 */
    static final String UPDATE_TIME = "2026-10-20T00:00:00.000000Z";

    private final CurrencySpecResponseBody body = new CurrencySpecResponseBody();

    /** 只能从模板开始。 */
    private CurrencySpecResponseBuilder() {
    }

    /** USD、2 位、HALF_UP、ACTIVE。 */
    public static CurrencySpecResponseBuilder template() {
        CurrencySpecResponseBuilder builder = new CurrencySpecResponseBuilder();
        builder.body.setCurrency(USD);
        builder.body.setMinorUnit(2);
        builder.body.setRoundingMode(CurrencyRoundingMode.HALF_UP.getCode());
        builder.body.setStatus(CurrencySpecStatus.ACTIVE.getCode());
        builder.body.setVersion(1);
        builder.body.setUpdateTime(UPDATE_TIME);
        return builder;
    }

    /** 币种代码。 */
    public CurrencySpecResponseBuilder currency(String currency) {
        body.setCurrency(currency);
        return this;
    }

    /** 小数位。 */
    public CurrencySpecResponseBuilder minorUnit(int minorUnit) {
        body.setMinorUnit(minorUnit);
        return this;
    }

    /** 舍入方式原始代码。 */
    public CurrencySpecResponseBuilder roundingMode(String roundingMode) {
        body.setRoundingMode(roundingMode);
        return this;
    }

    /** 状态。 */
    public CurrencySpecResponseBuilder status(CurrencySpecStatus status) {
        return status(status.getCode());
    }

    /** 状态原始代码。 */
    public CurrencySpecResponseBuilder status(String status) {
        body.setStatus(status);
        return this;
    }

    /** 构建响应 DTO。 */
    public CurrencySpecResponseBody build() {
        return body;
    }
}
