package com.paytech.client.master.auto.ext.http.checkout;

import org.junit.jupiter.api.Test;

import com.paytech.client.master.api.storedcard.ClientStoredCardSettingApi;
import com.paytech.client.master.test.base.checkout.AbstractCheckoutCandidateApiTest;
import com.paytech.client.master.test.mocker.checkout.CheckoutCandidateFixture;
import com.paytech.foundation.common.error.CommonErrorCode;

/**
 * Client 级已存卡开关（D42，自 Commerce 迁入）：独立查询与候选响应复用同一读取。
 * @see ClientStoredCardSettingApi#storedCardSetting(String)
 */
class ClientStoredCardSettingHttpTest extends AbstractCheckoutCandidateApiTest {
    private static final String PATH = "/client-master/v1/clients/" + CheckoutCandidateFixture.CLIENT_ID + "/stored-card-setting";

    /** 没有记录视为未开通。 */
    @Test
    void test_missingSettingIsDisabled() {
        get(PATH, headers()).assertOk().assertText("/clientId", Long.toString(CheckoutCandidateFixture.CLIENT_ID))
            .assertBoolean("/enabled", false);
    }

    /** 开通后独立查询与候选响应都返回开通。 */
    @Test
    void test_enabledSettingIsReturned() {
        data.storedCardSetting(CheckoutCandidateFixture.CLIENT_ID, true);
        get(PATH, headers()).assertOk().assertBoolean("/enabled", true);
        post(CANDIDATES, request(), headers()).assertOk().assertBoolean("/client/storedCardSetting/enabled", true);
    }

    /** 非规范 Client ID 在读取前拒绝。 */
    @Test
    void test_invalidClientId() {
        get("/client-master/v1/clients/01/stored-card-setting", headers())
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
    }
}
