package com.paytech.arch.auto;

import com.paytech.arch.ownership.TableOwnership;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 表归属白名单（由 design/03 生成）的内容与查询。
 *
 * @see com.paytech.arch.ownership.TableOwnership#loadDefault()
 * @author Claude
 */
class TableOwnershipAutoTest {

    private final TableOwnership ownership = TableOwnership.loadDefault();

    @Test
    void test_normalFlow() {
        // design/03 §4.2：17 个目标库共 265 张物理表。
        Assertions.assertEquals(265, ownership.physicalTableCount());
        Assertions.assertEquals(29, ownership.databasesOf("ledger").get("mor_ledger").size());
        Assertions.assertEquals(Set.of("mor_platform"), ownership.databasesOf("platform-support").keySet());
        Assertions.assertTrue(ownership.allowedTables("ledger").containsAll(
            List.of("t_journal_entry", "t_currency_spec", "t_compensation_event", "t_consumed_event")));
    }

    @Test
    void test_edgeCase() {
        // 两边各一份与只读副本。
        Assertions.assertTrue(ownership.allowedTables("client-master").contains("t_business_change_record"));
        Assertions.assertTrue(ownership.allowedTables("commercial").contains("t_business_change_record"));
        Assertions.assertTrue(ownership.allowedTables("billing").contains("t_currency_spec"));
        Assertions.assertFalse(ownership.allowedTables("tax").contains("t_currency_spec"));
        // 已删除的表不属于任何服务。
        Assertions.assertTrue(ownership.services().stream()
            .noneMatch(service -> ownership.allowedTables(service).contains("t_command_idempotency")));
        Assertions.assertEquals(List.of("t_payment_card", "t_payment_card_credential", "t_payment_card_instrument",
            "t_payment_stored_card"), ownership.cardTables());
    }

    @Test
    void test_noDataFound() {
        Assertions.assertEquals(Set.of(), ownership.allowedTables("api-gateway"));
        Assertions.assertEquals(Map.of(), ownership.databasesOf("vault"));
    }
}
