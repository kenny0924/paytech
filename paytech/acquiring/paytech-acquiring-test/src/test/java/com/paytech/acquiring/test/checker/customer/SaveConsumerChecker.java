package com.paytech.acquiring.test.checker.customer;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.springframework.boot.test.context.TestComponent;
import com.paytech.acquiring.core.dal.dataobject.customer.ConsumerContext;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ConsumerDetails;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ProtectedConsumer;
import com.paytech.acquiring.test.checker.order.OrderCancellationChecker;
import com.paytech.acquiring.test.dal.OrderCancellationTestMapper;
import com.paytech.acquiring.test.dal.OrderQueryTestMapper;
import com.paytech.acquiring.test.dal.SaveConsumerTestMapper;
import lombok.AllArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 独立查询消费者及关联事实；只有消费者和订单版本允许在成功保存时变化。 */
@TestComponent
@AllArgsConstructor
public class SaveConsumerChecker {
    private final SaveConsumerTestMapper mapper;
    private final OrderCancellationTestMapper orders;
    private final OrderQueryTestMapper queries;
    private final OrderCancellationChecker related;
    private final ObjectMapper json;

    /** 检查原始消费者身份和版本，不把无行当成成功。 */
    public ConsumerContext consumer(long orderId) {
        var row = mapper.consumer(orderId);
        assertNotNull(row, "消费者记录必须存在");
        return row;
    }

    /** 失败前后比较所有本例事实，包含原始未映射字段。 */
    public JsonNode snapshot(long orderId) {
        return json.valueToTree(List.of(queries.orderRows(orderId), mapper.consumerRows(orderId), unchangedFacts(orderId)));
    }

    /** 所有响应均不能改变会话、支付、退款、税务、法律接受和补偿事实。 */
    public JsonNode unchangedFacts(long orderId) {
        return json.valueToTree(List.of(related.unchangedFacts(orderId), queries.sessionRows(orderId),
            queries.paymentRows(orderId), mapper.legalRows(orderId), mapper.contractRows(orderId)));
    }

    /** 首次保存的主键由业务生成，其余字段从独立准备的 DTO 与时刻推导。 */
    public void assertSaved(long orderId, ConsumerDetails details, ProtectedConsumer values,
        long consumerVersion, Instant createdAt, Instant savedAt) {
        var actual = consumer(orderId);
        assertTrue(actual.getConsumerContextId() > 0);
        var expected = new ConsumerContext();
        expected.setConsumerContextId(actual.getConsumerContextId());
        expected.setOrderId(orderId);
        expected.setEmailVaultToken(values.emailToken());
        expected.setConsumerProfileVaultToken(values.profileToken());
        expected.setEmailDomain(details.email().substring(details.email().lastIndexOf('@') + 1));
        expected.setBillingCountryCode(details.billingAddress().countryCode());
        expected.setBillingRegionCode(details.billingAddress().region());
        expected.setBillingCity(details.billingAddress().city());
        expected.setVersion(consumerVersion);
        expected.setCreateTime(createdAt);
        expected.setUpdateTime(savedAt);
        assertEquals(json.valueToTree(expected), json.valueToTree(actual));
        assertEquals(1, mapper.consumerRows(orderId).size());
        var row = mapper.consumerRows(orderId).getFirst();
        assertAll(() -> assertNull(row.get("consumer_reference")), () -> assertNull(row.get("phone_vault_token")),
            () -> assertNull(row.get("phone_country_code")), () -> assertNull(row.get("billing_postal_prefix")));
    }

    /** 原订单的业务字段全部保持，仅递增一次版本并推进更新时间。 */
    public void assertOrderChangedOnlyVersion(CommerceOrder before,
        Instant savedAt) {
        var expected = json.convertValue(before, CommerceOrder.class);
        expected.setVersion(before.getVersion() + 1);
        expected.setUpdateTime(savedAt);
        assertEquals(json.valueToTree(expected), json.valueToTree(orders.order(before.getOrderId())));
    }
}
