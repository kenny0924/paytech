package com.paytech.channel.connector.test.dal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.*;

import com.paytech.channel.connector.core.dal.dataobject.*;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelConfigChange;

/**
 * mor_channel_connector 的前置数据和清理 SQL；不代替被测生产 Mapper
 * <li>只触碰本库 28 张表：10 §3.1 渠道表、渠道成本四表、变更记录表，以及 2026-10-20 自 Platform 迁入的 10 张渠道协议表。</li>
 */
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

    /** 读取本库其余八张表（仅 Schema 的路由策略、交互、回调、异常，以及补偿任务）总行数，空基线必须为零。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_routing_policy) + (SELECT COUNT(*) FROM t_routing_policy_version) + (SELECT COUNT(*) FROM t_routing_policy_mid) + (SELECT COUNT(*) FROM t_channel_interaction) + (SELECT COUNT(*) FROM t_channel_callback) + (SELECT COUNT(*) FROM t_channel_callback_item) + (SELECT COUNT(*) FROM t_channel_event_exception) + (SELECT COUNT(*) FROM t_compensation_event)")
    int countSchemaOnlyRows();

    /** 读取渠道成本四表总行数，空基线必须为零。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_channel_cost_plan) + (SELECT COUNT(*) FROM t_channel_cost_plan_ver) + (SELECT COUNT(*) FROM t_channel_cost_scope) + (SELECT COUNT(*) FROM t_channel_cost_component)")
    int countChannelCostRows();

    /** 读取方案的状态、乐观锁版本与当前版本号（经指针关联版本表），方案不存在时为空。 */
    @Select("SELECT p.status, p.version, v.cost_ver AS current_cost_ver FROM t_channel_cost_plan p LEFT JOIN t_channel_cost_plan_ver v ON v.id = p.current_ver_id WHERE p.plan_code = #{planCode}")
    Map<String, Object> selectChannelCostPlan(@Param("planCode") String planCode);

    /** 统计方案下的版本数。 */
    @Select("SELECT COUNT(*) FROM t_channel_cost_plan_ver v JOIN t_channel_cost_plan p ON p.id = v.plan_id WHERE p.plan_code = #{planCode}")
    int countChannelCostVersions(@Param("planCode") String planCode);

    /** 改写版本的状态与生效区间，模拟时间推进或制造重叠的历史版本；不改内容摘要。 */
    @Update("UPDATE t_channel_cost_plan_ver v JOIN t_channel_cost_plan p ON p.id = v.plan_id SET v.status = #{status}, v.effective_from = #{effectiveFrom,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler}, v.effective_to = #{effectiveTo,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler} WHERE p.plan_code = #{planCode} AND v.cost_ver = #{costVer}")
    int rewriteVersionWindow(@Param("planCode") String planCode, @Param("costVer") int costVer,
        @Param("status") String status, @Param("effectiveFrom") Instant effectiveFrom,
        @Param("effectiveTo") Instant effectiveTo);

    /** 插入本例的变更记录，供查询测试准备数据。 */
    @Insert("INSERT INTO t_channel_config_change (id, target_type, target_id, change_type, from_status, to_status, operator_id, reason, change_detail, create_time) VALUES (#{id}, #{targetType.code}, #{targetId}, #{changeType.code}, #{fromStatus}, #{toStatus}, #{operatorId}, #{reason}, #{changeDetail}, #{createTime,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler})")
    void insertConfigChange(ChannelConfigChange value);

    /** 读取变更记录表总行数，空基线必须为零。 */
    @Select("SELECT COUNT(*) FROM t_channel_config_change")
    int countConfigChangeRows();

    /** 读取一个对象的全部变更记录（按主键升序），供写入断言。 */
    @Select("SELECT id, target_type, target_id, change_type, from_status, to_status, operator_id, reason, create_time FROM t_channel_config_change WHERE target_type = #{targetType} AND target_id = #{targetId} ORDER BY id")
    List<Map<String, Object>> selectConfigChanges(@Param("targetType") String targetType, @Param("targetId") long targetId);

    /** 读取方案下指定版本号的主键，供变更记录按对象断言。 */
    @Select("SELECT v.id FROM t_channel_cost_plan_ver v JOIN t_channel_cost_plan p ON p.id = v.plan_id WHERE p.plan_code = #{planCode} AND v.cost_ver = #{costVer}")
    Long selectVersionId(@Param("planCode") String planCode, @Param("costVer") int costVer);

    /** 改写方案的更新时间，制造确定的列表排序。 */
    @Update("UPDATE t_channel_cost_plan SET update_time = #{updateTime,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler} WHERE plan_code = #{planCode}")
    int rewritePlanUpdateTime(@Param("planCode") String planCode, @Param("updateTime") Instant updateTime);

    /** 改写 MID 的状态与更新时间，制造确定的筛选与排序。 */
    @Update("UPDATE t_channel_mid SET mid_status = #{status}, update_time = #{updateTime,typeHandler=com.paytech.foundation.mybatis.typehandler.InstantTypeHandler} WHERE mid_id = #{midId}")
    int rewriteMid(@Param("midId") long midId, @Param("status") String status, @Param("updateTime") Instant updateTime);

    /** 停用 MID 的全部能力组，验证启用组计数。 */
    @Update("UPDATE t_mid_capability SET capability_status = 'DISABLED' WHERE mid_id = #{midId}")
    int disableCapabilities(@Param("midId") long midId);

    /** 仅清理本测试库的变更记录。 */
    @Delete("DELETE FROM t_channel_config_change")
    void clearConfigChanges();

    /** 仅清理本测试库的渠道成本组成。 */
    @Delete("DELETE FROM t_channel_cost_component")
    void clearChannelCostComponents();

    /** 仅清理本测试库的渠道成本适用范围。 */
    @Delete("DELETE FROM t_channel_cost_scope")
    void clearChannelCostScopes();

    /** 仅清理本测试库的渠道成本版本。 */
    @Delete("DELETE FROM t_channel_cost_plan_ver")
    void clearChannelCostVersions();

    /** 仅清理本测试库的渠道成本方案。 */
    @Delete("DELETE FROM t_channel_cost_plan")
    void clearChannelCostPlans();

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

    /** 仅清理本测试库的补偿任务（沙箱验证运行 INTEGRATION_VERIFY_RUN 等）。 */
    @Delete("DELETE FROM t_compensation_event")
    void clearCompensationEvents();

    // ---- CC-B 追加：渠道基础配置写入断言 ----

    /** 读取渠道行，不存在为空。 */
    @Select("SELECT channel_code, channel_name, channel_status FROM t_payment_channel WHERE channel_id = #{channelId}")
    Map<String, Object> selectChannel(@Param("channelId") long channelId);

    /** 统计渠道行数。 */
    @Select("SELECT COUNT(*) FROM t_payment_channel")
    int countChannels();

    /** 读取账号行，不存在为空。 */
    @Select("SELECT channel_id, entity_no, account_name, account_vault_token, account_status, remark FROM t_channel_account WHERE channel_account_id = #{accountId}")
    Map<String, Object> selectAccount(@Param("accountId") long accountId);

    /** 读取 MID 行，不存在为空。 */
    @Select("SELECT channel_id, channel_account_id, external_mid_code, entity_no, mid_status, activated_at, closed_at FROM t_channel_mid WHERE mid_id = #{midId}")
    Map<String, Object> selectMid(@Param("midId") long midId);

    /** 读取能力组状态，不存在为空。 */
    @Select("SELECT capability_status FROM t_mid_capability WHERE mid_capability_id = #{capabilityId}")
    String selectCapabilityStatus(@Param("capabilityId") long capabilityId);

    /** 统计能力组行数。 */
    @Select("SELECT COUNT(*) FROM t_mid_capability")
    int countCapabilities();

    /** 读取能力组明细，按能力代码升序。 */
    @Select("SELECT capability_code, value_type, boolean_value, decimal_value, code_value FROM t_mid_capability_detail WHERE mid_capability_id = #{capabilityId} ORDER BY capability_code")
    List<Map<String, Object>> selectDetails(@Param("capabilityId") long capabilityId);

    // ---- 2026-10-20 统一对外渠道出口第一段：渠道协议 10 表 ----

    /** 读取渠道协议 10 张表总行数，空基线必须为零。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_standard_api) + (SELECT COUNT(*) FROM t_api_field) + (SELECT COUNT(*) FROM t_integration) + (SELECT COUNT(*) FROM t_integration_api) + (SELECT COUNT(*) FROM t_integration_script) + (SELECT COUNT(*) FROM t_integration_standard_api) + (SELECT COUNT(*) FROM t_integration_verify_account) + (SELECT COUNT(*) FROM t_integration_verify_case) + (SELECT COUNT(*) FROM t_integration_verify_run) + (SELECT COUNT(*) FROM t_integration_verify_run_step)")
    int countProtocolRows();

    /** 仅清理本测试库的验证运行步骤。 */
    @Delete("DELETE FROM t_integration_verify_run_step")
    void clearVerifyRunSteps();

    /** 仅清理本测试库的验证运行。 */
    @Delete("DELETE FROM t_integration_verify_run")
    void clearVerifyRuns();

    /** 仅清理本测试库的验证用例。 */
    @Delete("DELETE FROM t_integration_verify_case")
    void clearVerifyCases();

    /** 仅清理本测试库的验证账号。 */
    @Delete("DELETE FROM t_integration_verify_account")
    void clearVerifyAccounts();

    /** 仅清理本测试库的标准 API 实现。 */
    @Delete("DELETE FROM t_integration_standard_api")
    void clearImplementations();

    /** 仅清理本测试库的原子 API。 */
    @Delete("DELETE FROM t_integration_api")
    void clearIntegrationApis();

    /** 仅清理本测试库的公共脚本。 */
    @Delete("DELETE FROM t_integration_script")
    void clearIntegrationScripts();

    /** 仅清理本测试库的 API 字段。 */
    @Delete("DELETE FROM t_api_field")
    void clearApiFields();

    /** 仅清理本测试库的接入方。 */
    @Delete("DELETE FROM t_integration")
    void clearIntegrations();

    /** 仅清理本测试库的标准 API。 */
    @Delete("DELETE FROM t_standard_api")
    void clearStandardApis();
}
