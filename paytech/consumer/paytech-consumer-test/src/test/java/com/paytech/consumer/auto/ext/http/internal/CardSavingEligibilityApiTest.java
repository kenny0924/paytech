package com.paytech.consumer.auto.ext.http.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.consumer.api.customer.ConsumerCustomerApi;
import com.paytech.consumer.api.enums.CardSavingIneligibleReason;
import com.paytech.consumer.api.enums.CustomerStatus;
import com.paytech.consumer.test.base.internal.AbstractConsumerInternalApiTest;
import com.paytech.consumer.test.mocker.external.ClientMasterStoredCardSettingMocker;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * 存卡资格（D42、S6）：经真实内部 HTTP 入口组合 mor_consumer 的 Customer 与 client-master 已存卡开关（第 04 步合同，DTO Mock）；
 * client-master 不可用或响应无效时失败关闭为 SETTING_UNAVAILABLE。
 *
 * @see ConsumerCustomerApi#cardSavingEligibility
 */
class CardSavingEligibilityApiTest extends AbstractConsumerInternalApiTest {
    private static final long CUSTOMER_ID = 770_000_000_101L;
    private static final String CUSTOMER_NO = "cus_cardsavingeligibility000000001";

    /** 有效 Customer 且 Client 已开通：可存卡并返回公开编号。 */
    @Test
    void test_eligible() {
        customerDataMocker.mockCustomer(CUSTOMER_ID, CUSTOMER_NO, clientId(), "user-eligible",
            CustomerStatus.ACTIVE.getCode());
        storedCardSettings.mockEnabled(clientId(), true);

        var response = eligibility(clientId(), CUSTOMER_ID);

        assertAll(() -> assertEquals(200, response.status()), () -> assertTrue(response.body().get("eligible").asBoolean()),
            () -> assertNull(response.text("reason")), () -> assertEquals(CUSTOMER_NO, response.text("customerNo")),
            () -> assertEquals(List.of(Long.toString(clientId())), storedCardSettings.calls()));
    }

    /** Client 未开通：SETTING_DISABLED。 */
    @Test
    void test_settingDisabled() {
        customerDataMocker.mockCustomer(CUSTOMER_ID, CUSTOMER_NO, clientId(), "user-disabled",
            CustomerStatus.ACTIVE.getCode());
        storedCardSettings.mockEnabled(clientId(), false);

        assertIneligible(eligibility(clientId(), CUSTOMER_ID), CardSavingIneligibleReason.SETTING_DISABLED, CUSTOMER_NO);
    }

    /** Customer 不存在、已删除或属于另一 Client：不可存卡，且不读取 Client 开关；跨 Client 不返回对方编号。 */
    @Test
    void test_customerNotUsable() {
        long deletedId = CUSTOMER_ID + 1;
        long foreignId = CUSTOMER_ID + 2;
        customerDataMocker.mockCustomer(deletedId, "cus_cardsavingeligibility000000002", clientId(), "user-deleted",
            CustomerStatus.DELETED.getCode());
        customerDataMocker.mockCustomer(foreignId, "cus_cardsavingeligibility000000003", otherClientId(), "user-foreign",
            CustomerStatus.ACTIVE.getCode());

        assertIneligible(eligibility(clientId(), CUSTOMER_ID), CardSavingIneligibleReason.CUSTOMER_NOT_FOUND, null);
        assertIneligible(eligibility(clientId(), deletedId), CardSavingIneligibleReason.CUSTOMER_INACTIVE,
            "cus_cardsavingeligibility000000002");
        assertIneligible(eligibility(clientId(), foreignId), CardSavingIneligibleReason.CLIENT_MISMATCH, null);
        assertTrue(storedCardSettings.calls().isEmpty(), "Customer 不可用时不读取开关");
    }

    /** client-master 不可用、连接中断或响应无效（未回显 Client）：失败关闭为 SETTING_UNAVAILABLE，接口本身仍 200。 */
    @ParameterizedTest
    @ValueSource(strings = {"unavailable", "disconnect", "malformed"})
    void test_clientMasterFailureFailsClosed(String failure) {
        customerDataMocker.mockCustomer(CUSTOMER_ID, CUSTOMER_NO, clientId(), "user-unavailable",
            CustomerStatus.ACTIVE.getCode());
        storedCardSettings.mockFailure(clientId(), switch (failure) {
            case "unavailable" -> ClientMasterStoredCardSettingMocker.unavailable();
            case "disconnect" -> MockHttpFault.disconnect();
            default -> MockHttpFault.json(200, Map.of("clientId", "1", "enabled", true));
        });

        assertIneligible(eligibility(clientId(), CUSTOMER_ID), CardSavingIneligibleReason.SETTING_UNAVAILABLE,
            CUSTOMER_NO);
        assertEquals(1, storedCardSettings.calls().size());
    }

    private static void assertIneligible(Response response, CardSavingIneligibleReason reason, String customerNo) {
        assertAll(() -> assertEquals(200, response.status()),
            () -> assertFalse(response.body().get("eligible").asBoolean()),
            () -> assertEquals(reason.getCode(), response.text("reason")),
            () -> assertEquals(customerNo, response.text("customerNo")));
    }
}
