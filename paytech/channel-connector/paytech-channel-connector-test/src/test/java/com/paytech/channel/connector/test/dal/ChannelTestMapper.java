package com.paytech.channel.connector.test.dal;

import org.apache.ibatis.annotations.*;

import com.paytech.channel.connector.core.dal.dataobject.*;

/** mor_channel_connector 的前置数据和清理 SQL；只触碰本库 13 张表（10 §3.1），不代替被测生产 Mapper。 */
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

    /** 读取渠道五表总行数，验证查询无新增副作用。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_payment_channel) + (SELECT COUNT(*) FROM t_channel_account) + (SELECT COUNT(*) FROM t_channel_mid) + (SELECT COUNT(*) FROM t_mid_capability) + (SELECT COUNT(*) FROM t_mid_capability_detail)")
    int countRows();

    /** 读取本库其余八张表（仅 Schema 的路由策略、交互、回调、异常与补偿任务）总行数，空基线必须为零。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_routing_policy) + (SELECT COUNT(*) FROM t_routing_policy_version) + (SELECT COUNT(*) FROM t_routing_policy_mid) + (SELECT COUNT(*) FROM t_channel_interaction) + (SELECT COUNT(*) FROM t_channel_callback) + (SELECT COUNT(*) FROM t_channel_callback_item) + (SELECT COUNT(*) FROM t_channel_event_exception) + (SELECT COUNT(*) FROM t_compensation_event)")
    int countSchemaOnlyRows();

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

    /** 制造账号 OE 与冻结坐标不一致，验证执行前拒绝。 */
    @Update("UPDATE t_channel_account SET entity_no = #{entityNo}")
    void accountEntity(String entityNo);

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

    /** 仅清理本测试库的路由策略 MID 权重（仅 Schema，防御性清理）。 */
    @Delete("DELETE FROM t_routing_policy_mid")
    void clearRoutingPolicyMids();

    /** 仅清理本测试库的路由策略版本（仅 Schema，防御性清理）。 */
    @Delete("DELETE FROM t_routing_policy_version")
    void clearRoutingPolicyVersions();

    /** 仅清理本测试库的路由策略（仅 Schema，防御性清理）。 */
    @Delete("DELETE FROM t_routing_policy")
    void clearRoutingPolicies();

    /** 仅清理本测试库的渠道交互（仅 Schema，防御性清理）。 */
    @Delete("DELETE FROM t_channel_interaction")
    void clearInteractions();

    /** 仅清理本测试库的回调条目（仅 Schema，防御性清理）。 */
    @Delete("DELETE FROM t_channel_callback_item")
    void clearCallbackItems();

    /** 仅清理本测试库的回调（仅 Schema，防御性清理）。 */
    @Delete("DELETE FROM t_channel_callback")
    void clearCallbacks();

    /** 仅清理本测试库的渠道事件异常（仅 Schema，防御性清理）。 */
    @Delete("DELETE FROM t_channel_event_exception")
    void clearEventExceptions();

    /** 仅清理本测试库的补偿任务（本服务尚无 Handler，防御性清理）。 */
    @Delete("DELETE FROM t_compensation_event")
    void clearCompensationEvents();
}
