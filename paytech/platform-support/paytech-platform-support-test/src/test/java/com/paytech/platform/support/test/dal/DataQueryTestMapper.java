package com.paytech.platform.support.test.dal;

import java.time.Instant;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 数据查询四表（含联合查询键类型）的测试数据准备与只读断言；整表回收由 PlatformDataCleaner 负责。 */
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

    /** 被查模板由 Owner 停用或启用（工作流用例模拟发布后 data-query 模板状态变化）。 */
    @Update("UPDATE t_query_template SET status = #{status}, version = version + 1 WHERE template_id = #{templateId}")
    int changeTemplateStatus(@Param("templateId") long templateId, @Param("status") String status);

    /** 被查模板当前版本 1 的正文由 Owner 改写（工作流用例在未满足后切换为满足）。 */
    @Update("UPDATE t_query_template_version SET body = #{body} WHERE template_id = #{templateId} AND version_no = 1")
    int replaceBody(@Param("templateId") long templateId, @Param("body") String body);

    /** 插入带联合查询配置的版本 1。 */
    @Insert("""
        INSERT INTO t_query_template_version (template_version_id, template_id, version_no, data_source_id, body,
            parameters_json, columns_json, constraints_json, joint_json, restored_from_version_no, change_note,
            created_by, create_time)
        VALUES (#{id}, #{templateId}, 1, #{dataSourceId}, #{body}, #{parameters}, #{columns}, #{constraints}, #{joint},
            NULL, NULL, 1, #{now})""")
    int insertJointVersion(@Param("id") long id, @Param("templateId") long templateId,
        @Param("dataSourceId") long dataSourceId, @Param("body") String body, @Param("parameters") String parameters,
        @Param("columns") String columns, @Param("constraints") String constraints, @Param("joint") String joint,
        @Param("now") Instant now);

    /** 插入键类型。 */
    @Insert("""
        INSERT INTO t_query_key_type (key_type_id, key_type_code, key_type_name, group_name, value_type, default_role,
            entry_allowed, value_pattern, input_hint, sort_no, description, status, version, created_by, updated_by,
            create_time, update_time)
        VALUES (#{id}, #{code}, #{name}, #{group}, #{valueType}, #{defaultRole}, #{entryAllowed}, #{pattern}, #{hint},
            #{sortNo}, NULL, #{status}, 1, 1, 1, #{now}, #{now})""")
    int insertKeyType(@Param("id") long id, @Param("code") String code, @Param("name") String name,
        @Param("group") String group, @Param("valueType") String valueType, @Param("defaultRole") String defaultRole,
        @Param("entryAllowed") boolean entryAllowed, @Param("pattern") String pattern, @Param("hint") String hint,
        @Param("sortNo") int sortNo, @Param("status") String status, @Param("now") Instant now);

    /** 键类型行。 */
    @Select("""
        SELECT key_type_code AS code, key_type_name AS name, group_name AS groupName, value_type AS valueType,
            default_role AS defaultRole, entry_allowed AS entryAllowed, value_pattern AS valuePattern, sort_no AS sortNo,
            status, version, updated_by AS updatedBy
        FROM t_query_key_type WHERE key_type_id = #{id}""")
    Map<String, Object> keyType(@Param("id") long id);

    /** 按编码查键类型主键。 */
    @Select("SELECT key_type_id FROM t_query_key_type WHERE key_type_code = #{code}")
    Long keyTypeId(@Param("code") String code);

    /** 某版本的联合查询配置原文。 */
    @Select("SELECT joint_json FROM t_query_template_version WHERE template_id = #{templateId} AND version_no = #{versionNo}")
    String jointJson(@Param("templateId") long templateId, @Param("versionNo") int versionNo);
}
