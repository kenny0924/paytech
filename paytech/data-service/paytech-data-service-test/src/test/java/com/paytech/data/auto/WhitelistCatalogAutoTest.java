package com.paytech.data.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.data.core.configuration.CdcWhitelistProperties;
import com.paytech.data.domain.whitelist.WhitelistedTable;
import com.paytech.data.domainservice.WhitelistCatalog;
import com.paytech.data.domainservice.impl.WhitelistCatalogImpl;
import com.paytech.foundation.common.error.ErrorException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

/**
 * 白名单契约校验：合法文件按顺序读出，非法样例在构造时被拒并给出定位信息。
 *
 * @see WhitelistCatalogImpl#WhitelistCatalogImpl(CdcWhitelistProperties, org.springframework.core.io.ResourceLoader)
 * @see WhitelistCatalog#tables()
 * @author Claude
 */
class WhitelistCatalogAutoTest {

    private static WhitelistCatalog load(String location) {
        return new WhitelistCatalogImpl(new CdcWhitelistProperties(location), new DefaultResourceLoader());
    }

    @Test
    void test_normalFlow() {
        WhitelistCatalog catalog = load("classpath:whitelists/valid-two-services.yaml");

        List<String> names = catalog.tables().stream().map(WhitelistedTable::qualifiedName).toList();
        assertEquals(List.of("mor_finance.t_currency_spec", "mor_finance.t_journal_entry",
            "mor_platform.t_currency_spec"), names);
        WhitelistedTable journal = catalog.find("mor_finance", "t_journal_entry").orElseThrow();
        assertEquals("ledger", journal.service());
        assertEquals(List.of("memo"), journal.maskedColumns());
        assertEquals(List.of("internal_note"), journal.excludedColumns());
        WhitelistedTable platformCurrency = catalog.find("mor_platform", "t_currency_spec").orElseThrow();
        assertEquals("platform-support", platformCurrency.service());
        assertTrue(catalog.find("mor_platform", "t_journal_entry").isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "duplicate-table.yaml         | 表重复登记: mor_finance.t_currency_spec",
        "blank-column.yaml            | services[0].tables[0].maskedColumns 列名不能为空",
        "database-without-prefix.yaml | services[0].database 取值 finance 不符合",
        "table-without-prefix.yaml    | services[0].tables[0].name 取值 currency_spec 不符合",
        "unknown-field.yaml           | 未知字段 includedColumns",
        "empty-services.yaml          | services 必须是非空列表",
        "masked-and-excluded.yaml     | 列 code 不能同时屏蔽与排除",
        "database-two-owners.yaml     | 库 mor_finance 已属于服务 ledger，不能再登记给 billing",
        "duplicate-column.yaml        | 列名重复: code",
        "malformed.yaml               | 不是合法 YAML",
        "missing.yaml                 | CDC 白名单文件不存在"})
    void test_invalidParameter(String file, String expectedMessage) {
        ErrorException exception = assertThrows(ErrorException.class, () -> load("classpath:whitelists/" + file));

        assertTrue(exception.getMessage().contains(expectedMessage),
            "期望包含: " + expectedMessage + "，实际: " + exception.getMessage());
    }

    @Test
    void test_missingLocation() {
        ErrorException exception = assertThrows(ErrorException.class, () -> load(" "));

        assertTrue(exception.getMessage().contains("data-service.cdc.whitelist-location"), exception.getMessage());
    }
}
