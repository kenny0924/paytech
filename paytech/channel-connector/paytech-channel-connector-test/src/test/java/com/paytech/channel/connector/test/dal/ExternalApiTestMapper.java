package com.paytech.channel.connector.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 外部 API 配置测试的只读检查：按编码读取对象主键，以及按对象读取 t_channel_config_change 变更记录
 * <li>不替代生产查询 SQL；10 张协议表与变更记录表由 ChannelDataMocker 整表回收。</li>
 */
public interface ExternalApiTestMapper {

    /** 标准 API 主键；不存在为空。 */
    @Select("SELECT standard_api_id FROM t_standard_api WHERE standard_api_code = #{code}")
    Long standardApiId(@Param("code") String code);

    /** 渠道主键；不存在为空。 */
    @Select("SELECT channel_id FROM t_channel WHERE channel_code = #{code}")
    Long channelId(@Param("code") String code);

    /** 渠道下原子 API 主键；不存在为空。 */
    @Select("SELECT a.channel_api_id FROM t_channel_api a JOIN t_channel i ON i.channel_id = a.channel_id WHERE i.channel_code = #{integrationCode} AND a.api_code = #{code}")
    Long channelApiId(@Param("integrationCode") String integrationCode, @Param("code") String code);

    /** 渠道下公共脚本主键；不存在为空。 */
    @Select("SELECT s.channel_script_id FROM t_channel_script s JOIN t_channel i ON i.channel_id = s.channel_id WHERE i.channel_code = #{integrationCode} AND s.script_code = #{code}")
    Long scriptId(@Param("integrationCode") String integrationCode, @Param("code") String code);

    /** 渠道对标准 API 的实现主键；不存在为空。 */
    @Select("SELECT m.channel_standard_api_id FROM t_channel_standard_api m JOIN t_channel i ON i.channel_id = m.channel_id JOIN t_standard_api s ON s.standard_api_id = m.standard_api_id WHERE i.channel_code = #{integrationCode} AND s.standard_api_code = #{standardApiCode}")
    Long implementationId(@Param("integrationCode") String integrationCode,
        @Param("standardApiCode") String standardApiCode);

    /** 渠道下验证账号主键；不存在为空。 */
    @Select("SELECT a.verify_account_id FROM t_channel_verify_account a JOIN t_channel i ON i.channel_id = a.channel_id WHERE i.channel_code = #{integrationCode} AND a.account_code = #{code}")
    Long verifyAccountId(@Param("integrationCode") String integrationCode, @Param("code") String code);

    /** 渠道下验证用例主键；不存在为空。 */
    @Select("SELECT c.verify_case_id FROM t_channel_verify_case c JOIN t_channel i ON i.channel_id = c.channel_id WHERE i.channel_code = #{integrationCode} AND c.case_code = #{code}")
    Long verifyCaseId(@Param("integrationCode") String integrationCode, @Param("code") String code);

    /** 一个对象的全部变更记录（按主键升序）；change_detail 以文本读取。 */
    @Select("SELECT id, target_type, target_id, change_type, from_status, to_status, operator_id, reason, CAST(change_detail AS CHAR) AS change_detail FROM t_channel_config_change WHERE target_type = #{targetType} AND target_id = #{targetId} ORDER BY id")
    List<Map<String, Object>> changes(@Param("targetType") String targetType, @Param("targetId") long targetId);

    /** 某类对象的全部变更记录数，用于确认拒绝的写入没有留痕。 */
    @Select("SELECT COUNT(*) FROM t_channel_config_change WHERE target_type = #{targetType}")
    int countChanges(@Param("targetType") String targetType);
}
