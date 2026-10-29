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
        // design/03 §4.2：17 个目标库共 265 张物理表；另加保留服务 platform-support 自身 Schema 的 46 张表
        // （2026-10-20 渠道协议 10 表迁往 channel-connector 后由 56 张减为 46 张，总表数不变）。
        Assertions.assertEquals(265 + 46 + 1 + 6 - 7 + 9 + 4 + 6 + 1 + 2 + 1 - 13 + 10 + 2 + 10 - 2 - 2 + 2 + 3 + 10 + 3 - 1 + 2 + 1 + 4 + 1 + 7 + 3 + 1 + 13 + 4 + 3 + 3, ownership.physicalTableCount(),
            "design 03 + Platform 保留表 + 第 12 步 t_card_material_ticket + account 明细账六张业务表（2026-10-19 重构）"
                + " - billing 经济事实三表与渠道成本四表 + billing 计费事实九表 + channel-connector 渠道成本四表（2026-10-19 计费事实重构）"
                + " + commercial 产品目录三表与合同订阅 / 开通 / 约束三表（2026-10-19 六层产品定价模型）"
                + " + account 账户号序列 + ledger 科目变更记录与账户号序列（2026-10-19 科目分级）"
                + " + channel-connector 变更记录表 t_channel_config_change（2026-10-20）"
                + " - reconciliation 旧对账十三表 + 新模型八张业务表与消费幂等两表（2026-10-20 对账切片 1）"
                + " + commercial 可配维度与价格协议签署两表（2026-10-20 开通与定价模型调整）"
                + " + channel-connector 渠道协议 10 表（2026-10-20 统一对外渠道出口第一段，自 Platform 迁入）"
                + " - ledger 与 billing 的 t_currency_spec 只读副本（2026-10-20 币种快照读取）"
                + " - t_payment_channel 与 t_integration + t_channel 与 t_channel_call_grant（2026-10-22 统一对外渠道出口第二段，"
                + "t_integration_* 7 表改名 t_channel_*，表数不变）"
                + "；+ Platform 文件存储 3 表（2026-10-22 文件多存储并存）+ Platform 工作流 10 表（2026-10-22 工作流并入 Platform Support）"
                + " + Platform data-query 数据源与查询模板三表（2026-10-22）"
                + " - Platform t_file_owner_ref（2026-10-22 文件多存储并存第二段）"
                + " + payment 消费幂等两表（2026-10-22 3DS 全链路）+ Platform 国家区域映射 t_country_region + card BIN 库四表（2026-10-22 BIN 库与卡属性）"
                + " + Platform t_file_version（2026-10-22 文件公开桶与同地址替换）"
                + " + Platform 多语言七表（2026-10-23 多语言管理）"
                + " + client-master 角色与关系三表 + acquiring t_order_relationship_binding（2026-10-24 商业关系快照与真实解析接入）"
                + " + client-master 入驻配置与入驻码 13 表 + compliance 程序文档类型 / 命中名单 / 风险因子 / 退回错误 4 表（2026-10-27 全球合规入驻切片 1）"
                + " + Platform 行政区 / 行业 / MCC 三表（2026-10-27 参考数据目录；t_currency_spec、t_country_region 改名 t_ref_currency、t_ref_country，表数不变）"
                + " + risk-decision 承保记录与自动承保规则版本 / 阈值三表（2026-10-29 REQ-03 承保记录）");
        // mor_platform：2026-10-20 渠道协议 10 表迁往 channel-connector，由 57 张减为 47 张；2026-10-22 文件存储 3 表、工作流 10 表与 data-query 3 表并入，
        // 第二段删除 t_file_owner_ref，卡 BIN 库新增 t_country_region，文件公开桶新增 t_file_version，共 64 张；2026-10-23 多语言新增七张 t_i18n_*，共 71 张；
        // 2026-10-27 参考数据目录：币种与国家两表改名 t_ref_*，新增行政区、行业、MCC 三表，共 74 张。
        Assertions.assertEquals(74, ownership.databasesOf("platform-support").get("mor_platform").size());
        Assertions.assertTrue(ownership.allowedTables("platform-support").containsAll(
            List.of("t_workflow_definition", "t_workflow_instance", "t_workflow_callback")), "2026-10-22 工作流表");
        Assertions.assertEquals(30, ownership.databasesOf("ledger").get("mor_ledger").size());
        Assertions.assertTrue(ownership.allowedTables("ledger").containsAll(
            List.of("t_chart_account_change", "t_account_no_sequence")), "2026-10-19 科目分级新增的 ledger 表");
        Assertions.assertEquals(Set.of("mor_platform"), ownership.databasesOf("platform-support").keySet());
        Assertions.assertTrue(ownership.allowedTables("ledger").containsAll(
            List.of("t_journal_entry", "t_compensation_event", "t_consumed_event",
                "t_consumed_aggregate_version")));
    }

    @Test
    void test_edgeCase() {
        // 两边各一份；币种表只在 Platform（2026-10-20 起 ledger、billing 读快照，不持副本；2026-10-27 改名 t_ref_currency）。
        Assertions.assertTrue(ownership.allowedTables("client-master").contains("t_business_change_record"));
        Assertions.assertTrue(ownership.allowedTables("commercial").contains("t_business_change_record"));
        Assertions.assertTrue(ownership.allowedTables("platform-support").containsAll(List.of("t_ref_currency",
            "t_ref_country", "t_ref_country_subdivision", "t_ref_industry", "t_ref_mcc")), "2026-10-27 参考数据目录");
        Assertions.assertTrue(ownership.services().stream().noneMatch(service -> ownership.allowedTables(service)
            .contains("t_currency_spec") || ownership.allowedTables(service).contains("t_country_region")), "改名前的旧表名");
        Assertions.assertFalse(ownership.allowedTables("billing").contains("t_ref_currency"));
        Assertions.assertFalse(ownership.allowedTables("ledger").contains("t_ref_currency"));
        Assertions.assertFalse(ownership.allowedTables("tax").contains("t_ref_currency"));
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
        // 2026-10-22 BIN 库与卡属性：card 新增 BIN 库四表，同样受卡域隔离（R11）。
        Assertions.assertEquals(List.of("t_card_bin_import_error", "t_card_bin_library_activation",
            "t_card_bin_library_version", "t_card_bin_range", "t_card_material_ticket", "t_payment_card",
            "t_payment_card_credential", "t_payment_card_instrument", "t_payment_stored_card"), ownership.cardTables());
    }

    /**
     * 2026-10-19 计费事实重构：mor_billing 为 10 张表（2026-10-20 删除 t_currency_spec 副本）；渠道成本四表归 channel-connector，billing 不再引用。
     * mor_channel_connector 2026-10-20 加变更记录表后 18 张，同日统一对外渠道出口第一段迁入渠道协议 10 表后 28 张；
     * 2026-10-22 第二段 t_payment_channel 改名 t_channel 并合并 t_integration（−1）、新增 t_channel_call_grant（+1），仍为 28 张。
     */
    @Test
    void test_billingFactAndChannelCostOwnership() {
        Assertions.assertEquals(Set.of("t_billing_fact", "t_billing_fact_ref", "t_billing_fact_pricing",
            "t_billing_fact_line", "t_billing_fact_line_link", "t_business_calendar", "t_business_calendar_day",
            "t_consumed_event", "t_consumed_aggregate_version", "t_compensation_event"),
            ownership.databasesOf("billing").get("mor_billing"));
        List<String> channelCostTables = List.of("t_channel_cost_plan", "t_channel_cost_plan_ver",
            "t_channel_cost_scope", "t_channel_cost_component");
        Assertions.assertEquals(28, ownership.databasesOf("channel-connector").get("mor_channel_connector").size());
        Assertions.assertTrue(ownership.databasesOf("channel-connector").get("mor_channel_connector")
            .containsAll(channelCostTables));
        Assertions.assertTrue(channelCostTables.stream().noneMatch(ownership.allowedTables("billing")::contains));
        Assertions.assertTrue(ownership.allowedTables("billing").containsAll(
            List.of("t_consumed_event", "t_consumed_aggregate_version")));
    }

    /** 2026-10-20 对账切片 1：mor_reconciliation 重建为 15 张表（12 张业务表 + 补偿事件 + 消费幂等两表），旧对账表全部移除。 */
    @Test
    void test_reconciliationOwnership() {
        Assertions.assertEquals(Set.of("t_clearing_account", "t_clearing_account_mid", "t_platform_txn",
            "t_channel_statement", "t_channel_statement_line", "t_cash_account", "t_bank_statement",
            "t_bank_statement_line", "t_expected_bank_movement", "t_bank_match", "t_recon_exception",
            "t_recon_resolution", "t_compensation_event", "t_consumed_event", "t_consumed_aggregate_version"),
            ownership.databasesOf("reconciliation").get("mor_reconciliation"));
        Assertions.assertTrue(List.of("t_channel_account", "t_channel_settle_batch", "t_bank_recon_match",
            "t_cash_account_binding", "t_channel_cost_confirm").stream()
            .noneMatch(ownership.allowedTables("reconciliation")::contains));
        Assertions.assertTrue(ownership.allowedTables("channel-connector").contains("t_channel_account"),
            "channel-connector 的渠道技术账号表与已删除的旧清算账户同名，归属不受影响");
    }

    /**
     * 2026-10-20 统一对外渠道出口第一段：渠道协议表只归 channel-connector，Platform 不再拥有。
     * 2026-10-22 第二段：t_payment_channel 改名 t_channel 并合并 t_integration，t_integration_* 7 表改名 t_channel_*，
     * 新增 t_channel_call_grant；旧表名不再归任何服务。
     */
    @Test
    void test_channelProtocolTablesOwnedByChannelConnector() {
        List<String> protocolTables = List.of("t_channel", "t_standard_api", "t_api_field", "t_channel_api",
            "t_channel_script", "t_channel_standard_api", "t_channel_verify_account", "t_channel_verify_case",
            "t_channel_verify_run", "t_channel_verify_run_step", "t_channel_call_grant");
        Assertions.assertTrue(ownership.databasesOf("channel-connector").get("mor_channel_connector")
            .containsAll(protocolTables));
        Assertions.assertTrue(protocolTables.stream().noneMatch(ownership.allowedTables("platform-support")::contains));
        List<String> retiredTables = List.of("t_payment_channel", "t_integration", "t_integration_api",
            "t_integration_script", "t_integration_standard_api", "t_integration_verify_account",
            "t_integration_verify_case", "t_integration_verify_run", "t_integration_verify_run_step");
        Assertions.assertTrue(retiredTables.stream().noneMatch(ownership.allowedTables("channel-connector")::contains));
        Assertions.assertTrue(retiredTables.stream().noneMatch(ownership.allowedTables("platform-support")::contains));
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
