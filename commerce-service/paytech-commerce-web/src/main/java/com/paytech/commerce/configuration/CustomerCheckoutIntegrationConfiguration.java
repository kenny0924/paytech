package com.paytech.commerce.configuration;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.PaymentReference;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.PaymentRequest;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.PaymentResult;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.*;
import com.paytech.commerce.domainservice.port.customer.*;

/** 本轮明确授权的固定外部 Client Mock；仅用于临时流程联通，不代表真实 Owner 集成。 */
@Configuration(proxyBeanMethods = false)
public class CustomerCheckoutIntegrationConfiguration {
    /** TODO(Vault)：接入稳定 Email Protect 与独立 Profile Protect/Reveal。 */
    @Bean
    @ConditionalOnMissingBean(ConsumerProtectionPort.class)
    ConsumerProtectionPort consumerProtectionPort() {
        return new ConsumerProtectionPort() {
            public ProtectedConsumer protect(ConsumerDetails details) {
                return new ProtectedConsumer("mock-email-token", "mock-profile-token");
            }

            public ConsumerDetails reveal(ProtectedConsumer reference) {
                return new ConsumerDetails("Mock Consumer", "consumer@example.invalid",
                    new Address("AE", "Mock address", null, null, "Dubai", null));
            }
        };
    }

    /** TODO(CCT)：接入 exact OE、Affiliation 与 TERM_PRICE 决策接口。 */
    @Bean
    @ConditionalOnMissingBean(CheckoutCommercialPort.class)
    CheckoutCommercialPort checkoutCommercialPort() {
        return request -> CommercialFacts.builder().entityNo("AE001").entitySourceType("PLATFORM")
            .industryCode("RETAIL").mccCode("5999").affiliationId(101L).entityScopeId(102L).entitySourceRelationId(103L)
            .clientPricingVersionId(104L).clientAgreementId(105L).sellerDisclosureId(106L)
            .seller(new Seller("Mock Operating Entity", "MOCK-REG", null, "Mock seller address",
                "support@example.invalid", null))
            .consumerTerms(new FileRef(201L, 301L, "a".repeat(64)))
            .refundPolicy(new FileRef(202L, 302L, "b".repeat(64)))
            .privacyNotice(new FileRef(203L, 303L, "c".repeat(64))).build();
    }

    /** TODO(Risk)：接入批准的准确 OE 资格评估，本轮固定返回评估引用，接入时必须验证真实决定。 */
    @Bean
    @ConditionalOnMissingBean(CheckoutRiskPort.class)
    CheckoutRiskPort checkoutRiskPort() {
        return (request, facts) -> request.getOrderId();
    }

    /** TODO(Finance)：接入按原订单身份冻结的 Tax Snapshot。 */
    @Bean
    @ConditionalOnMissingBean(CheckoutTaxPort.class)
    CheckoutTaxPort checkoutTaxPort() {
        return (request, facts) -> {
            var zero = java.math.BigDecimal.ZERO;
            var lines = request.getItems().stream()
                .map(item -> new TaxLine(item.clientItemId(), item.lineNetAmount(), zero, item.lineNetAmount()))
                .toList();
            return TaxFacts.builder().snapshotId(request.getOrderId()).snapshotHash("d".repeat(64)).taxAmount(zero)
                .roundingAmount(zero).totalAmount(request.getNetAmount()).orderId(request.getOrderId())
                .entityNo(facts.entityNo()).country(request.getCountry()).currency(request.getCurrency())
                .netAmount(request.getNetAmount()).lines(lines).build();
        };
    }

    /** TODO(Platform)：接入不可变合同生成与受控文件链接。 */
    @Bean
    @ConditionalOnMissingBean(CheckoutDocumentPort.class)
    CheckoutDocumentPort checkoutDocumentPort() {
        return new CheckoutDocumentPort() {
            public RenderedContract render(InitializationRequest r, CommercialFacts f, TaxFacts t) {
                return new RenderedContract(r.getOrderId(), "e".repeat(64));
            }

            public FileLink link(long fileId, String hash, Instant expiresAt) {
                return new FileLink("https://documents.example.invalid/mock/" + fileId, expiresAt);
            }
        };
    }

    /** TODO(Payment)：接入无副作用的 Customer 概要查询；Intent 创建已使用正式客户端。 */
    @Bean
    @ConditionalOnMissingBean(CheckoutPaymentPort.class)
    CheckoutPaymentPort checkoutPaymentPort(Clock clock) {
        return new CheckoutPaymentPort() {
            public PaymentStatus query(long orderId, long intentId) {
                return new PaymentStatus(null, null, null, "PROCESSING", null, null, null, null, null, clock.instant());
            }
        };
    }

    /** TODO(Payment Card)：接入统一 pay 与按来源提交查询；工具申请已使用正式客户端；本轮固定返回处理中，不模拟扣款成功。 */
    @Bean
    @ConditionalOnMissingBean(CheckoutCardPaymentPort.class)
    CheckoutCardPaymentPort checkoutCardPaymentPort(Clock clock) {
        return new CheckoutCardPaymentPort() {
            /** TODO(Payment)：Intent 锁、来源提交去重、过期/取消栅栏及 Card/渠道执行。 */
            public PaymentResult submit(PaymentRequest request) {
                var ref = request.receipt().reference();
                return new PaymentResult(ref.submissionId(), ref.orderId(), ref.intentId(),
                    Long.toString(ref.submissionId()), "PROCESSING", "4242", null, null, clock.instant());
            }

            /** TODO(Payment)：只查同订单与 Intent 的原提交；未找到不视作无资金动作。 */
            public PaymentResult query(PaymentReference reference) {
                return new PaymentResult(reference.submissionId(), reference.orderId(), reference.intentId(),
                    Long.toString(reference.submissionId()), "PROCESSING", "4242", null, null, clock.instant());
            }
        };
    }
}
