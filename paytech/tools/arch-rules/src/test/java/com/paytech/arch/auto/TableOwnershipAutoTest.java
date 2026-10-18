package com.paytech.arch.auto;

import com.paytech.arch.ownership.TableOwnership;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 表归属白名单（由 design/03 与保留服务的评审 Schema 生成）的内容与查询。
 *
 * @see com.paytech.arch.ownership.TableOwnership#loadDefault()
 * @author Claude
 */
class TableOwnershipAutoTest {

    private final TableOwnership ownership = TableOwnership.loadDefault();

    @Test
    void test_normalFlow() {
        // design/03 §4.2：17 个目标库共 265 张物理表；另加保留服务 platform-support 自身 Schema 的 56 张表。
        Assertions.assertEquals(265 + 56, ownership.physicalTableCount());
        Assertions.assertEquals(57, ownership.databasesOf("platform-support").get("mor_platform").size());
        Assertions.assertEquals(29, ownership.databasesOf("ledger").get("mor_ledger").size());
        Assertions.assertEquals(Set.of("mor_platform"), ownership.databasesOf("platform-support").keySet());
        Assertions.assertTrue(ownership.allowedTables("ledger").containsAll(
            List.of("t_journal_entry", "t_currency_spec", "t_compensation_event", "t_consumed_event",
                "t_consumed_aggregate_version")));
    }

    @Test
    void test_edgeCase() {
        // 两边各一份与只读副本。
        Assertions.assertTrue(ownership.allowedTables("client-master").contains("t_business_change_record"));
        Assertions.assertTrue(ownership.allowedTables("commercial").contains("t_business_change_record"));
        Assertions.assertTrue(ownership.allowedTables("billing").contains("t_currency_spec"));
        Assertions.assertFalse(ownership.allowedTables("tax").contains("t_currency_spec"));
        // 第 07 步更名：经济事实三表以新表名登记在 billing，旧表名不再出现。
        Assertions.assertTrue(ownership.allowedTables("billing").containsAll(
            List.of("t_economic_fact", "t_economic_fact_line", "t_economic_fact_line_link")));
        Assertions.assertTrue(ownership.services().stream()
            .noneMatch(service -> ownership.allowedTables(service).contains("t_transaction_economics")));
        // 共库过渡：account 只读 ledger 的三张表，不计入 account 归属。
        Assertions.assertTrue(ownership.allowedTables("account").containsAll(
            List.of("t_account", "t_chart_account", "t_ledger_book")));
        Assertions.assertEquals(Set.of("t_compensation_event"), ownership.databasesOf("account").get("mor_account"));
        Assertions.assertEquals(Set.of("t_account", "t_chart_account", "t_ledger_book"),
            ownership.transitionalReadsOf("account").get("mor_ledger"));
        // design/03 删除的表不属于任何重切服务；保留服务 Platform 的存量命令幂等表待专项迁移，仍归 Platform。
        Assertions.assertTrue(ownership.services().stream().filter(service -> !"platform-support".equals(service))
            .noneMatch(service -> ownership.allowedTables(service).contains("t_command_idempotency")));
        Assertions.assertTrue(ownership.allowedTables("platform-support").contains("t_command_idempotency"));
        Assertions.assertEquals(List.of("t_payment_card", "t_payment_card_credential", "t_payment_card_instrument",
            "t_payment_stored_card"), ownership.cardTables());
    }

    /** 第 12 步前卡表暂居 mor_payment：计入 payment 的 R4 白名单与 paytech-payment-card 的 R11 豁免，不重复计数。 */
    @Test
    void test_transitionalPlacement() {
        List<String> cardTables = List.of("t_payment_card", "t_payment_card_credential", "t_payment_card_instrument",
            "t_payment_stored_card");
        Assertions.assertTrue(ownership.allowedTables("payment").containsAll(cardTables));
        Assertions.assertFalse(ownership.databasesOf("payment").get("mor_payment").contains("t_payment_card"));
        Assertions.assertEquals(Set.of("paytech-payment-card"), ownership.transitionalCardModules());
        var placement = ownership.transitionalPlacements().getFirst();
        Assertions.assertEquals(List.of("payment", "mor_payment", "card", 12),
            List.of(placement.host(), placement.database(), placement.owner(), placement.untilStep()));
        Assertions.assertEquals(Set.copyOf(cardTables), placement.tables());
        Assertions.assertFalse(ownership.allowedTables("channel-connector").contains("t_payment_card"));
    }

    @Test
    void test_noDataFound() {
        Assertions.assertEquals(Set.of(), ownership.allowedTables("api-gateway"));
        Assertions.assertEquals(Map.of(), ownership.databasesOf("vault"));
    }
}
