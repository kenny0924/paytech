package com.paytech.payment.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.*;
import com.paytech.payment.channel.core.dal.dataobject.*;

/** 专用测试库的前置数据和清理 SQL；不代替被测生产 Mapper。 */
public interface ChannelTestMapper {
    /** 插入本例的 PaymentChannel 配置。 */
    @Insert("INSERT INTO t_payment_channel (channel_id, channel_code, channel_name, channel_status, create_time, update_time) VALUES (#{channelId}, #{channelCode}, #{channelName}, #{channelStatus.code}, #{createTime,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler}, #{updateTime,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler})")
    void insertPaymentChannel(PaymentChannel value);

    /** 插入本例的 ChannelAccount 配置。 */
    @Insert("INSERT INTO t_channel_account (channel_account_id, channel_id, entity_no, account_name, account_vault_token, account_status, remark, create_time, update_time) VALUES (#{channelAccountId}, #{channelId}, #{entityNo}, #{accountName}, #{accountVaultToken}, #{accountStatus.code}, #{remark}, #{createTime,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler}, #{updateTime,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler})")
    void insertChannelAccount(ChannelAccount value);

    /** 插入本例的 ChannelMid 配置。 */
    @Insert("INSERT INTO t_channel_mid (mid_id, channel_id, channel_account_id, external_mid_code, entity_no, mid_status, activated_at, closed_at, create_time, update_time) VALUES (#{midId}, #{channelId}, #{channelAccountId}, #{externalMidCode}, #{entityNo}, #{midStatus.code}, #{activatedAt,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler}, #{closedAt,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler}, #{createTime,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler}, #{updateTime,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler})")
    void insertChannelMid(ChannelMid value);

    /** 插入本例的 MidCapability 配置。 */
    @Insert("INSERT INTO t_mid_capability (mid_capability_id, mid_id, payment_method_code, scheme_code, country_code, currency, mcc_code, capability_status, create_time, update_time) VALUES (#{midCapabilityId}, #{midId}, #{paymentMethodCode}, #{schemeCode}, #{countryCode}, #{currency}, #{mccCode}, #{capabilityStatus.code}, #{createTime,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler}, #{updateTime,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler})")
    void insertMidCapability(MidCapability value);

    /** 插入本例的 MidCapabilityDetail 配置。 */
    @Insert("INSERT INTO t_mid_capability_detail (mid_capability_detail_id, mid_capability_id, capability_code, value_type, boolean_value, decimal_value, code_value, create_time, update_time) VALUES (#{midCapabilityDetailId}, #{midCapabilityId}, #{capabilityCode}, #{valueType.code}, #{booleanValue}, #{decimalValue}, #{codeValue}, #{createTime,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler}, #{updateTime,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler})")
    void insertMidCapabilityDetail(MidCapabilityDetail value);

    /** 取得基础 Fixture 的唯一条件组，仅用于扩充本例测试数据。 */
    @Select("SELECT mid_capability_id FROM t_mid_capability WHERE mid_id = #{midId}")
    long capabilityId(long midId);

    /** 制造缺失凭据引用，验证不请求 Vault。 */
    @Update("UPDATE t_channel_account SET account_vault_token = NULL")
    void clearAccountToken();

    /** 检查隔离库身份，禁止清理共享库。 */
    @Select("SELECT DATABASE()")
    String databaseName();

    /** 读取渠道五表总行数，验证空基线及查询无新增副作用。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_payment_channel) + (SELECT COUNT(*) FROM t_channel_account) + (SELECT COUNT(*) FROM t_channel_mid) + (SELECT COUNT(*) FROM t_mid_capability) + (SELECT COUNT(*) FROM t_mid_capability_detail)")
    int countRows();

    /** 读取执行链路五表总行数。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_payment_intent) + (SELECT COUNT(*) FROM t_payment_attempt) + (SELECT COUNT(*) FROM t_payment_instrument_token) + (SELECT COUNT(*) FROM t_payment_action) + (SELECT COUNT(*) FROM t_channel_transaction_ref)")
    int countExecutionRows();

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

    /** 仅清理本测试库的支付意图。 */
    @Delete("DELETE FROM t_payment_intent")
    void clearIntents();

    /** 支付尝试状态与当前动作。 */
    @Select("SELECT attempt_status AS status, active_action_id AS activeActionId, channel_id AS channelId, mid_id AS midId FROM t_payment_attempt WHERE payment_attempt_id = #{attemptId}")
    Map<String, Object> attempt(@Param("attemptId") long attemptId);

    /** 支付意图状态与活动/成功尝试。 */
    @Select("SELECT intent_status AS status, active_attempt_id AS activeAttemptId, successful_attempt_id AS successfulAttemptId FROM t_payment_intent WHERE payment_intent_id = #{intentId}")
    Map<String, Object> intent(@Param("intentId") long intentId);

    /** 某尝试的全部动作，按序号。 */
    @Select("SELECT action_type AS type, action_status AS status, interaction_type AS interactionType, channel_response_code AS code, channel_response_msg AS message, risk_result AS riskResult FROM t_payment_action WHERE payment_attempt_id = #{attemptId} ORDER BY action_no")
    List<Map<String, Object>> actions(@Param("attemptId") long attemptId);

    /** 全部渠道交易引用的外部编号与类型。 */
    @Select("SELECT reference_type AS type, external_transaction_id AS externalId, amount, currency FROM t_channel_transaction_ref")
    List<Map<String, Object>> transactionRefs();

    /** 执行链路各表的全部文本列拼接，用于检索不得落库的值。 */
    @Select("SELECT CONCAT_WS('|', (SELECT GROUP_CONCAT(CONCAT_WS(',', channel_response_code, channel_response_msg)) FROM t_payment_action), (SELECT GROUP_CONCAT(CONCAT_WS(',', payment_instrument_token, instrument_ref)) FROM t_payment_instrument_token), (SELECT GROUP_CONCAT(external_transaction_id) FROM t_channel_transaction_ref))")
    String executionText();

    /** 更新当前绑定以模拟真实换绑后的历史执行。 */
    @Update("UPDATE t_channel_mid SET channel_account_id = #{accountId} WHERE mid_id = #{midId}")
    void rebind(@Param("midId") long midId, @Param("accountId") long accountId);

    /** 修改渠道状态以验证每次查询重新读取配置。 */
    @Update("UPDATE t_payment_channel SET channel_status = #{status}")
    void channelStatus(String status);

    /** 修改账号状态以验证原交易矩阵。 */
    @Update("UPDATE t_channel_account SET account_status = #{status}")
    void accountStatus(String status);

    /** 修改 MID 状态以验证原交易矩阵。 */
    @Update("UPDATE t_channel_mid SET mid_status = #{status}")
    void midStatus(String status);

    /** 制造跨 OE 关联损坏，验证应用层拒绝。 */
    @Update("UPDATE t_channel_mid SET entity_no = #{entityNo}")
    void midEntity(String entityNo);

    /** 制造非法能力行，验证类型守卫。 */
    @Update("UPDATE t_mid_capability_detail SET decimal_value = 1")
    void corruptValue();

    /** 仅清理本测试库的能力明细。 */
    @Delete("DELETE FROM t_mid_capability_detail")
    void clearDetails();

    /** 仅清理本测试库的能力主表。 */
    @Delete("DELETE FROM t_mid_capability")
    void clearCapabilities();

    /** 仅清理本测试库的 MID。 */
    @Delete("DELETE FROM t_channel_mid")
    void clearMids();

    /** 仅清理本测试库的账号。 */
    @Delete("DELETE FROM t_channel_account")
    void clearAccounts();

    /** 仅清理本测试库的渠道。 */
    @Delete("DELETE FROM t_payment_channel")
    void clearChannels();
}
