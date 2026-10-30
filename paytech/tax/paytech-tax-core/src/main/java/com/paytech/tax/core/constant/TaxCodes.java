package com.paytech.tax.core.constant;

/**
 * tax 业务依赖的稳定开放代码与单值目录项
 * <li>这些列在本期只有一个合法值，或是由来源 Owner 维护的开放目录；集中定义，避免散落魔法字符串。</li>
 */
public final class TaxCodes {
    /** 来源系统：acquiring。 */
    public static final String SOURCE_SYSTEM_ACQUIRING = "acquiring";

    /** 来源对象类型：订单。 */
    public static final String SOURCE_OBJECT_ORDER = "ORDER";

    /** 税务范围：销售。 */
    public static final String SCOPE_SALE = "SALE";

    /** 供应类型：销售。 */
    public static final String SUPPLY_SALE = "SALE";

    /** 来源事实类型：订单支付成功。 */
    public static final String FACT_ORDER_PAYMENT_SUCCEEDED = "ORDER_PAYMENT_SUCCEEDED";

    /** 税点依据：付款（预付 SaaS）。 */
    public static final String TAX_POINT_PAYMENT = "PAYMENT";

    /** 买方纳税人状态：未登记（本期一律按 B2C）。 */
    public static final String BUYER_NON_REGISTERED = "NON_REGISTERED";

    /** 底稿阻断原因：缺法定汇率。 */
    public static final String REPORTING_FX_RATE_MISSING = "FX_RATE_MISSING";

    /** 同币种汇率来源。 */
    public static final String FX_SAME_CURRENCY = "SAME_CURRENCY";

    /** 跨币种汇率来源：法定汇率端口。 */
    public static final String FX_STATUTORY = "STATUTORY";

    /** 单据范围：销售。 */
    public static final String DOCUMENT_SCOPE_SALE = "SALE";

    /** Platform 文件上传原因：简化税票（technique 12，Platform 写路由登记 tax-service / PRIVATE）。 */
    public static final String FILE_REASON_TAX_RECEIPT = "TAX_RECEIPT";

    /** 单据内容结构版本。 */
    public static final int DOCUMENT_CONTENT_SCHEMA_VERSION = 1;

    /** NOT_COLLECTED 组件缺省原因码（地区包未配置 applicability.notCollectedReasonCode 时）。 */
    public static final String DEFAULT_NOT_COLLECTED_REASON = "NO_COLLECTION_REQUIRED";

    /** 纯静态常量。 */
    private TaxCodes() {
    }
}
