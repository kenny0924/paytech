package com.paytech.card.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * mor_card 的断言查询、固件状态调整与清理 SQL：只触碰本库 8 张表（卡 4 + 读卡票据 + 补偿 + 消费记录 2，10 §3.1）；不代替被测生产 Mapper。
 */
public interface CardTestMapper {
    /** 检查隔离库身份，禁止清理共享库。 */
    @Select("SELECT DATABASE()")
    String databaseName();

    /** 本库 8 张表的总行数。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_payment_card) + (SELECT COUNT(*) FROM t_payment_card_credential) + (SELECT COUNT(*) FROM t_payment_card_instrument) + (SELECT COUNT(*) FROM t_payment_stored_card) + (SELECT COUNT(*) FROM t_card_material_ticket) + (SELECT COUNT(*) FROM t_compensation_event) + (SELECT COUNT(*) FROM t_consumed_event) + (SELECT COUNT(*) FROM t_consumed_aggregate_version)")
    int countRows();

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

    /** 清理消费聚合版本。 */
    @Delete("DELETE FROM t_consumed_aggregate_version")
    void clearConsumedVersions();

    /** 清理消费记录。 */
    @Delete("DELETE FROM t_consumed_event")
    void clearConsumedEvents();

    /** 清理补偿任务（card 目前不登记补偿事件，防御性清理）。 */
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
