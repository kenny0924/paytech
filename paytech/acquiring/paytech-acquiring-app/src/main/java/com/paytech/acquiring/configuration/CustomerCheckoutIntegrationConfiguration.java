package com.paytech.acquiring.configuration;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.acquiring.domain.customer.CheckoutSubmissionModels.PaymentReference;
import com.paytech.acquiring.domain.customer.CheckoutSubmissionModels.PaymentRequest;
import com.paytech.acquiring.domain.customer.CheckoutSubmissionModels.PaymentResult;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.*;
import com.paytech.acquiring.domainservice.port.customer.*;

/** 本轮明确授权的固定外部 Client Mock；仅用于临时流程联通，不代表真实 Owner 集成。商业解析的 Mock 见 CommercialMockConfiguration。 */
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
}
