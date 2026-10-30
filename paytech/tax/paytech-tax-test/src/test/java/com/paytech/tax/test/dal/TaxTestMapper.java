package com.paytech.tax.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * tax 测试专用前置数据、只读断言与全局清理；只触碰 mor_tax 的 16 张表
 * <li>初始化 DML 的保留行（ID 1–999：税类、三国地区包、AE001 演示注册、准入与号码序列）只读，清理时保留。</li>
 */
@Mapper
public interface TaxTestMapper {

    /** 统计测试可写入的行数：运行时表全部行，主数据表只统计 1000 以上的行。 */
    @Select("""
        SELECT 't_tax_calculation' t, COUNT(*) c FROM t_tax_calculation UNION ALL
        SELECT 't_tax_calculation_line', COUNT(*) FROM t_tax_calculation_line UNION ALL
        SELECT 't_tax_calculation_component', COUNT(*) FROM t_tax_calculation_component UNION ALL
        SELECT 't_tax_evidence', COUNT(*) FROM t_tax_evidence UNION ALL
        SELECT 't_tax_transaction', COUNT(*) FROM t_tax_transaction UNION ALL
        SELECT 't_tax_reporting_entry', COUNT(*) FROM t_tax_reporting_entry UNION ALL
        SELECT 't_tax_period', COUNT(*) FROM t_tax_period UNION ALL
        SELECT 't_tax_document', COUNT(*) FROM t_tax_document UNION ALL
        SELECT 't_compensation_event', COUNT(*) FROM t_compensation_event UNION ALL
        SELECT 't_consumed_event', COUNT(*) FROM t_consumed_event UNION ALL
        SELECT 't_consumed_aggregate_version', COUNT(*) FROM t_consumed_aggregate_version UNION ALL
        SELECT 't_tax_category', COUNT(*) FROM t_tax_category WHERE id >= 1000 UNION ALL
        SELECT 't_tax_market', COUNT(*) FROM t_tax_market WHERE id >= 1000 UNION ALL
        SELECT 't_tax_registration', COUNT(*) FROM t_tax_registration WHERE id >= 1000 UNION ALL
        SELECT 't_tax_policy', COUNT(*) FROM t_tax_policy WHERE id >= 1000 UNION ALL
        SELECT 't_tax_document_sequence', COUNT(*) FROM t_tax_document_sequence WHERE id >= 1000
        """)
    List<Map<String, Object>> countWritableRows();

    /** 初始化 DML 保留行数（应为 1 + 3 + 3 + 3 + 3 = 13）。 */
    @Select("""
        SELECT (SELECT COUNT(*) FROM t_tax_category WHERE id < 1000) + (SELECT COUNT(*) FROM t_tax_policy WHERE id < 1000)
          + (SELECT COUNT(*) FROM t_tax_registration WHERE id < 1000) + (SELECT COUNT(*) FROM t_tax_market WHERE id < 1000)
          + (SELECT COUNT(*) FROM t_tax_document_sequence WHERE id < 1000)
        """)
    int countReservedRows();

    /** 删除一张运行时表的全部行；表名只来自清理组件的固定清单。 */
    @Delete("DELETE FROM ${table}")
    void deleteAll(@Param("table") String table);

    /** 删除一张主数据表的非保留行（ID 1000 以上）。 */
    @Delete("DELETE FROM ${table} WHERE id >= 1000")
    void deleteNonReserved(@Param("table") String table);

    /** 插入税类。 */
    @Insert("""
        INSERT INTO t_tax_category (id, category_code, name, description, status, create_time, update_time)
        VALUES (#{id}, #{code}, #{code}, NULL, #{status}, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
        """)
    void insertCategory(@Param("id") long id, @Param("code") String code, @Param("status") String status);

    /** 插入注册。 */
    @Insert("""
        INSERT INTO t_tax_registration (id, subject_party_id, jurisdiction_code, authority_code, tax_type, scheme_code,
          registration_no_ref, public_registration_no, registered_name, effective_from, effective_to, status,
          reporting_currency, legal_timezone, filing_frequency, create_time, update_time)
        VALUES (#{id}, #{subject}, #{jurisdiction}, CONCAT(#{jurisdiction}, '_TEST_AUTHORITY'), 'VAT', 'STANDARD',
          CONCAT('TEST-REF-', #{id}), CONCAT('TEST-TRN-', #{jurisdiction}), 'Test Seller LLC', '2026-01-01', NULL, #{status},
          #{reportingCurrency}, #{timezone}, #{frequency}, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
        """)
    void insertRegistration(@Param("id") long id, @Param("subject") long subject, @Param("jurisdiction") String jurisdiction,
        @Param("status") String status, @Param("reportingCurrency") String reportingCurrency,
        @Param("timezone") String timezone, @Param("frequency") String frequency);

    /** 插入已批准的准入。 */
    @Insert("""
        INSERT INTO t_tax_market (id, subject_party_id, jurisdiction_code, tax_type, scheme_code, assessment_no, revision,
          valid_from, valid_to, obligation, collection_status, managed_by, registration_id, policy_id, approved_by,
          approved_at, review_due_at, create_time, update_time)
        VALUES (#{id}, #{subject}, #{jurisdiction}, 'VAT', 'STANDARD', CONCAT('TEST-', #{id}), 1, '2026-01-01', NULL,
          #{obligation}, #{collectionStatus}, 'PLATFORM_OPERATIONS', #{registrationId}, #{policyId}, 'TEST',
          UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
        """)
    void insertMarket(@Param("id") long id, @Param("subject") long subject, @Param("jurisdiction") String jurisdiction,
        @Param("obligation") String obligation, @Param("collectionStatus") String collectionStatus,
        @Param("registrationId") Long registrationId, @Param("policyId") Long policyId);

    /** 插入已批准的地区包（内容由用例给出）。 */
    @Insert("""
        INSERT INTO t_tax_policy (id, package_code, revision, jurisdiction_code, tax_type, scheme_code, effective_from,
          effective_to, schema_version, status, policy_json, source_refs_json, approved_by, approved_at, create_time, update_time)
        VALUES (#{id}, CONCAT('TEST_', #{id}), 1, #{jurisdiction}, 'VAT', 'STANDARD', '2026-01-01', NULL, 1, 'APPROVED',
          #{policyJson}, '[]', 'TEST', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
        """)
    void insertPolicy(@Param("id") long id, @Param("jurisdiction") String jurisdiction,
        @Param("policyJson") String policyJson);

    /** 初始化 DML 的地区包内容。 */
    @Select("SELECT policy_json FROM t_tax_policy WHERE id = #{id}")
    String selectPolicyJson(@Param("id") long id);

    /** 本库计算数。 */
    @Select("SELECT COUNT(*) FROM t_tax_calculation")
    int countCalculations();

    /** 计算头（状态与金额）。 */
    @Select("""
        SELECT status, calculation_stage, tax_amount_source, net_amount, charged_tax_amount, gross_amount, rounding_amount,
          expires_at, block_reasons_json FROM t_tax_calculation WHERE id = #{id}
        """)
    Map<String, Object> selectCalculation(@Param("id") long id);

    /** 计算行，按行号升序。 */
    @Select("""
        SELECT line_no, net_amount, charged_tax_amount, gross_amount, rounding_delta FROM t_tax_calculation_line
        WHERE calculation_id = #{id} ORDER BY line_no
        """)
    List<Map<String, Object>> selectLines(@Param("id") long id);

    /** 计算组件。 */
    @Select("""
        SELECT treatment, rule_code, reason_code, rate, tax_base, charged_tax_amount, registration_id, policy_id
        FROM t_tax_calculation_component WHERE calculation_id = #{id} ORDER BY id
        """)
    List<Map<String, Object>> selectComponents(@Param("id") long id);

    /** 计算的证据数。 */
    @Select("SELECT COUNT(*) FROM t_tax_evidence WHERE calculation_id = #{id}")
    int countEvidence(@Param("id") long id);

    /** 计算的税务责任方（可空）。 */
    @Select("SELECT tax_subject_party_id FROM t_tax_calculation WHERE id = #{id}")
    Long selectTaxSubject(@Param("id") long id);

    /** 正式交易数。 */
    @Select("SELECT COUNT(*) FROM t_tax_transaction")
    int countTransactions();

    /** 订单的正式交易。 */
    @Select("""
        SELECT id, calculation_id, reporting_status, reporting_block_reason, tax_point_date, source_payload_hash
        FROM t_tax_transaction WHERE source_fact_id = #{orderId}
        """)
    Map<String, Object> selectTransaction(@Param("orderId") String orderId);

    /** 交易的底稿与期间。 */
    @Select("""
        SELECT e.entry_role, e.source_tax_amount, e.reporting_currency, e.fx_rate, e.reporting_tax_amount, e.tax_point_date,
          p.period_start, p.period_end, p.filing_due_date, p.status AS period_status
        FROM t_tax_reporting_entry e JOIN t_tax_period p ON p.id = e.period_id WHERE e.transaction_id = #{transactionId}
        """)
    List<Map<String, Object>> selectEntries(@Param("transactionId") long transactionId);

    /** 期间数。 */
    @Select("SELECT COUNT(*) FROM t_tax_period")
    int countPeriods();

    /** 交易的单据。 */
    @Select("""
        SELECT id, document_number, series_code, issue_status, document_file_address, document_digest, document_content_json
        FROM t_tax_document WHERE transaction_id = #{transactionId}
        """)
    Map<String, Object> selectDocument(@Param("transactionId") long transactionId);

    /** 全部单据号码。 */
    @Select("SELECT document_number FROM t_tax_document ORDER BY document_number")
    List<String> selectDocumentNumbers();

    /** 补偿任务（按类型）。 */
    @Select("SELECT event_key, execute_status, execute_count FROM t_compensation_event WHERE event_type = #{eventType}")
    List<Map<String, Object>> selectCompensationEvents(@Param("eventType") String eventType);

    /** 是否已登记消费该事件。 */
    @Select("SELECT COUNT(*) FROM t_consumed_event WHERE event_id = #{eventId}")
    int countConsumedEvent(@Param("eventId") String eventId);
}
