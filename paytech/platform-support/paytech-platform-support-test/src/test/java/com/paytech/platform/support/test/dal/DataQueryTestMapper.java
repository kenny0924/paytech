package com.paytech.platform.support.test.dal;

import java.time.Instant;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 数据查询三表的测试数据准备与只读断言；整表回收由 PlatformDataCleaner 负责。 */
public interface DataQueryTestMapper {

    /** 插入数据源行。 */
    @Insert("""
        INSERT INTO t_data_source (data_source_id, data_source_code, data_source_name, engine_type, host, port,
            database_name, username, password_token, connection_params_json, max_pool_size, status, version,
            created_by, updated_by, create_time, update_time)
        VALUES (#{id}, #{code}, #{code}, 'MYSQL', #{host}, #{port}, #{database}, #{username}, #{token}, NULL, 2,
            #{status}, 1, 1, 1, #{now}, #{now})""")
    int insertDataSource(@Param("id") long id, @Param("code") String code, @Param("host") String host,
        @Param("port") int port, @Param("database") String database, @Param("username") String username,
        @Param("token") String token, @Param("status") String status, @Param("now") Instant now);

    /** 插入模板头，当前版本为 1。 */
    @Insert("""
        INSERT INTO t_query_template (template_id, template_code, template_name, description, data_source_id, status,
            current_version_no, version, created_by, updated_by, create_time, update_time)
        VALUES (#{id}, #{code}, #{code}, NULL, #{dataSourceId}, #{status}, 1, 1, 1, 1, #{now}, #{now})""")
    int insertTemplate(@Param("id") long id, @Param("code") String code, @Param("dataSourceId") long dataSourceId,
        @Param("status") String status, @Param("now") Instant now);

    /** 插入版本 1。 */
    @Insert("""
        INSERT INTO t_query_template_version (template_version_id, template_id, version_no, data_source_id, body,
            parameters_json, columns_json, constraints_json, restored_from_version_no, change_note, created_by,
            create_time)
        VALUES (#{id}, #{templateId}, 1, #{dataSourceId}, #{body}, #{parameters}, #{columns}, #{constraints}, NULL,
            NULL, 1, #{now})""")
    int insertVersion(@Param("id") long id, @Param("templateId") long templateId,
        @Param("dataSourceId") long dataSourceId, @Param("body") String body, @Param("parameters") String parameters,
        @Param("columns") String columns, @Param("constraints") String constraints, @Param("now") Instant now);

    /** 数据源行。 */
    @Select("""
        SELECT data_source_code AS code, status, version, username, password_token AS passwordToken,
            max_pool_size AS maxPoolSize, last_check_result AS lastCheckResult, last_check_message AS lastCheckMessage
        FROM t_data_source WHERE data_source_id = #{id}""")
    Map<String, Object> dataSource(@Param("id") long id);

    /** 按编码查数据源主键。 */
    @Select("SELECT data_source_id FROM t_data_source WHERE data_source_code = #{code}")
    Long dataSourceId(@Param("code") String code);

    /** 模板行。 */
    @Select("""
        SELECT template_code AS code, status, version, current_version_no AS currentVersionNo,
            data_source_id AS dataSourceId
        FROM t_query_template WHERE template_id = #{id}""")
    Map<String, Object> template(@Param("id") long id);

    /** 按编码查模板主键。 */
    @Select("SELECT template_id FROM t_query_template WHERE template_code = #{code}")
    Long templateId(@Param("code") String code);

    /** 某模板的版本数。 */
    @Select("SELECT COUNT(*) FROM t_query_template_version WHERE template_id = #{templateId}")
    long versionCount(@Param("templateId") long templateId);

    /** 某版本行。 */
    @Select("""
        SELECT version_no AS versionNo, body, restored_from_version_no AS restoredFromVersionNo,
            change_note AS changeNote
        FROM t_query_template_version WHERE template_id = #{templateId} AND version_no = #{versionNo}""")
    Map<String, Object> version(@Param("templateId") long templateId, @Param("versionNo") int versionNo);
}
