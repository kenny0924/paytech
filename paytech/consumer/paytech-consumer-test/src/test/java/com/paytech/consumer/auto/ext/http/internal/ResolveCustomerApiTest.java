package com.paytech.consumer.auto.ext.http.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.paytech.consumer.api.customer.ConsumerCustomerApi;
import com.paytech.consumer.api.enums.ConsumerErrorCode;
import com.paytech.consumer.api.enums.CustomerStatus;
import com.paytech.consumer.test.base.internal.AbstractConsumerInternalApiTest;

/**
 * acquiring 建单的 Customer 建或取与按主键读取（design/03 §3、S6）：经真实内部 HTTP 入口、Processor 与 mor_consumer；
 * 幂等由 {@code uk(client_id, active_reference)} 保证，并发撞唯一键时重读获胜的一条。
 *
 * @see ConsumerCustomerApi#resolveCustomer
 * @see ConsumerCustomerApi#getCustomer
 */
class ResolveCustomerApiTest extends AbstractConsumerInternalApiTest {
    /** 首次新建 created=true，同一引用再次调用返回同一 Customer 且 created=false；另一 Client 的同一引用是不同 Customer。 */
    @Test
    void test_resolveIsIdempotent() {
        var created = resolve(clientId(), "user-2001");
        var again = resolve(clientId(), "user-2001");
        var other = resolve(otherClientId(), "user-2001");

        assertAll(() -> assertEquals(List.of(200, 200, 200), List.of(created.status(), again.status(), other.status())),
            () -> assertTrue(created.body().get("created").asBoolean()),
            () -> assertFalse(again.body().get("created").asBoolean()),
            () -> assertEquals(created.text("customerId"), again.text("customerId")),
            () -> assertEquals(created.text("customerNo"), again.text("customerNo")),
            () -> assertTrue(created.text("customerNo").startsWith("cus_"), "公开编号前缀"),
            () -> assertEquals(CustomerStatus.ACTIVE.getCode(), created.text("status")),
            () -> assertNotEquals(created.text("customerId"), other.text("customerId")),
            () -> assertEquals(2, customerRows().size()));
    }

    /** 同一引用并发两次建或取只建一个 Customer，两次都返回它。 */
    @Test
    void test_concurrentResolveCreatesOneCustomer() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(() -> await(ready, start, "user-race"), executor);
            var second = CompletableFuture.supplyAsync(() -> await(ready, start, "user-race"), executor);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var one = first.get(10, TimeUnit.SECONDS);
            var two = second.get(10, TimeUnit.SECONDS);

            assertAll(() -> assertEquals(List.of(200, 200), List.of(one.status(), two.status())),
                () -> assertEquals(one.text("customerId"), two.text("customerId")),
                () -> assertEquals(1, customerRows().size()),
                () -> assertEquals(1, (one.body().get("created").asBoolean() ? 1 : 0)
                    + (two.body().get("created").asBoolean() ? 1 : 0), "只有一次是新建"));
        }
    }

    /** 像电话的引用 422 INVALID_CONSUMER_REFERENCE，字符集不符 422，均不写库。 */
    @Test
    void test_personalReferenceRejected() {
        var phone = resolve(clientId(), "13800138000");
        var email = resolve(clientId(), "a@example.com");

        assertAll(() -> assertEquals(List.of(422, 422), List.of(phone.status(), email.status())),
            () -> assertEquals(ConsumerErrorCode.INVALID_CONSUMER_REFERENCE.getCode(), phone.text("code")),
            () -> assertTrue(customerRows().isEmpty()));
    }

    /** 按主键读取有效与已删除的 Customer（含所属 Client 与状态，不含引用原值）；未知主键 404 CUSTOMER_NOT_FOUND。 */
    @Test
    void test_getCustomerById() {
        long deletedId = 770_000_000_001L;
        customerDataMocker.mockCustomer(deletedId, "cus_deletedcustomerbyid0000000001", clientId(), "user-deleted",
            CustomerStatus.DELETED.getCode());
        var created = resolve(clientId(), "user-2002");

        var active = getCustomer(created.text("customerId"));
        var deleted = getCustomer(Long.toString(deletedId));
        var unknown = getCustomer("770000000999");

        assertAll(() -> assertEquals(List.of(200, 200, 404), List.of(active.status(), deleted.status(), unknown.status())),
            () -> assertEquals(created.text("customerNo"), active.text("customerNo")),
            () -> assertEquals(Long.toString(clientId()), active.text("clientId")),
            () -> assertNull(active.body().get("consumerReference"), "不返回消费者引用原值"),
            () -> assertEquals(CustomerStatus.DELETED.getCode(), deleted.text("status")),
            () -> assertEquals("cus_deletedcustomerbyid0000000001", deleted.text("customerNo")),
            () -> assertEquals(ConsumerErrorCode.CUSTOMER_NOT_FOUND.getCode(), unknown.text("code")));
    }

    private Response await(CountDownLatch ready, CountDownLatch start, String reference) {
        ready.countDown();
        try {
            start.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        return resolve(clientId(), reference);
    }
}
