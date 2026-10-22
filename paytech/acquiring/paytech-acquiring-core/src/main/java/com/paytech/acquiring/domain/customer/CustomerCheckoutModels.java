package com.paytech.acquiring.domain.customer;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.paytech.acquiring.core.dal.dataobject.customer.FrozenCheckoutRecord;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderItem;
import com.paytech.acquiring.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;

import jakarta.validation.constraints.Email;

/** Customer 订单及初始化模型；瞬时卡提交单独定义在 CheckoutSubmissionModels。 */
public final class CustomerCheckoutModels {
    /** 禁止实例化模型容器。 */
    private CustomerCheckoutModels() {}

    /** 完整替换资料；只在当前请求与受控 Vault 调用中出现原值。 */
    public record ConsumerDetails(String fullName, @Email String email, Address billingAddress) {
    }

    /** 与计税国家独立的完整账单地址。 */
    public record Address(String countryCode, String addressLine1, String addressLine2, String region, String city,
        String postalCode) {
    }

    /** 姓名与地址 Profile 不重复邮箱。 */
    public record ConsumerProfile(String fullName, Address billingAddress) {
    }

    /** 仅本库 Consumer 表保存的受保护引用。 */
    public record ProtectedConsumer(String emailToken, String profileToken) {
        /** 保护引用必须完整且符合存储边界；具体内容仍由保护 Owner 定义。 */
        public void validate() {
            if (emailToken == null || emailToken.isBlank() || emailToken.length() > 128 || profileToken == null
                || profileToken.isBlank() || profileToken.length() > 128) {
                throw new CheckoutUpstreamProtocolException("消费者保护引用不完整");
            }
        }
    }

    /** 订单创建时的商业初始化输入，不含消费者原值或 HTTP 幂等键。 */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class InitializationRequest {
        /** 原订单主键，同时作为外部初始化的业务身份。 */
        private final long orderId;

        /** 原公开订单编号。 */
        private final String orderNo;

        /** 原 Client。 */
        private final long clientId;

        /** 创建时已确认的计税国家。 */
        private final String country;

        /** 订单币种。 */
        private final String currency;

        /** 本次商业事实时间。 */
        private final Instant contextTime;

        /** 优惠前商品总额。 */
        private final BigDecimal merchandiseAmount;

        /** 商品优惠总额，无折扣为 0。 */
        private final BigDecimal discountAmount;

        /** 税前净额。 */
        private final BigDecimal netAmount;

        /** 商品行。 */
        private final List<OrderItem> items;
    }

    /** CCT 返回已逐候选验证的准确 OE、合同和定价版本。 */
    @lombok.Builder(toBuilder = true)
    public record CommercialFacts(String entityNo, String entitySourceType,
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) Long localPartnerId,
        String industryCode, String mccCode,
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) long affiliationId,
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) long entityScopeId,
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) long entitySourceRelationId,
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) long clientPricingVersionId,
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) Long partnerShareVersionId,
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) Long platformChargeVersionId,
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) Long platformServicePartyId,
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) long clientAgreementId,
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) Long partnerAgreementId,
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) Long partnerAgreementOperatingEntityId,
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) long sellerDisclosureId,
        Seller seller, FileRef consumerTerms, FileRef refundPolicy, FileRef privacyNotice) {
    }

    /** 冻结的卖方披露。 */
    public record Seller(String legalName, String registrationNumber, String taxRegistrationNumber, String addressText,
        String supportEmail, String supportPhone) {
    }

    /** 不可变源文件的准确身份及摘要。 */
    public record FileRef(
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) long documentId,
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) long fileId,
        String contentHash) {
    }

    /** Finance 权威税结果，不能以零税兜底。 */
    @lombok.Builder(toBuilder = true)
    public record TaxFacts(long snapshotId, String snapshotHash, BigDecimal taxAmount, BigDecimal roundingAmount,
        BigDecimal totalAmount, long orderId, String entityNo, String country, String currency, BigDecimal netAmount,
        List<TaxLine> lines) {
    }

    /** Finance 税明细按原 Client Item ID 精确对齐，不更新不可变商品行。 */
    public record TaxLine(String clientItemId, BigDecimal netAmount, BigDecimal taxAmount, BigDecimal totalAmount) {
    }

    /** 个性化合同文件，不包括临时访问 URL。 */
    public record RenderedContract(long fileId, String contentHash) {
    }

    /** 只读文件访问结果，每次仍指向同一冻结文件。 */
    public record FileLink(String url, Instant expiresAt) {
    }

    /** Payment 创建输入；同一 sourceType + orderId 永久对应唯一 Intent。 */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class IntentRequest {
        /** 来源类型，当前固定为 COMMERCE_ORDER。 */
        private final String sourceType;

        /** Commerce Order 内部身份，同时作为 Payment sourceId。 */
        private final long orderId;

        /** 原 Client。 */
        private final long clientId;

        /** 原冻结经营主体。 */
        private final String entityNo;

        /** 原币种。 */
        private final String currency;

        /** 原最终应付金额。 */
        private final BigDecimal totalAmount;

        /** 原始到期时间，不因首次支付或重试延长。 */
        private final Instant expiresAt;

        /** 原客户价格版本。 */
        private final long clientPricingVersionId;

        /** 原合作方分成版本。 */
        private final Long partnerShareVersionId;

        /** 原平台服务收费版本。 */
        private final Long platformChargeVersionId;

        /** 原行业。 */
        private final String industryCode;

        /** 原 MCC。 */
        private final String mccCode;

        /** 原计税国家。 */
        private final String consumerCountryCode;

        /** 从本次已加载的冻结事实构造；首次支付及创建响应丢失后的支付重提使用同一输入。 */
        public static IntentRequest from(CommerceOrder order, FrozenCheckoutRecord frozen) {
            if (frozen == null || order.getTotalAmount() == null || order.getEntityNo() == null) {
                throw new ErrorException("Intent 创建缺少冻结订单事实");
            }
            return new IntentRequest("COMMERCE_ORDER", order.getOrderId(), order.getClientId(), order.getEntityNo(),
                order.getCurrency(), order.getTotalAmount(), order.getExpiresAt(), frozen.clientPricingVersionId(),
                frozen.partnerShareVersionId(), frozen.platformChargeVersionId(), order.getIndustryCode(),
                order.getMccCode(), order.getConsumerCountryCode());
        }

    }

    /** Payment 返回原 Intent 当前状态，不把创建响应当成支付成功。 */
    public record IntentResult(long intentId, String status) {
        /** 终态 Intent 不再申请工具或发起支付。 */
        public void checkPayable() {
            if ("EXPIRED".equals(status)) {
                throw new FailException(com.paytech.acquiring.api.checkout.v1.enums.CustomerCheckoutErrorCode.CHECKOUT_EXPIRED);
            }
            if (!"OPEN".equals(status)) {
                throw new FailException(com.paytech.acquiring.api.checkout.v1.enums.CustomerCheckoutErrorCode.CHECKOUT_CLOSED);
            }
        }
    }

    /** Payment 只读概要，AVAILABLE 只表示本次查询成功。 */
    public record PaymentStatus(String submissionId, Instant submissionExpiresAt, String attemptId, String status,
        String actionId, String actionType, Instant actionExpiresAt, String failureCode, String failureMessage,
        Instant observedAt) {
        /** 只校验摘要自身；外部查询完成后的时刻由调用方传入。 */
        public void validate(Instant now) {
            if (observedAt == null || observedAt.isAfter(now)) {
                throw new CheckoutUpstreamProtocolException("Payment 概要观察时刻无效");
            }
            if (status != null && !Set.of("PROCESSING", "UNKNOWN", "AWAITING_CARD", "REQUIRES_ACTION", "AUTHORIZED",
                "SUCCEEDED", "FAILED", "CANCELLED", "VOIDED", "EXPIRED").contains(status)) {
                throw new CheckoutUpstreamProtocolException("Payment 概要状态无效");
            }
            if (actionId != null && (!"REQUIRES_ACTION".equals(status) || actionType == null
                || !CheckoutSubmissionModels.PaymentResult.ACTION_TYPES.contains(actionType))) {
                throw new CheckoutUpstreamProtocolException("Payment 概要动作与状态不匹配");
            }
        }
    }

    /** Commerce 入站请求取得的有限网络证据，不能由浏览器 Body 自报。 */
    public record NetworkEvidence(String clientIpAddress, String userAgent, String trustedProxyChain) {
        /** 检查入站证据的存储边界；仅解析字面 IP，不发起 DNS 或外部查询。 */
        public void validate() {
            if (clientIpAddress == null || clientIpAddress.length() > 45 || userAgent == null
                || userAgent.length() > 1024 || userAgent.codePoints().anyMatch(Character::isISOControl)
                || (trustedProxyChain != null && (trustedProxyChain.length() > 2048
                    || trustedProxyChain.codePoints().anyMatch(Character::isISOControl)))) {
                throw new FailException(CommonErrorCode.VALIDATION_ERROR);
            }
            try {
                InetAddress.ofLiteral(clientIpAddress);
            } catch (IllegalArgumentException invalidIp) {
                throw new FailException(CommonErrorCode.VALIDATION_ERROR);
            }
        }
    }
}
