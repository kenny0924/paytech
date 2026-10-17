package com.paytech.acquiring.test.builder.checkout;

import com.paytech.acquiring.core.dal.dataobject.customer.FrozenCheckoutRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paytech.acquiring.api.client.v1.enums.OrderCreationMode;
import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderFundsAvailabilityProjection;
import com.paytech.acquiring.core.dal.dataobject.order.OrderItem;
import com.paytech.acquiring.core.dal.dataobject.order.OrderRefundPosition;
import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;
import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.acquiring.domain.enums.FundsAvailabilityStatus;
import com.paytech.acquiring.domain.enums.OrderStatus;

import lombok.Setter;
import lombok.experimental.Accessors;

/** 原子写入边界的最小合法数据；只组装对象，不查询、生成业务事实或调用生产计算。 */
@Setter
@Accessors(fluent = true)
public final class CreateCheckoutContextBuilder {
    private long orderId;
    private long clientId;
    private Instant now = Instant.parse("2026-09-08T00:00:00Z");

    /** 本类只用于事务用例；真实资源 ID 由 Mocker 预留并负责清理。 */
    public static CreateCheckoutContextBuilder template() {
        return new CreateCheckoutContextBuilder();
    }

    /** 同一个 ID 可用于不同表的主键，各表内仍保持唯一。 */
    public CreateCheckoutContext build() {
        var one = new BigDecimal("1.00000000");
        var zero = new BigDecimal("0.00000000");
        var suffix = String.format(java.util.Locale.ROOT, "%030d", orderId);
        var order = CommerceOrder.builder().orderId(orderId).orderNo("ord_" + suffix).clientId(clientId)
            .clientOrderId("transaction-order").creationMode(OrderCreationMode.HOSTED_CHECKOUT)
            .consumerCountryCode("US").merchandiseAmount(one).discountAmount(zero).netAmount(one)
            .entityNo("AE001").entitySourceType("PLATFORM").industryCode("RETAIL").mccCode("5999")
            .taxAmount(zero).roundingAmount(zero).totalAmount(one).currency("USD").orderStatus(OrderStatus.OPEN).expiresAt(now.plusSeconds(1800))
            .version(1L).createTime(now).updateTime(now).build();
        var item = OrderItem.builder().orderItemId(orderId).orderId(orderId).clientItemId("line-1")
            .itemName("Purchase").productTypeCode("SERVICE").taxCategoryCode("STANDARD")
            .quantity(one).unitAmount(one).lineGrossAmount(one).lineDiscountAmount(zero).lineNetAmount(one)
            .currency("USD").sortNo(0).createTime(now).build();
        var refund = OrderRefundPosition.builder().orderRefundPositionId(orderId).orderId(orderId)
            .refundableBaseAmount(zero).reservedAmount(zero).refundedAmount(zero).currency("USD")
            .version(1L).createTime(now).updateTime(now).build();
        var funds = OrderFundsAvailabilityProjection.builder().orderFundsAvailabilityProjectionId(orderId)
            .orderId(orderId).fundsAvailabilityStatus(FundsAvailabilityStatus.NOT_APPLICABLE).currency("USD")
            .asOf(now).version(1L).createTime(now).updateTime(now).build();
        var session = CheckoutSession.builder().checkoutSessionId(orderId).orderId(orderId)
            .publicToken("chk_" + suffix).publicOrigin("https://checkout.local.test")
            .openedAt(now).sessionStatus(CheckoutSessionStatus.OPEN).version(1L).createTime(now).updateTime(now).build();
        var frozen = FrozenCheckoutRecord.builder()
            .orderContextId(orderId).orderId(orderId).commercialContextTime(now)
            .affiliationId(101).entityScopeId(102).entitySourceRelationId(103).clientPricingVersionId(104)
            .riskCheckoutAssessmentId(orderId).financeTaxSnapshotId(orderId).financeTaxSnapshotHash("d".repeat(64))
            .consumerCountryCode("US").locale("en").orderLegalId(orderId).clientAgreementId(105)
            .consumerTermsDocumentId(201).consumerTermsFileId(301).consumerTermsContentHash("a".repeat(64))
            .refundPolicyDocumentId(202).refundPolicyFileId(302).refundPolicyContentHash("b".repeat(64))
            .privacyNoticeDocumentId(203).privacyNoticeFileId(303).privacyNoticeContentHash("c".repeat(64))
            .sellerDisclosureId(106).publicLegalName("Test Seller").publicRegistrationNumber("TEST-REG")
            .publicAddressText("Test Address").publicSupportEmail("support@example.invalid")
            .consumerContractId(orderId).renderedFileId(orderId).renderedContentHash("e".repeat(64)).presentedAt(now).build();
        return new CreateCheckoutContext(frozen, order, List.of(item), refund, funds, session);
    }
}
