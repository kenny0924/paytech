package com.paytech.payment.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.*;

/**
 * mor_payment 的断言查询与清理 SQL：只触碰本库 13 张表（payment 10 + 补偿 + 3DS 全链路的两张 messaging 消费表，10 §3.1；卡表第 12 步迁入
 * mor_card）；不代替被测生产 Mapper。
 */
public interface PaymentTestMapper {







    /** 检查隔离库身份，禁止清理共享库。 */
    @Select("SELECT DATABASE()")
    String databaseName();


    /** 读取执行链路五表与补偿任务表总行数。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_payment_intent) + (SELECT COUNT(*) FROM t_payment_attempt) + (SELECT COUNT(*) FROM t_payment_instrument_token) + (SELECT COUNT(*) FROM t_payment_action) + (SELECT COUNT(*) FROM t_channel_transaction_ref) + (SELECT COUNT(*) FROM t_compensation_event) + (SELECT COUNT(*) FROM t_consumed_event) + (SELECT COUNT(*) FROM t_consumed_aggregate_version)")
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

    /** 仅清理本测试库的消费登记（ChannelPaymentNotified）；须在监听结束后执行。 */
    @Delete("DELETE FROM t_consumed_event")
    void clearConsumedEvents();

    /** 仅清理本测试库的已消费聚合版本。 */
    @Delete("DELETE FROM t_consumed_aggregate_version")
    void clearConsumedAggregateVersions();

    /** 本测试库已消费的某类领域事件数。 */
    @Select("SELECT COUNT(*) FROM t_consumed_event WHERE event_type = #{eventType}")
    int consumedEvents(@Param("eventType") String eventType);

    /** 把某任务的下次执行时间拨到当前之前，供显式扫描立即认领（不改其他字段）。 */
    @Update("UPDATE t_compensation_event SET next_execute_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE event_type = #{eventType} AND event_key = #{eventKey}")
    int dueNow(@Param("eventType") String eventType, @Param("eventKey") String eventKey);

    /** 某类型、某事件键的补偿任务执行明细。 */
    @Select("SELECT execute_status AS status, execute_count AS executeCount, next_execute_at > UTC_TIMESTAMP(6) AS deferred, last_error_code AS errorCode FROM t_compensation_event WHERE event_type = #{eventType} AND event_key = #{eventKey}")
    Map<String, Object> compensationEvent(@Param("eventType") String eventType, @Param("eventKey") String eventKey);

    /** 把动作创建时间前移指定秒数，模拟渠道落单宽限或人工处理期限已过。 */
    @Update("UPDATE t_payment_action SET create_time = create_time - INTERVAL #{seconds} SECOND WHERE payment_action_id = #{actionId}")
    int ageAction(@Param("actionId") long actionId, @Param("seconds") long seconds);

    /** 动作的交互类型与截止时间（判断完成后是否保留历史值）。 */
    @Select("SELECT interaction_type AS interactionType, interaction_expires_at AS interactionExpiresAt, action_status AS status FROM t_payment_action WHERE payment_action_id = #{actionId}")
    Map<String, Object> actionInteraction(@Param("actionId") long actionId);

    /** 仅清理本测试库的补偿任务（PAYMENT_SUCCEEDED 与 PAY_RESULT_QUERY）；须在异步执行结束后执行。 */
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










    /** 本测试库全部工具 Token、工具引用、期限、Customer、保存意愿、风控卡属性与 BIN 库卡属性快照，按创建先后排列。 */
    @Select("SELECT payment_instrument_token AS token, instrument_ref AS instrumentRef, expires_at AS expiresAt, customer_id AS customerId, consent_to_save AS consentToSave, card_bin AS cardBin, card_fingerprint AS cardFingerprint, card_brand AS cardBrand, funding_type AS fundingType, card_level AS cardLevel, card_product AS cardProduct, issuer_country AS issuerCountry, issuer_region AS issuerRegion, bin_library_version_no AS binLibraryVersionNo, bin_match_length AS binMatchLength FROM t_payment_instrument_token ORDER BY create_time, payment_instrument_token_id")
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
    @Select("SELECT CONCAT_WS('|', (SELECT GROUP_CONCAT(CONCAT_WS(',', channel_response_code, channel_response_msg)) FROM t_payment_action), (SELECT GROUP_CONCAT(CONCAT_WS(',', payment_instrument_token, instrument_ref)) FROM t_payment_instrument_token), (SELECT GROUP_CONCAT(external_transaction_id) FROM t_channel_transaction_ref))")
    String executionText();











}
