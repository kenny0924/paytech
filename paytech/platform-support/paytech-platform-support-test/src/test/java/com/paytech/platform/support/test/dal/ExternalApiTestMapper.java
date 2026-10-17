package com.paytech.platform.support.test.dal;

import java.util.Collection;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 外部 API 配置测试的按本例身份清理和只读检查；不替代生产查询 SQL。 */
public interface ExternalApiTestMapper {

    /** 快速检查六张表已经人工准备，不建表、不初始化业务库。 */
    @Select("""
        SELECT (SELECT COUNT(*) FROM t_standard_api WHERE standard_api_id=-1)
            +(SELECT COUNT(*) FROM t_api_field WHERE api_field_id=-1)
            +(SELECT COUNT(*) FROM t_integration WHERE integration_id=-1)
            +(SELECT COUNT(*) FROM t_integration_api WHERE integration_api_id=-1)
            +(SELECT COUNT(*) FROM t_integration_script WHERE integration_script_id=-1)
            +(SELECT COUNT(*) FROM t_integration_standard_api WHERE integration_standard_api_id=-1)
        """)
    int checkSchema();

    /** 删除本例 API 的全部字段。 */
    @Delete("""
        <script>DELETE FROM t_api_field WHERE api_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach></script>
        """)
    int deleteFields(@Param("ids") Collection<Long> apiIds);

    /** 删除本例接入方下的实现、脚本与原子 API。 */
    @Delete("""
        <script>DELETE FROM t_integration_standard_api WHERE integration_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach></script>
        """)
    int deleteImplementations(@Param("ids") Collection<Long> integrationIds);

    /** 删除本例接入方下的脚本。 */
    @Delete("""
        <script>DELETE FROM t_integration_script WHERE integration_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach></script>
        """)
    int deleteScripts(@Param("ids") Collection<Long> integrationIds);

    /** 删除本例接入方下的原子 API。 */
    @Delete("""
        <script>DELETE FROM t_integration_api WHERE integration_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach></script>
        """)
    int deleteIntegrationApis(@Param("ids") Collection<Long> integrationIds);

    /** 删除本例接入方。 */
    @Delete("""
        <script>DELETE FROM t_integration WHERE integration_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach></script>
        """)
    int deleteIntegrations(@Param("ids") Collection<Long> integrationIds);

    /** 删除本例标准 API。 */
    @Delete("""
        <script>DELETE FROM t_standard_api WHERE standard_api_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach></script>
        """)
    int deleteStandardApis(@Param("ids") Collection<Long> standardApiIds);

    /** 统计本例接入方与标准 API 的残留行，用于确认清理完整。 */
    @Select("""
        <script>SELECT (SELECT COUNT(*) FROM t_integration WHERE integration_id IN
        <foreach collection="integrationIds" item="id" open="(" separator="," close=")">#{id}</foreach>)
        +(SELECT COUNT(*) FROM t_standard_api WHERE standard_api_id IN
        <foreach collection="standardApiIds" item="id" open="(" separator="," close=")">#{id}</foreach>)</script>
        """)
    int remaining(@Param("integrationIds") Collection<Long> integrationIds,
        @Param("standardApiIds") Collection<Long> standardApiIds);
}
