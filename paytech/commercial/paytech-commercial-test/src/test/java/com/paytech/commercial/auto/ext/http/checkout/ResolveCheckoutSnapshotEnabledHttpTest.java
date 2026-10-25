package com.paytech.commercial.auto.ext.http.checkout;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.paytech.commercial.api.checkout.CheckoutContextApi;
import com.paytech.commercial.api.checkout.request.ResolveCheckoutContextRequestBody;
import com.paytech.commercial.test.base.checkout.AbstractCheckoutApiTest;

/**
 * 快照开关打开（commercial.relationship-snapshot.enabled=true）时响应 effectiveModelVersion=1，推导规则版本与快照内容不变。
 *
 * @see CheckoutContextApi#resolve(String, ResolveCheckoutContextRequestBody)
 */
@TestPropertySource(properties = "commercial.relationship-snapshot.enabled=true")
class ResolveCheckoutSnapshotEnabledHttpTest extends AbstractCheckoutApiTest {
    /** 每例准备商务事实、默认主档候选、复核通过与全部主体合格。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture.install();
        clientMaster.mockCandidates();
        clientMaster.mockVerified(true);
        compliance.mockAllEligible();
    }

    /** 开关打开：生效模型版本 1，快照仍为 8 个 MOR 角色。 */
    @Test
    void test_enabledSwitchReturnsModelVersionOne() {
        var snapshot = invoke(request(), headers()).success().getRelationshipSnapshot();

        assertAll(() -> assertEquals(1, snapshot.getEffectiveModelVersion()),
            () -> assertEquals(1, snapshot.getSnapshotModelVersion()),
            () -> assertEquals(8, snapshot.getBindings().size()));
    }
}
