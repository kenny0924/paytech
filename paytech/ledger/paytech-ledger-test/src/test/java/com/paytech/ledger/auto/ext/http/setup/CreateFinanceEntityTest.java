package com.paytech.ledger.auto.ext.http.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.ledger.api.FinanceEntityCommandApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.api.request.CreateFinanceEntityRequestBody;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.dal.mapper.FinanceEntityMapper;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.test.base.MockTestBase;
import com.paytech.ledger.test.builder.AccountingSetupRequestBuilder;
import com.paytech.ledger.test.checker.LedgerResponseChecker;

/**
 * 创建财务主体：成功、唯一性、操作人 Header 必填与参数校验。
 *
 * @see FinanceEntityCommandApi#createFinanceEntity(String, String, CreateFinanceEntityRequestBody)
 */
class CreateFinanceEntityTest extends MockTestBase {
    private static final String PATH = "/finance-entities/v1";

    @Autowired
    private FinanceEntityMapper financeEntityMapper;

    @Test
    void test_normalFlow() {
        var response = postAsOperator(PATH, AccountingSetupRequestBuilder.financeEntity("FE-PLATFORM", "880001")).assertSuccess(201);

        FinanceEntity entity = financeEntityMapper.selectById(response.id("financeEntityId"));
        assertEquals("FE-PLATFORM", entity.getEntityCode());
        assertEquals(880001L, entity.getLegalPartyId());
        assertEquals(FinanceEntityStatus.ACTIVE, entity.getStatus());
    }

    @Test
    void test_duplicateCodeOrPartyRejected() {
        ledgerMocker.mockFinanceEntity("FE-PLATFORM", 880001L, FinanceEntityStatus.ACTIVE);

        postAsOperator(PATH, AccountingSetupRequestBuilder.financeEntity("FE-PLATFORM", "880002"))
            .assertRejected(LedgerSetupErrorCode.FINANCE_ENTITY_ALREADY_EXISTS);
        postAsOperator(PATH, AccountingSetupRequestBuilder.financeEntity("FE-OTHER", "880001"))
            .assertRejected(LedgerSetupErrorCode.FINANCE_ENTITY_ALREADY_EXISTS);
    }

    @Test
    void test_missingOperatorHeaderRejected() {
        var body = AccountingSetupRequestBuilder.financeEntity("FE-PLATFORM", "880001");
        HttpHeaders actorOnly = TestRequestHeaders.internal(REQUEST_ID);
        actorOnly.set(ACTOR_HEADER, OPERATOR_ACCOUNT_ID);

        post(PATH, body).assertInvalidRequest();
        new LedgerResponseChecker(http.post(PATH, actorOnly, body), objectMapper, REQUEST_ID).assertInvalidRequest();

        get("/finance-entities/v1").assertSuccess(200).assertPage(1, 10, 0);
    }

    @Test
    void test_invalidParameter() {
        var body = AccountingSetupRequestBuilder.financeEntity("FE-PLATFORM", "not-a-number");

        postAsOperator(PATH, body).assertProblem(422, "VALIDATION_ERROR").assertFieldError("legalPartyId");
    }
}
