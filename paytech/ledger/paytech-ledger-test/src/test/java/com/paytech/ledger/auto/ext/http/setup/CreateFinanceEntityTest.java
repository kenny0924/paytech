package com.paytech.ledger.auto.ext.http.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.ledger.api.FinanceEntityCommandApi;
import com.paytech.ledger.api.request.CreateFinanceEntityRequestBody;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.dal.mapper.FinanceEntityMapper;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.test.base.MockTestBase;
import com.paytech.ledger.test.builder.AccountingSetupRequestBuilder;

/**
 * 创建财务主体：成功、唯一性与参数校验。
 *
 * @see FinanceEntityCommandApi#createFinanceEntity(CreateFinanceEntityRequestBody)
 */
class CreateFinanceEntityTest extends MockTestBase {
    private static final String PATH = "/finance-entities/v1";

    @Autowired
    private FinanceEntityMapper financeEntityMapper;

    @Test
    void test_normalFlow() {
        var response = post(PATH, AccountingSetupRequestBuilder.financeEntity("FE-PLATFORM", "880001")).assertSuccess(201);

        FinanceEntity entity = financeEntityMapper.selectById(response.id("financeEntityId"));
        assertEquals("FE-PLATFORM", entity.getEntityCode());
        assertEquals(880001L, entity.getLegalPartyId());
        assertEquals(FinanceEntityStatus.ACTIVE, entity.getStatus());
    }

    @Test
    void test_duplicateCodeOrPartyRejected() {
        ledgerMocker.mockFinanceEntity("FE-PLATFORM", 880001L, FinanceEntityStatus.ACTIVE);

        post(PATH, AccountingSetupRequestBuilder.financeEntity("FE-PLATFORM", "880002"))
            .assertBusinessRejected("财务主体代码或法律 Party 已存在");
        post(PATH, AccountingSetupRequestBuilder.financeEntity("FE-OTHER", "880001"))
            .assertBusinessRejected("财务主体代码或法律 Party 已存在");
    }

    @Test
    void test_invalidParameter() {
        var body = AccountingSetupRequestBuilder.financeEntity("FE-PLATFORM", "not-a-number");

        post(PATH, body).assertProblem(422, "VALIDATION_ERROR").assertFieldError("legalPartyId");
    }
}
