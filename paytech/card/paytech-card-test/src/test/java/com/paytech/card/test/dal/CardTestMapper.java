package com.paytech.card.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * mor_card 的断言查询、固件状态调整与清理 SQL：只触碰本库 12 张表（卡 4 + 读卡票据 + BIN 库 4 + 补偿 + 消费记录 2，10 §3.1）；不代替被测生产
 * Mapper。BIN 库固件（版本、号段、激活记录）直接插入，不经被测导入链路。
 */
public interface CardTestMapper {
    /** 检查隔离库身份，禁止清理共享库。 */
    @Select("SELECT DATABASE()")
    String databaseName();

    /** 本库 8 张表的总行数。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_payment_card) + (SELECT COUNT(*) FROM t_payment_card_credential) + (SELECT COUNT(*) FROM t_payment_card_instrument) + (SELECT COUNT(*) FROM t_payment_stored_card) + (SELECT COUNT(*) FROM t_card_material_ticket) + (SELECT COUNT(*) FROM t_compensation_event) + (SELECT COUNT(*) FROM t_consumed_event) + (SELECT COUNT(*) FROM t_consumed_aggregate_version) + (SELECT COUNT(*) FROM t_card_bin_library_version) + (SELECT COUNT(*) FROM t_card_bin_range) + (SELECT COUNT(*) FROM t_card_bin_import_error) + (SELECT COUNT(*) FROM t_card_bin_library_activation)")
    int countRows();

    /** 固件：插入一个 BIN 库版本（状态、生效占位与行数由用例给定）。 */
    @Insert("INSERT INTO t_card_bin_library_version (version_id, version_no, status, active_slot, file_address, file_content_hash, file_name, total_rows, processed_rows, error_rows, failure_reason, created_by, version, create_time, update_time) VALUES (#{versionId}, #{versionNo}, #{status}, #{activeSlot}, NULL, NULL, NULL, #{totalRows}, #{totalRows}, 0, NULL, NULL, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))")
    int insertBinVersion(@Param("versionId") long versionId, @Param("versionNo") int versionNo, @Param("status") String status,
        @Param("activeSlot") Integer activeSlot, @Param("totalRows") Integer totalRows);

    /** 固件：插入一个待导入（VALIDATING）的版本，冻结给定文件地址与摘要。 */
    @Insert("INSERT INTO t_card_bin_library_version (version_id, version_no, status, active_slot, file_address, file_content_hash, file_name, total_rows, processed_rows, error_rows, failure_reason, created_by, version, create_time, update_time) VALUES (#{versionId}, #{versionNo}, 'VALIDATING', NULL, #{fileAddress}, #{fileContentHash}, 'bin.csv', NULL, 0, 0, NULL, 900000000000000123, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))")
    int insertImportingVersion(@Param("versionId") long versionId, @Param("versionNo") int versionNo,
        @Param("fileAddress") String fileAddress, @Param("fileContentHash") String fileContentHash);

    /** 固件：插入一个号段。 */
    @Insert("INSERT INTO t_card_bin_range (range_id, version_id, bin_prefix, prefix_length, card_brand, funding_type, card_level, card_product, issuer_country, issuer_region, issuer_name, create_time, update_time) VALUES (#{rangeId}, #{versionId}, #{binPrefix}, CHAR_LENGTH(#{binPrefix}), #{cardBrand}, #{fundingType}, #{cardLevel}, #{cardProduct}, #{issuerCountry}, #{issuerRegion}, #{issuerName}, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))")
    int insertBinRange(@Param("rangeId") long rangeId, @Param("versionId") long versionId, @Param("binPrefix") String binPrefix,
        @Param("cardBrand") String cardBrand, @Param("fundingType") String fundingType, @Param("cardLevel") String cardLevel,
        @Param("cardProduct") String cardProduct, @Param("issuerCountry") String issuerCountry,
        @Param("issuerRegion") String issuerRegion, @Param("issuerName") String issuerName);

    /** 全部 BIN 库版本，按版本号排列。 */
    @Select("SELECT version_id AS versionId, version_no AS versionNo, status, active_slot AS activeSlot, file_address AS fileAddress, file_content_hash AS fileContentHash, file_name AS fileName, total_rows AS totalRows, processed_rows AS processedRows, error_rows AS errorRows, failure_reason AS failureReason, created_by AS createdBy, version FROM t_card_bin_library_version ORDER BY version_no")
    List<Map<String, Object>> binVersions();

    /** 某版本的号段数。 */
    @Select("SELECT COUNT(*) FROM t_card_bin_range WHERE version_id = #{versionId}")
    int countBinRanges(@Param("versionId") long versionId);

    /** 某版本的号段，按前缀排列。 */
    @Select("SELECT bin_prefix AS binPrefix, prefix_length AS prefixLength, card_brand AS cardBrand, funding_type AS fundingType, card_level AS cardLevel, card_product AS cardProduct, issuer_country AS issuerCountry, issuer_region AS issuerRegion, issuer_name AS issuerName FROM t_card_bin_range WHERE version_id = #{versionId} ORDER BY bin_prefix")
    List<Map<String, Object>> binRanges(@Param("versionId") long versionId);

    /** 某版本的导入错误明细，按行号排列。 */
    @Select("SELECT line_no AS lineNo, column_name AS columnName, error_code AS errorCode, error_message AS errorMessage, value_excerpt AS valueExcerpt FROM t_card_bin_import_error WHERE version_id = #{versionId} ORDER BY line_no, error_id")
    List<Map<String, Object>> binImportErrors(@Param("versionId") long versionId);

    /** 全部激活记录，按时间排列。 */
    @Select("SELECT version_id AS versionId, previous_version_id AS previousVersionId, activation_type AS activationType, operator_account_id AS operatorAccountId FROM t_card_bin_library_activation ORDER BY create_time, activation_id")
    List<Map<String, Object>> binActivations();

    /** 全部本地补偿任务。 */
    @Select("SELECT event_key AS eventKey, event_type AS eventType, sub_event_type AS subEventType, execute_status AS executeStatus, execute_count AS executeCount, last_error_code AS lastErrorCode FROM t_compensation_event ORDER BY id")
    List<Map<String, Object>> compensationEvents();

    /** 执行中的本地补偿任务数（清理前须为 0）。 */
    @Select("SELECT COUNT(*) FROM t_compensation_event WHERE execute_status = 'PROCESSING'")
    int countProcessingCompensationEvents();

    /** 固件：让等待退避的补偿任务立即到期，由真实 Scanner 唤醒（不改变执行次数与状态）。 */
    @Update("UPDATE t_compensation_event SET next_execute_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND) WHERE event_key = #{eventKey} AND execute_status = 'ERROR'")
    int makeCompensationDue(@Param("eventKey") String eventKey);

    /** 全部卡，按创建先后排列。 */
    @Select("SELECT payment_card_id AS paymentCardId, card_fingerprint AS cardFingerprint, card_bin AS cardBin, pan_last4 AS panLast4, encrypted_pan AS encryptedPan, dek_vault_token AS dekVaultToken FROM t_payment_card ORDER BY create_time, payment_card_id")
    List<Map<String, Object>> cards();

    /** 全部卡凭证，按创建先后排列。 */
    @Select("SELECT payment_card_credential_id AS credentialId, payment_card_id AS paymentCardId, credential_digest AS credentialDigest, encrypted_details AS encryptedDetails, expiry_month AS expiryMonth, expiry_year AS expiryYear, verified_time AS verifiedTime FROM t_payment_card_credential ORDER BY create_time, payment_card_credential_id")
    List<Map<String, Object>> cardCredentials();

    /** 全部卡交易工具，按创建先后排列。 */
    @Select("SELECT payment_card_instrument_id AS instrumentId, payment_card_credential_id AS credentialId, client_id AS clientId, source_submission_id AS sourceSubmissionId, customer_id AS customerId, save_requested AS saveRequested, payment_stored_card_id AS storedCardId, expires_at AS expiresAt FROM t_payment_card_instrument ORDER BY create_time, payment_card_instrument_id")
    List<Map<String, Object>> cardInstruments();

    /** 全部已存卡，按创建先后排列。 */
    @Select("SELECT payment_stored_card_id AS storedCardId, stored_card_token AS token, client_id AS clientId, customer_id AS customerId, payment_card_id AS paymentCardId, payment_card_credential_id AS credentialId, stored_card_status AS status, source_card_instrument_id AS sourceInstrumentId, consent_time AS consentTime FROM t_payment_stored_card ORDER BY create_time, payment_stored_card_id")
    List<Map<String, Object>> storedCards();

    /** 全部读卡票据，按签发先后排列（只有摘要）。 */
    @Select("SELECT card_material_ticket_id AS ticketId, ticket_hash AS ticketHash, instrument_ref AS instrumentRef, attempt_id AS attemptId, action_id AS actionId, purpose, expires_at AS expiresAt, used_at AS usedAt, caller FROM t_card_material_ticket ORDER BY create_time, card_material_ticket_id")
    List<Map<String, Object>> tickets();

    /** 已消费的领域事件 ID。 */
    @Select("SELECT event_id FROM t_consumed_event ORDER BY consumed_at")
    List<String> consumedEventIds();

    /** 本测试库全部卡交易工具 ID，用于清理对应的 Redis CVV。 */
    @Select("SELECT payment_card_instrument_id FROM t_payment_card_instrument")
    List<Long> cardInstrumentIds();

    /** 固件：把工具期限移到给定秒数之前（模拟已过期或已过宽限期）。 */
    @Update("UPDATE t_payment_card_instrument SET expires_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL #{seconds} SECOND) WHERE payment_card_instrument_id = #{instrumentId}")
    int expireInstrument(@Param("instrumentId") long instrumentId, @Param("seconds") long seconds);

    /** 固件：把票据期限移到当前之前（模拟已过期）。 */
    @Update("UPDATE t_card_material_ticket SET expires_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND) WHERE ticket_hash = #{ticketHash}")
    int expireTicket(@Param("ticketHash") String ticketHash);

    /** 固件：改写票据用途（模拟用途不符的票据）。 */
    @Update("UPDATE t_card_material_ticket SET purpose = #{purpose} WHERE ticket_hash = #{ticketHash}")
    int changeTicketPurpose(@Param("ticketHash") String ticketHash, @Param("purpose") String purpose);

    /** 清理 BIN 库激活记录。 */
    @Delete("DELETE FROM t_card_bin_library_activation")
    void clearBinActivations();

    /** 清理 BIN 库导入错误明细。 */
    @Delete("DELETE FROM t_card_bin_import_error")
    void clearBinImportErrors();

    /** 清理 BIN 号段。 */
    @Delete("DELETE FROM t_card_bin_range")
    void clearBinRanges();

    /** 清理 BIN 库版本。 */
    @Delete("DELETE FROM t_card_bin_library_version")
    void clearBinVersions();

    /** 清理消费聚合版本。 */
    @Delete("DELETE FROM t_consumed_aggregate_version")
    void clearConsumedVersions();

    /** 清理消费记录。 */
    @Delete("DELETE FROM t_consumed_event")
    void clearConsumedEvents();

    /** 清理补偿任务（BIN 库导入任务）。 */
    @Delete("DELETE FROM t_compensation_event")
    void clearCompensationEvents();

    /** 清理读卡票据。 */
    @Delete("DELETE FROM t_card_material_ticket")
    void clearTickets();

    /** 清理已存卡。 */
    @Delete("DELETE FROM t_payment_stored_card")
    void clearStoredCards();

    /** 清理卡交易工具。 */
    @Delete("DELETE FROM t_payment_card_instrument")
    void clearCardInstruments();

    /** 清理卡凭证。 */
    @Delete("DELETE FROM t_payment_card_credential")
    void clearCardCredentials();

    /** 清理卡。 */
    @Delete("DELETE FROM t_payment_card")
    void clearCards();
}
