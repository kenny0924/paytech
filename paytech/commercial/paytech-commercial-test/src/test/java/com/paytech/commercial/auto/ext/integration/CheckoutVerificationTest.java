package com.paytech.commercial.auto.ext.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commercial.api.enums.CheckoutErrorCode;
import com.paytech.commercial.domain.CheckoutResolutionContext;
import com.paytech.commercial.integration.ClientMasterCheckoutClient;
import com.paytech.commercial.test.base.checkout.AbstractCheckoutApiTest;
import com.paytech.commercial.test.dal.CommercialTestMapper;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;

/**
 * 两段式解析第 4 步：外部资格检查后的复核（design/06 §2.2、§4）。
 * <li>从公开端口 {@link ClientMasterCheckoutClient} 与公开领域合同 {@link CheckoutResolutionContext#verifyUnchanged} 直接触发复核逻辑，
 * 覆盖“回查版本不一致返回冲突”的各个来源；经 HTTP 的 CCT_CONTEXT_CHANGED 见 ResolveCheckoutRelationshipHttpTest。</li>
 */
class CheckoutVerificationTest extends AbstractCheckoutApiTest {
    @Autowired
    CommercialTestMapper data;

    /** 每例准备商务事实与默认主档候选。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture.install();
        clientMaster.mockCandidates();
    }

    /** client-master 复核报告根或候选变化时端口返回 false，Processor 据此返回 CCT_CONTEXT_CHANGED。 */
    @Test
    void test_clientMasterVersionMismatchIsReported() {
        var context = context(query());
        var selected = context.candidates().iterator().next();
        clientMaster.mockVerified(true);
        assertTrue(clientMasterClient.unchanged(query(), context.master(), selected));
        clientMaster.mockVerified(false, "OPERATING_ENTITY:AE991");
        assertFalse(clientMasterClient.unchanged(query(), context.master(), selected));
        assertEquals(2, clientMaster.count("verify:AE991"));
    }

    /** 本库合同根修订在两次快照之间推进时复核冲突。 */
    @Test
    void test_commercialRootChangeIsConflict() {
        var context = context(query());
        var selected = context.candidates().iterator().next();
        context.verifyUnchanged(context(query()), selected);
        data.advanceAgreementRoot(910000000000106L);
        var failure = assertThrows(FailException.class, () -> context.verifyUnchanged(context(query()), selected));
        assertEquals(CheckoutErrorCode.CCT_CONTEXT_CHANGED.getCode(), failure.getCode());
    }

    /** 选定合同在两次快照之间被终止时复核冲突，不改用其他 OE。 */
    @Test
    void test_terminatedAgreementIsConflict() {
        var context = context(query());
        var selected = context.candidates().iterator().next();
        data.terminateAgreement(910000000000106L);
        var failure = assertThrows(FailException.class, () -> context.verifyUnchanged(context(query()), selected));
        assertEquals(CheckoutErrorCode.CCT_CONTEXT_CHANGED.getCode(), failure.getCode());
    }

    /** client-master 复核不可用时失败关闭为 OWNER_UNAVAILABLE，不降级为未变化。 */
    @Test
    void test_verifyUnavailableFailsClosed() {
        var context = context(query());
        var selected = context.candidates().iterator().next();
        clientMaster.mockVerifyUnavailable();
        var failure = assertThrows(ErrorException.class,
            () -> clientMasterClient.unchanged(query(), context.master(), selected));
        assertEquals(CheckoutErrorCode.OWNER_UNAVAILABLE.getCode(), failure.getCode());
    }
}
