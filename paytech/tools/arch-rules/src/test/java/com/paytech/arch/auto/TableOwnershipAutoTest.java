package com.paytech.arch.auto;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.paytech.arch.ownership.TableOwnership;

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
        Assertions.assertEquals(265 + 56 + 1 + 6 - 7 + 9 + 4 + 6 + 1 + 2, ownership.physicalTableCount(),
            "design 03 + Platform 保留表 + 第 12 步 t_card_material_ticket + account 明细账六张业务表（2026-10-19 重构）"
                + " - billing 经济事实三表与渠道成本四表 + billing 计费事实九表 + channel-connector 渠道成本四表（2026-10-19 计费事实重构）"
                + " + commercial 产品目录三表与合同订阅 / 开通 / 约束三表（2026-10-19 六层产品定价模型）"
                + " + account 账户号序列 + ledger 科目变更记录与账户号序列（2026-10-19 科目分级）");
        Assertions.assertEquals(57, ownership.databasesOf("platform-support").get("mor_platform").size());
        Assertions.assertEquals(31, ownership.databasesOf("ledger").get("mor_ledger").size());
        Assertions.assertTrue(ownership.allowedTables("ledger").containsAll(
            List.of("t_chart_account_change", "t_account_no_sequence")), "2026-10-19 科目分级新增的 ledger 表");
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
        // 第 07 步更名的经济事实三表已由 2026-10-19 计费事实重构删除，来源表名与第 07 步表名都不再出现。
        Assertions.assertTrue(ownership.services().stream()
            .noneMatch(service -> ownership.allowedTables(service).contains("t_transaction_economics")));
        Assertions.assertTrue(ownership.services().stream()
            .noneMatch(service -> ownership.allowedTables(service).contains("t_economic_fact")));
        // 第 11b 步拆表：t_account 更名 t_ledger_account 归 ledger；account 改连自有 mor_account，不再只读 mor_ledger。
        Assertions.assertTrue(ownership.allowedTables("ledger").contains("t_ledger_account"));
        Assertions.assertTrue(ownership.services().stream()
            .noneMatch(service -> ownership.allowedTables(service).contains("t_account")));
        // 2026-10-19 明细账重构：account 为 Owner 明细账，删除 t_account_balance、t_balance_applied_entry、t_balance_discrepancy。
        // 2026-10-19 科目分级：t_owner_account_no_sequence 为明细账账户分配可读账户号。
        Assertions.assertEquals(Set.of("t_owner_account", "t_account_posting_stream", "t_account_posting",
            "t_account_entry", "t_balance_applied_fact", "t_balance_movement", "t_compensation_event",
            "t_owner_account_no_sequence"),
            ownership.databasesOf("account").get("mor_account"));
        Assertions.assertFalse(ownership.allowedTables("account").contains("t_chart_account"));
        Assertions.assertTrue(ownership.transitionalReadsOf("account").isEmpty());
        // design/03 删除的表不属于任何重切服务；保留服务 Platform 的存量命令幂等表待专项迁移，仍归 Platform。
        Assertions.assertTrue(ownership.services().stream().filter(service -> !"platform-support".equals(service))
            .noneMatch(service -> ownership.allowedTables(service).contains("t_command_idempotency")));
        Assertions.assertTrue(ownership.allowedTables("platform-support").contains("t_command_idempotency"));
        Assertions.assertEquals(List.of("t_card_material_ticket", "t_payment_card", "t_payment_card_credential",
            "t_payment_card_instrument", "t_payment_stored_card"), ownership.cardTables());
    }

    /** 2026-10-19 计费事实重构：mor_billing 为 11 张表；渠道成本四表归 channel-connector（17 张），billing 不再引用。 */
    @Test
    void test_billingFactAndChannelCostOwnership() {
        Assertions.assertEquals(Set.of("t_billing_fact", "t_billing_fact_ref", "t_billing_fact_pricing",
            "t_billing_fact_line", "t_billing_fact_line_link", "t_business_calendar", "t_business_calendar_day",
            "t_consumed_event", "t_consumed_aggregate_version", "t_currency_spec", "t_compensation_event"),
            ownership.databasesOf("billing").get("mor_billing"));
        List<String> channelCostTables = List.of("t_channel_cost_plan", "t_channel_cost_plan_ver",
            "t_channel_cost_scope", "t_channel_cost_component");
        Assertions.assertEquals(17, ownership.databasesOf("channel-connector").get("mor_channel_connector").size());
        Assertions.assertTrue(ownership.databasesOf("channel-connector").get("mor_channel_connector")
            .containsAll(channelCostTables));
        Assertions.assertTrue(channelCostTables.stream().noneMatch(ownership.allowedTables("billing")::contains));
        Assertions.assertTrue(ownership.allowedTables("billing").containsAll(
            List.of("t_consumed_event", "t_consumed_aggregate_version")));
    }

    /** 第 12 步起卡表只在 mor_card：card 的 R4 白名单含卡表与读卡票据表，payment 与 channel-connector 不含。 */
    @Test
    void test_cardTablesOwnedByCard() {
        List<String> cardTables = List.of("t_payment_card", "t_payment_card_credential", "t_payment_card_instrument",
            "t_payment_stored_card", "t_card_material_ticket");
        Assertions.assertTrue(ownership.databasesOf("card").get("mor_card").containsAll(cardTables));
        Assertions.assertTrue(ownership.allowedTables("card").containsAll(List.of("t_consumed_event",
            "t_consumed_aggregate_version", "t_compensation_event")));
        Assertions.assertFalse(ownership.allowedTables("payment").contains("t_payment_card"));
        Assertions.assertFalse(ownership.allowedTables("channel-connector").contains("t_payment_card"));
    }

    @Test
    void test_noDataFound() {
        Assertions.assertEquals(Set.of(), ownership.allowedTables("api-gateway"));
        Assertions.assertEquals(Map.of(), ownership.databasesOf("vault"));
    }
}
