package com.paytech.consumer.auto.ext.http.clientapi.v1.customer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.consumer.api.client.v1.customer.ClientCustomerApi;
import com.paytech.consumer.api.enums.ConsumerErrorCode;
import com.paytech.consumer.api.enums.CustomerStatus;
import com.paytech.consumer.test.base.customer.AbstractCustomerApiTest;
import com.paytech.consumer.test.mocker.external.StoredCardMocker;

/**
 * Customer 接口（需求 20261015 commerce-Customer与存卡支付，AC-01、AC-03、AC-04、AC-12）：经真实 Client API 入口、Processor 与 MySQL；
 * 删除后的已存卡失效经真实补偿任务调用 Payment 测试 HTTP 服务。
 *
 * @see ClientCustomerApi
 */
class CustomerApiTest extends AbstractCustomerApiTest {
    private static final String REFERENCE = "user-1001";

    /** 新引用 201 并带 Location；同一引用再次创建 200 返回原 Customer；另一 Client 的同一引用是不同 Customer。 */
    @Test
    void test_createAndReuse() {
        var created = create(REFERENCE);
        var again = create(REFERENCE);
        var other = create(otherClientId(), REFERENCE);

        String customerNo = created.text("customerNo");
        assertAll(() -> assertEquals(201, created.status()),
            () -> assertEquals("/api/v1/customers/" + customerNo,
                created.result().getResponseHeaders().getLocation().toString()),
            () -> assertTrue(customerNo.startsWith("cus_"), "公开编号前缀"),
            () -> assertEquals(CustomerStatus.ACTIVE.getCode(), created.text("status")),
            () -> assertEquals(REFERENCE, created.text("consumerReference")),
            () -> assertEquals(200, again.status()), () -> assertEquals(customerNo, again.text("customerNo")),
            () -> assertEquals(201, other.status()), () -> assertNotEquals(customerNo, other.text("customerNo")),
            () -> assertEquals(2, customerRows().size()));
    }

    /** 像电话的纯数字 422 INVALID_CONSUMER_REFERENCE；含 @、+ 或空格的字符集校验 422；均不写库。 */
    @ParameterizedTest
    @ValueSource(strings = {"13800138000", "a@example.com", "+971501234567", "has space"})
    void test_personalReferenceRejected(String reference) {
        var response = create(reference);

        assertAll(() -> assertEquals(422, response.status()), () -> assertTrue(customerRows().isEmpty()));
        if (reference.matches("[0-9]+")) {
            assertEquals(ConsumerErrorCode.INVALID_CONSUMER_REFERENCE.getCode(), response.text("code"));
        }
    }

    /** 按编号与按引用读取；跨 Client 与未知编号 404。 */
    @Test
    void test_get() {
        String customerNo = create(REFERENCE).text("customerNo");

        var byNo = get(clientId(), customerNo);
        var byReference = getByReference(clientId(), REFERENCE);
        var crossClient = get(otherClientId(), customerNo);
        var unknownReference = getByReference(clientId(), "user-unknown");

        assertAll(() -> assertEquals(200, byNo.status()), () -> assertEquals(customerNo, byNo.text("customerNo")),
            () -> assertEquals(customerNo, byReference.text("customerNo")),
            () -> assertEquals(List.of(404, 404), List.of(crossClient.status(), unknownReference.status())),
            () -> assertEquals(ConsumerErrorCode.CUSTOMER_NOT_FOUND.getCode(), crossClient.text("code")));
    }

    /** 删除：状态与时间；提交后任务调用 Payment 失效已存卡；重复删除返回原结果、不再调用 Payment；引用再次使用新建 Customer。 */
    @Test
    void test_deleteAndRecreate() {
        String customerNo = create(REFERENCE).text("customerNo");
        String customerId = customerRows().getFirst().get("customerId").toString();

        var deleted = delete(clientId(), customerNo);
        await().atMost(Duration.ofSeconds(10)).until(() -> !storedCards.calls().isEmpty());
        var again = delete(clientId(), customerNo);
        var byReference = getByReference(clientId(), REFERENCE);
        var recreated = create(REFERENCE);

        assertAll(() -> assertEquals(200, deleted.status()),
            () -> assertEquals(CustomerStatus.DELETED.getCode(), deleted.text("status")),
            () -> assertNotNull(deleted.text("deletedAt")),
            () -> assertEquals(List.of(new StoredCardMocker.Call("INVALIDATE", Long.toString(clientId()), customerId,
                null)), storedCards.calls()),
            () -> assertEquals(200, again.status()), () -> assertEquals(deleted.text("deletedAt"), again.text("deletedAt")),
            () -> assertEquals(404, byReference.status()), () -> assertEquals(201, recreated.status()),
            () -> assertNotEquals(customerNo, recreated.text("customerNo")),
            () -> assertEquals(CustomerStatus.DELETED.getCode(), get(clientId(), customerNo).text("status")));
    }
}
