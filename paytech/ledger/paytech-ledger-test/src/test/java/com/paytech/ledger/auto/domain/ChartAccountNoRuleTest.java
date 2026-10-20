package com.paytech.ledger.auto.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.paytech.foundation.common.error.FailException;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.domainservice.validator.chart.ChartAccountNoRule;

/**
 * 科目编号规则：4-2-2-2 位数、类别首位、上级前缀与长度、级次推导。
 *
 * @see ChartAccountNoRule#check(String, AccountClass, String)
 * @see ChartAccountNoRule#level(String)
 */
class ChartAccountNoRuleTest {

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"1001, ASSET, NULL, 1", "220101, LIABILITY, 2201, 2",
        "31030101, EQUITY, 310301, 3", "4001010101, REVENUE, 40010101, 4", "5101, EXPENSE, NULL, 1"})
    void test_validNumbers(String accountNo, AccountClass accountClass, String parentNo, int level) {
        assertDoesNotThrow(() -> ChartAccountNoRule.check(accountNo, accountClass, parentNo));
        assertEquals(level, ChartAccountNoRule.level(accountNo));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"2001, ASSET, NULL", "6001, EXPENSE, NULL", "100101, ASSET, NULL",
        "10010101, ASSET, 1001", "110101, ASSET, 1001", "1001, ASSET, 1001", "0101, ASSET, NULL",
        "10010, ASSET, 1001", "100101010101, ASSET, 1001010101", "10a1, ASSET, NULL"})
    void test_invalidNumbers(String accountNo, AccountClass accountClass, String parentNo) {
        FailException error = assertThrows(FailException.class,
            () -> ChartAccountNoRule.check(accountNo, accountClass, parentNo));
        assertEquals(LedgerSetupErrorCode.CHART_ACCOUNT_NO_INVALID.getCode(), error.getErrorCode().getCode());
    }
}
