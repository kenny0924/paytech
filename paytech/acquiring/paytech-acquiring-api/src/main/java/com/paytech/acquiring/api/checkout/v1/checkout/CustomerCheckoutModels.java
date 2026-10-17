package com.paytech.acquiring.api.checkout.v1.checkout;

import java.time.Instant;
import java.util.List;

/** Customer公开字段；不包含卡、Vault引用、OE或浏览器并发版本。 */
public final class CustomerCheckoutModels {
    /** 仅提供协议模型。 */
    private CustomerCheckoutModels() {}

    /** 六项币种主单位金额字符串；USD 按两位小数输出，例如 "10.00"；新创建订单已确定税、舍入及总额。 */
    public record CheckoutAmountsInfo(
            String merchandiseAmount,
            String discountAmount,
            String netAmount,
            String taxAmount,
            String roundingAmount,
            String totalAmount) {}

    /** 冻结法律卖方；注册号、税号及联系方式允许按事实为空。 */
    public record SellerInfo(
            String legalName,
            String registrationNumber,
            String taxRegistrationNumber,
            String addressText,
            String supportEmail,
            String supportPhone) {}

    /** 指向准确不可变文件的HTTPS链接；永久链接的期限为空。 */
    public record DocumentInfo(
            String documentType, String title, String url, Instant urlExpiresAt) {}

    /** 已展示的冻结合同及恰好三类文件。 */
    public record LegalInfo(String consumerContractId, List<DocumentInfo> documents) {}

}
