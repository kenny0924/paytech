package com.paytech.ledger.auto.ext.http.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.ledger.api.LedgerSetupQueryApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.test.base.MockTestBase;
import com.paytech.ledger.test.checker.LedgerResponseChecker;

/**
 * 按法律 Party 解析财务主体：ACTIVE 命中、CLOSED 与未知 Party 为 404、路径参数校验。
 *
 * @see LedgerSetupQueryApi#getFinanceEntityByParty(String)
 */
class GetFinanceEntityByPartyTest extends MockTestBase {
    private static final String PATH = "/finance-entities/v1/by-party/";

    private static final long ACTIVE_PARTY = 880101L;

    private static final long CLOSED_PARTY = 880102L;

    private long activeEntity;

    @BeforeEach
    void setUpClassMocks() {
        activeEntity = ledgerMocker.mockFinanceEntity("FE-PLATFORM-HQ", ACTIVE_PARTY, FinanceEntityStatus.ACTIVE);
        ledgerMocker.mockFinanceEntity("FE-CLOSED", CLOSED_PARTY, FinanceEntityStatus.CLOSED);
    }

    @Test
    void test_normalFlow() {
        get(PATH + ACTIVE_PARTY).assertSuccess(200)
            .assertText("financeEntityId", String.valueOf(activeEntity))
            .assertText("entityCode", "FE-PLATFORM-HQ")
            .assertText("legalPartyId", String.valueOf(ACTIVE_PARTY))
            .assertText("displayName", "Test FE-PLATFORM-HQ")
            .assertText("status", FinanceEntityStatus.ACTIVE.getCode());
    }

    @Test
    void test_closedEntity() {
        assertNotFound(get(PATH + CLOSED_PARTY));
    }

    @Test
    void test_noDataFound() {
        assertNotFound(get(PATH + (ACTIVE_PARTY + 1000)));
    }

    @Test
    void test_invalidParameter() {
        get(PATH + "0").assertInvalidRequest();
        get(PATH + "abc").assertInvalidRequest();
    }

    /** 404 使用专用错误码与默认消息。 */
    private void assertNotFound(LedgerResponseChecker response) {
        response.assertProblem(404, LedgerSetupErrorCode.FINANCE_ENTITY_NOT_FOUND.getCode())
            .assertDetailContains(LedgerSetupErrorCode.FINANCE_ENTITY_NOT_FOUND.getMessage());
    }
}
