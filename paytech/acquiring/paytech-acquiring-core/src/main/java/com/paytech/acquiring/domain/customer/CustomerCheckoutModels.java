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
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.RelationshipRole;
import com.paytech.commercial.api.enums.RelationshipSubjectType;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;

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

        /** 订单冻结的法律内容 Locale；精确匹配法律文件与卖方披露，当前恒为 en。 */
        private final String locale;

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
        Seller seller, FileRef consumerTerms, FileRef refundPolicy, FileRef privacyNotice,
        RelationshipFacts relationship, List<CapabilityFacts> capabilities, String businessMode) {
    }

    /**
     * commercial 推导的商业关系快照，订单创建时原样冻结
     * <li>固定 Mock 不提供快照（为 null）：不写角色绑定，fund_flow 与 snapshot_model_version 为空，生效版本记 0。</li>
     *
     * @param effectiveModelVersion 生效模型版本；0 只记录，1 下游按快照生效
     * @param snapshotModelVersion 推导规则版本
     * @param fundFlow 资金流向 Code
     * @param bindings 角色绑定，按 commercial 返回顺序
     */
    public record RelationshipFacts(int effectiveModelVersion, int snapshotModelVersion, String fundFlow,
        List<RoleBindingFacts> bindings) {
    }

    /**
     * 一个角色到主体的绑定
     *
     * @param role 角色 Code
     * @param subjectType 主体类型 Code
     * @param subjectId 主体标识；PLATFORM 为空
     * @param partyId 主体法律 Party；可空
     */
    public record RoleBindingFacts(String role, String subjectType, String subjectId,
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) Long partyId) {
    }

    /**
     * Client 合同命中的开通项；本期不落库，二期执行接受条件与 3DS 时使用
     *
     * @param agreementCapabilityId 开通项 ID
     * @param capabilityCode 可开通项代码
     * @param capabilityKind 可开通项种类 Code
     * @param paymentMethodCode 绑定的支付方式代码；非支付方式类为空
     * @param require3ds 是否要求 3DS
     * @param conditions 接受条件：维度 Code → 允许取值；同维度 OR，异维度 AND
     */
    public record CapabilityFacts(
        @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) long agreementCapabilityId,
        String capabilityCode, String capabilityKind, String paymentMethodCode, boolean require3ds,
        java.util.Map<String, List<String>> conditions) {
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

    /**
     * tax 计算请求：订单身份、计算序号与阶段、国家证据、商业关系主体与不可变商品行
     * <li>建单为 revision 1、PROVISIONAL、Client 声明国家；消费者资料提交为最新 revision + 1、FINAL、账单国家。</li>
     * <li>同一 orderId + revision 的内容必须稳定：tax 对同身份同内容返回原计算，不同内容 409。</li>
     */
    @lombok.Getter
    @lombok.Builder
    public static class TaxQuoteRequest {
        /** 订单主键，作为 tax sourceObjectId。 */
        private final long orderId;

        /** 公开订单号，作为 tax sourceObjectNo。 */
        private final String orderNo;

        /** 订单内计算序号，作为 tax sourceRevision。 */
        private final int revision;

        /** 计算阶段：PROVISIONAL 用 Client 声明国家，FINAL 用消费者账单国家。 */
        private final TaxCalculationStage stage;

        /** 本阶段的国家证据（ISO 3166-1 alpha-2）。 */
        private final String countryCode;

        /** 买方 Client。 */
        private final long clientId;

        /** 冻结的收单 OE。 */
        private final String entityNo;

        /** 由商业关系快照取得的计税主体。 */
        private final TaxParties parties;

        /** 订单币种。 */
        private final String currency;

        /** 税点日期（UTC 日历日）。 */
        private final java.time.LocalDate taxPointDate;

        /** 订单不可变商品行，按原呈现顺序。 */
        private final List<OrderItem> items;
    }

    /**
     * 商业关系快照中计税所需的主体
     * <li>有快照时：businessMode 按快照反推（SELLER 为 OPERATING_ENTITY 即 MOR，否则 PSP，与 commercial RelationshipSnapshot.derive
     * 一致），TAX_RESPONSIBLE 必须有法律 Party。</li>
     * <li>没有快照（CLIENT 来源 OE，责任方在 Client 一侧）：taxSubjectPartyId 为空，tax 恒返回 NOT_MANAGED（TAX_RESPONSIBLE_UNRESOLVED）。</li>
     *
     * @param businessMode commercial BusinessMode Code
     * @param sellerPartyId SELLER 角色的法律 Party，可空
     * @param taxSubjectPartyId TAX_RESPONSIBLE 角色的法律 Party；没有快照时为空
     */
    public record TaxParties(String businessMode, Long sellerPartyId, Long taxSubjectPartyId) {
        /**
         * 从商业关系快照取得计税主体
         * <li>快照存在但缺少 SELLER 或 TAX_RESPONSIBLE 法律 Party：上游协议缺陷，失败关闭，不以零税或 NOT_MANAGED 兜底。</li>
         *
         * @param bindings 订单冻结的角色绑定，非空
         * @return 计税主体
         */
        public static TaxParties from(List<RoleBindingFacts> bindings) {
            RoleBindingFacts seller = role(bindings, RelationshipRole.SELLER);
            RoleBindingFacts responsible = role(bindings, RelationshipRole.TAX_RESPONSIBLE);
            if (seller == null || responsible == null || responsible.partyId() == null) {
                throw new CheckoutUpstreamProtocolException("商业关系缺少 SELLER 或 TAX_RESPONSIBLE 法律 Party");
            }
            boolean merchantOfRecord = RelationshipSubjectType.OPERATING_ENTITY.getCode().equals(seller.subjectType());
            return new TaxParties(merchantOfRecord ? BusinessMode.MOR.getCode() : BusinessMode.PSP.getCode(),
                seller.partyId(), responsible.partyId());
        }

        /**
         * 没有商业关系快照时的计税主体：只有合同业务模式，卖方与责任方为空
         *
         * @param businessMode commercial 返回的 Client 合同业务模式
         * @return 计税主体
         */
        public static TaxParties unresolved(String businessMode) {
            if (businessMode == null || businessMode.isBlank()) {
                throw new CheckoutUpstreamProtocolException("商业事实缺少合同业务模式");
            }
            return new TaxParties(businessMode, null, null);
        }

        /** 取指定角色的唯一绑定；不存在返回 null。 */
        private static RoleBindingFacts role(List<RoleBindingFacts> bindings, RelationshipRole role) {
            return bindings.stream().filter(binding -> role.getCode().equals(binding.role())).findFirst().orElse(null);
        }
    }

    /**
     * tax 返回的一次不可变税务计算
     * <li>BLOCKED 时税额、舍入额、总额与逐行税额为空，blockReasonCode 为第一条原因；NOT_MANAGED 时税额为自报值、组件为空。</li>
     * <li>expiresAt 只有 FINAL + READY 非空。</li>
     *
     * @param calculationId tax 计算 ID
     * @param stage 计算阶段
     * @param status 计算状态
     * @param taxAmount 税额汇总
     * @param roundingAmount 头部舍入差额
     * @param totalAmount 应付总额 = 订单净额 + 税额 + 舍入差额
     * @param blockReasonCode BLOCKED 的第一条原因代码
     * @param expiresAt 报价有效截止时刻
     * @param lines 逐行税额，与订单商品行一一对应
     * @param components 按辖区与注册汇总的税组件
     */
    @lombok.Builder(toBuilder = true)
    public record TaxCalculation(long calculationId, TaxCalculationStage stage, TaxCalculationStatus status,
        BigDecimal taxAmount, BigDecimal roundingAmount, BigDecimal totalAmount, String blockReasonCode,
        Instant expiresAt, List<TaxLine> lines, List<TaxComponent> components) {
        /** 是否被阻断：税额未知、订单不可支付。 */
        public boolean blocked() {
            return status == TaxCalculationStatus.BLOCKED;
        }
    }

    /** 一行税额，按原 Client Item ID 与商品行对齐，不更新不可变商品行。 */
    public record TaxLine(String clientItemId, BigDecimal netAmount, BigDecimal taxAmount, BigDecimal totalAmount) {
    }

    /** 按辖区、税种、注册与纳税主体汇总的税组件，事件按此逐组件携带；ID 为十进制字符串。 */
    public record TaxComponent(String jurisdictionCode, String taxType, String registrationId,
        String taxSubjectPartyId, BigDecimal taxAmount) {
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
        /** Payment 来源业务类型：Commerce 订单。 */
        public static final String COMMERCE_ORDER_SOURCE_TYPE = "COMMERCE_ORDER";

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
            return new IntentRequest(COMMERCE_ORDER_SOURCE_TYPE, order.getOrderId(), order.getClientId(), order.getEntityNo(),
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
