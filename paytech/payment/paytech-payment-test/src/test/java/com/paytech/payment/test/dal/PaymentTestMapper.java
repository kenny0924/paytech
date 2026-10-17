package com.paytech.payment.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.*;

/** mor_payment 的断言查询与清理 SQL：只触碰本库 15 张表（payment 10 + 卡 4 + 补偿，10 §3.1）；不代替被测生产 Mapper。 */
public interface PaymentTestMapper {







    /** 检查隔离库身份，禁止清理共享库。 */
    @Select("SELECT DATABASE()")
    String databaseName();


    /** 读取执行链路五表、卡四表与补偿任务表总行数。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_payment_intent) + (SELECT COUNT(*) FROM t_payment_attempt) + (SELECT COUNT(*) FROM t_payment_instrument_token) + (SELECT COUNT(*) FROM t_payment_action) + (SELECT COUNT(*) FROM t_channel_transaction_ref) + (SELECT COUNT(*) FROM t_payment_card) + (SELECT COUNT(*) FROM t_payment_card_credential) + (SELECT COUNT(*) FROM t_payment_card_instrument) + (SELECT COUNT(*) FROM t_payment_stored_card) + (SELECT COUNT(*) FROM t_compensation_event)")
    int countExecutionRows();

    /** 读取本库仅 Schema 的五张表（路由决策、候选证据、重复支付、退款头寸、渠道退款）总行数，空基线必须为零。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_routing_decision) + (SELECT COUNT(*) FROM t_route_candidate_eval) + (SELECT COUNT(*) FROM t_duplicate_payment_case) + (SELECT COUNT(*) FROM t_payment_refund_position) + (SELECT COUNT(*) FROM t_channel_refund)")
    int countSchemaOnlyRows();

    /** 仅清理本测试库的候选证据（仅 Schema，防御性清理）。 */
    @Delete("DELETE FROM t_route_candidate_eval")
    void clearRouteCandidateEvals();

    /** 仅清理本测试库的路由决策（仅 Schema，防御性清理）。 */
    @Delete("DELETE FROM t_routing_decision")
    void clearRoutingDecisions();

    /** 仅清理本测试库的重复支付案例（仅 Schema，防御性清理）。 */
    @Delete("DELETE FROM t_duplicate_payment_case")
    void clearDuplicatePaymentCases();

    /** 仅清理本测试库的渠道退款（仅 Schema，防御性清理）。 */
    @Delete("DELETE FROM t_channel_refund")
    void clearChannelRefunds();

    /** 仅清理本测试库的退款头寸（仅 Schema，防御性清理）。 */
    @Delete("DELETE FROM t_payment_refund_position")
    void clearRefundPositions();

    /** 仅清理本测试库的补偿任务（本服务只有 PAYMENT_SUCCEEDED）；须在异步发布结束后执行。 */
    @Delete("DELETE FROM t_compensation_event")
    void clearCompensationEvents();

    /** 某类型、某事件键的补偿任务执行状态。 */
    @Select("SELECT execute_status AS status, sub_event_type AS subEventType, payload_json AS payload FROM t_compensation_event WHERE event_type = #{eventType} AND event_key = #{eventKey}")
    List<Map<String, Object>> compensationEvents(@Param("eventType") String eventType, @Param("eventKey") String eventKey);

    /** 仅清理本测试库的渠道交易引用。 */
    @Delete("DELETE FROM t_channel_transaction_ref")
    void clearTransactionRefs();

    /** 仅清理本测试库的支付动作。 */
    @Delete("DELETE FROM t_payment_action")
    void clearActions();

    /** 仅清理本测试库的支付尝试。 */
    @Delete("DELETE FROM t_payment_attempt")
    void clearAttempts();

    /** 仅清理本测试库的支付工具。 */
    @Delete("DELETE FROM t_payment_instrument_token")
    void clearInstrumentTokens();

    /** 仅清理本测试库的已存卡。 */
    @Delete("DELETE FROM t_payment_stored_card")
    void clearStoredCards();

    /** 全部已存卡，按创建先后排列。 */
    @Select("SELECT payment_stored_card_id AS storedCardId, stored_card_token AS token, client_id AS clientId, customer_id AS customerId, payment_card_id AS paymentCardId, payment_card_credential_id AS credentialId, stored_card_status AS status, source_card_instrument_id AS sourceInstrumentId, consent_time AS consentTime FROM t_payment_stored_card ORDER BY create_time, payment_stored_card_id")
    List<Map<String, Object>> storedCards();

    /** 仅清理本测试库的卡交易工具。 */
    @Delete("DELETE FROM t_payment_card_instrument")
    void clearCardInstruments();

    /** 仅清理本测试库的卡凭证。 */
    @Delete("DELETE FROM t_payment_card_credential")
    void clearCardCredentials();

    /** 仅清理本测试库的卡。 */
    @Delete("DELETE FROM t_payment_card")
    void clearCards();

    /** 本测试库全部卡交易工具 ID，用于清理对应的 Redis CVV。 */
    @Select("SELECT payment_card_instrument_id FROM t_payment_card_instrument")
    List<Long> cardInstrumentIds();

    /** 全部卡，按创建先后排列。 */
    @Select("SELECT payment_card_id AS paymentCardId, card_fingerprint AS cardFingerprint, card_bin AS cardBin, pan_last4 AS panLast4, encrypted_pan AS encryptedPan, dek_vault_token AS dekVaultToken FROM t_payment_card ORDER BY create_time, payment_card_id")
    List<Map<String, Object>> cards();

    /** 全部卡凭证，按创建先后排列。 */
    @Select("SELECT payment_card_credential_id AS credentialId, payment_card_id AS paymentCardId, credential_digest AS credentialDigest, encrypted_details AS encryptedDetails, expiry_month AS expiryMonth, expiry_year AS expiryYear, verified_time AS verifiedTime FROM t_payment_card_credential ORDER BY create_time, payment_card_credential_id")
    List<Map<String, Object>> cardCredentials();

    /** 全部卡交易工具，按创建先后排列。 */
    @Select("SELECT payment_card_instrument_id AS instrumentId, payment_card_credential_id AS credentialId, client_id AS clientId, customer_id AS customerId, save_requested AS saveRequested, payment_stored_card_id AS storedCardId FROM t_payment_card_instrument ORDER BY create_time, payment_card_instrument_id")
    List<Map<String, Object>> cardInstruments();

    /** 本测试库全部工具 Token 与工具引用。 */
    @Select("SELECT payment_instrument_token AS token, instrument_ref AS instrumentRef, expires_at AS expiresAt FROM t_payment_instrument_token")
    List<Map<String, Object>> instrumentTokens();

    /** 仅清理本测试库的支付意图。 */
    @Delete("DELETE FROM t_payment_intent")
    void clearIntents();

    /** 支付尝试状态、当前动作、渠道坐标与版本。 */
    @Select("SELECT attempt_status AS status, active_action_id AS activeActionId, channel_id AS channelId, channel_account_id AS channelAccountId, mid_id AS midId, version FROM t_payment_attempt WHERE payment_attempt_id = #{attemptId}")
    Map<String, Object> attempt(@Param("attemptId") long attemptId);

    /** 支付意图状态与活动/成功尝试。 */
    @Select("SELECT intent_status AS status, active_attempt_id AS activeAttemptId, successful_attempt_id AS successfulAttemptId FROM t_payment_intent WHERE payment_intent_id = #{intentId}")
    Map<String, Object> intent(@Param("intentId") long intentId);

    /** 某尝试的全部动作，按序号。 */
    @Select("SELECT payment_action_id AS actionId, action_type AS type, action_status AS status, interaction_type AS interactionType, channel_response_code AS code, channel_response_msg AS message, risk_result AS riskResult, transaction_assessment_id AS evaluationId, authentication_requirement AS authentication FROM t_payment_action WHERE payment_attempt_id = #{attemptId} ORDER BY action_no")
    List<Map<String, Object>> actions(@Param("attemptId") long attemptId);

    /** 全部渠道交易引用的外部编号与类型。 */
    @Select("SELECT reference_type AS type, external_transaction_id AS externalId, amount, currency FROM t_channel_transaction_ref")
    List<Map<String, Object>> transactionRefs();

    /** 执行链路各表的全部文本列拼接，用于检索不得落库的值。 */
    @Select("SELECT CONCAT_WS('|', (SELECT GROUP_CONCAT(CONCAT_WS(',', channel_response_code, channel_response_msg)) FROM t_payment_action), (SELECT GROUP_CONCAT(CONCAT_WS(',', payment_instrument_token, instrument_ref)) FROM t_payment_instrument_token), (SELECT GROUP_CONCAT(external_transaction_id) FROM t_channel_transaction_ref), (SELECT GROUP_CONCAT(CONCAT_WS(',', card_bin, pan_last4, card_fingerprint, dek_vault_token)) FROM t_payment_card), (SELECT GROUP_CONCAT(credential_digest) FROM t_payment_card_credential))")
    String executionText();











}
