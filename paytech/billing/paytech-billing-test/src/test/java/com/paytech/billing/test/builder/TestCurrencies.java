package com.paytech.billing.test.builder;

/** 测试用币种代码；默认快照只有 USD（2 位、HALF_UP、ACTIVE），其他币种由用例经参考数据 Mocker 按需配置。 */
public final class TestCurrencies {
    /** 标准币种，与 {@link BillingFactRequestBuilder#CURRENCY} 相同。 */
    public static final String USD = BillingFactRequestBuilder.CURRENCY;

    /** 第二个两位小数币种。 */
    public static final String EUR = "EUR";

    /** 零位小数币种。 */
    public static final String JPY = "JPY";

    /** 用作草稿币种。 */
    public static final String GBP = "GBP";

    private TestCurrencies() {
    }
}
