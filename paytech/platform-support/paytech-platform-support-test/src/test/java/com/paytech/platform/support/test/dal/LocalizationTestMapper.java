package com.paytech.platform.support.test.dal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 多语言八表与导入文件的测试数据准备和只读断言；整表回收由 PlatformDataCleaner 负责。
 * <li>上线补偿任务 I18N_RELEASE_ACTIVATE 不在整表回收范围，由 LocalizationMocker 按类型回收。</li>
 */
public interface LocalizationTestMapper {

    /** 插入语言。 */
    @Insert("""
        INSERT INTO t_i18n_locale (locale_id, locale_code, native_name, english_name, text_direction, status, sort_order,
            version, created_by, updated_by, create_time, update_time)
        VALUES (#{id}, #{code}, #{nativeName}, #{englishName}, 'LTR', #{status}, #{sortOrder}, 1, 1, 1, #{now}, #{now})""")
    int insertLocale(@Param("id") long id, @Param("code") String code, @Param("nativeName") String nativeName,
        @Param("englishName") String englishName, @Param("status") String status, @Param("sortOrder") int sortOrder,
        @Param("now") Instant now);

    /** 插入应用，修订号为 0、版本为 1、尚无发布。 */
    @Insert("""
        INSERT INTO t_i18n_application (application_id, application_code, application_name, default_locale,
            required_locales, coverage_threshold_percent, current_release_id, key_set_revision, content_revision,
            status, version, created_by, updated_by, create_time, update_time)
        VALUES (#{id}, #{code}, #{code}, #{defaultLocale}, #{requiredLocales}, #{threshold}, NULL, 0, 0, 'ENABLED', 1, 1,
            1, #{now}, #{now})""")
    int insertApplication(@Param("id") long id, @Param("code") String code, @Param("defaultLocale") String defaultLocale,
        @Param("requiredLocales") String requiredLocales, @Param("threshold") int threshold, @Param("now") Instant now);

    /** 插入命名空间。 */
    @Insert("""
        INSERT INTO t_i18n_namespace (namespace_id, application_id, namespace_code, namespace_name, description,
            sort_order, version, created_by, updated_by, create_time, update_time)
        VALUES (#{id}, #{applicationId}, #{code}, #{code}, NULL, #{sortOrder}, 1, 1, 1, #{now}, #{now})""")
    int insertNamespace(@Param("id") long id, @Param("applicationId") long applicationId, @Param("code") String code,
        @Param("sortOrder") int sortOrder, @Param("now") Instant now);

    /** 插入 key。 */
    @Insert("""
        INSERT INTO t_i18n_key (key_id, application_id, namespace_id, key_code, source_text, description,
            source_revision, status, version, created_by, updated_by, create_time, update_time)
        VALUES (#{id}, #{applicationId}, #{namespaceId}, #{code}, #{text}, NULL, #{sourceRevision}, #{status}, 1, 1, 1,
            #{now}, #{now})""")
    int insertKey(@Param("id") long id, @Param("applicationId") long applicationId,
        @Param("namespaceId") long namespaceId, @Param("code") String code, @Param("text") String text,
        @Param("sourceRevision") int sourceRevision, @Param("status") String status, @Param("now") Instant now);

    /** 插入翻译，版本为 1。 */
    @Insert("""
        INSERT INTO t_i18n_translation (translation_id, key_id, locale_code, text, based_on_source_revision, version,
            created_by, updated_by, create_time, update_time)
        VALUES (#{id}, #{keyId}, #{locale}, #{text}, #{basedOn}, 1, 1, 1, #{now}, #{now})""")
    int insertTranslation(@Param("id") long id, @Param("keyId") long keyId, @Param("locale") String locale,
        @Param("text") String text, @Param("basedOn") int basedOn, @Param("now") Instant now);

    /** 插入已完成的导入文件上传记录（operations-console-bff 来源）。 */
    @Insert("""
        INSERT INTO t_file_upload(upload_id,tenant_id,uploader_id,source_system,reason_code,expected_size,expected_mime,
            display_name,storage_provider,storage_container_ref,storage_object_ref,
            scan_policy_id,expires_time,status,version,create_time,update_time)
        VALUES(#{id},NULL,1,'operations-console-bff',#{reason},#{size},'application/json','i18n.json','UT_MINIO',
            #{container},#{objectRef},#{policyId},#{now},'COMPLETED',2,#{now},#{now})""")
    int insertUpload(@Param("id") long id, @Param("reason") String reason, @Param("size") long size,
        @Param("container") String container, @Param("objectRef") String objectRef, @Param("policyId") long policyId,
        @Param("now") Instant now);

    /** 插入导入文件对象：真实版本号与内容摘要，扫描状态由用例指定。 */
    @Insert("""
        INSERT INTO t_file_object(file_id,creation_source,upload_id,source_system,reason_code,storage_provider,
            storage_container_ref,storage_object_ref,object_version,content_hash,size_bytes,mime_type,
            storage_encryption_key_ref,classification,scan_policy_id,scan_status,object_status,available_time,version,
            create_time,update_time)
        VALUES(#{id},'UPLOAD',#{uploadId},'operations-console-bff',#{reason},'UT_MINIO',#{container},#{objectRef},
            #{objectVersion},#{contentHash},#{size},'application/json','AES256','RESTRICTED',#{policyId},#{scanStatus},
            'ACTIVE',NULL,1,#{now},#{now})""")
    int insertFile(@Param("id") long id, @Param("uploadId") long uploadId, @Param("reason") String reason,
        @Param("container") String container, @Param("objectRef") String objectRef,
        @Param("objectVersion") String objectVersion, @Param("contentHash") String contentHash,
        @Param("size") long size, @Param("policyId") long policyId, @Param("scanStatus") String scanStatus,
        @Param("now") Instant now);

    /** 应用的修订号、版本与当前发布。 */
    @Select("""
        SELECT application_id AS applicationId, key_set_revision AS keySetRevision, content_revision AS contentRevision,
            version, current_release_id AS currentReleaseId, default_locale AS defaultLocale
        FROM t_i18n_application WHERE application_code = #{code}""")
    Map<String, Object> application(@Param("code") String code);

    /** 语言行。 */
    @Select("SELECT status, version, native_name AS nativeName FROM t_i18n_locale WHERE locale_code = #{code}")
    Map<String, Object> locale(@Param("code") String code);

    /** 应用的全部 key，按 key 排序。 */
    @Select("""
        SELECT key_code AS keyCode, source_text AS sourceText, description, source_revision AS sourceRevision, status,
            version
        FROM t_i18n_key WHERE application_id = #{applicationId} ORDER BY key_code""")
    List<Map<String, Object>> keys(@Param("applicationId") long applicationId);

    /** 某 key 在某语言下的翻译。 */
    @Select("""
        SELECT t.text, t.version, t.based_on_source_revision AS basedOnSourceRevision
        FROM t_i18n_translation t JOIN t_i18n_key k ON k.key_id = t.key_id
        WHERE k.application_id = #{applicationId} AND k.key_code = #{keyCode} AND t.locale_code = #{locale}""")
    Map<String, Object> translation(@Param("applicationId") long applicationId, @Param("keyCode") String keyCode,
        @Param("locale") String locale);

    /** 命名空间数。 */
    @Select("SELECT COUNT(*) FROM t_i18n_namespace WHERE application_id = #{applicationId} AND namespace_code = #{code}")
    long namespaceCount(@Param("applicationId") long applicationId, @Param("code") String code);

    /** 应用的发布数。 */
    @Select("SELECT COUNT(*) FROM t_i18n_release WHERE application_id = #{applicationId}")
    long releaseCount(@Param("applicationId") long applicationId);

    /** 应用的全部发布，按发布号排序。 */
    @Select("""
        SELECT release_id AS releaseId, release_no AS releaseNo, status, failure_reason AS failureReason,
            ready_time AS readyTime, based_on_content_revision AS basedOnContentRevision,
            change_count AS changeCount
        FROM t_i18n_release WHERE application_id = #{applicationId} ORDER BY release_no""")
    List<Map<String, Object>> releases(@Param("applicationId") long applicationId);

    /** 发布的语言包行，按语言、命名空间排序。 */
    @Select("""
        SELECT locale_code AS locale, namespace_code AS namespaceCode, file_address AS fileAddress,
            content_hash AS contentHash, byte_size AS byteSize
        FROM t_i18n_release_bundle WHERE release_id = #{releaseId} ORDER BY locale_code, namespace_code""")
    List<Map<String, Object>> bundles(@Param("releaseId") long releaseId);

    /** 应用的发布文案变化，按发布号、语言、key 排序。 */
    @Select("""
        SELECT release_no AS releaseNo, locale_code AS locale, key_code AS keyCode, change_type AS changeType, text
        FROM t_i18n_release_change WHERE application_id = #{applicationId}
        ORDER BY release_no, locale_code, key_code""")
    List<Map<String, Object>> releaseChanges(@Param("applicationId") long applicationId);

    /** 把发布的变化条数置空，构造“历史发布尚未补录变更明细”的前置状态。 */
    @Update("UPDATE t_i18n_release SET change_count = NULL WHERE release_id = #{releaseId}")
    int clearChangeCount(@Param("releaseId") long releaseId);

    /** 某原因码的全部上传与对应文件对象（未完成的上传没有文件），按上传 ID 排序。 */
    @Select("""
        SELECT u.upload_id AS uploadId, u.source_system AS sourceSystem, u.access_level AS accessLevel,
            u.expected_mime AS mimeType, u.storage_container_ref AS container, u.storage_object_ref AS objectRef,
            u.status AS uploadStatus, u.uploader_id AS uploaderId, f.file_id AS fileId, f.scan_status AS scanStatus,
            f.content_hash AS contentHash
        FROM t_file_upload u LEFT JOIN t_file_object f ON f.upload_id = u.upload_id
        WHERE u.reason_code = #{reason} ORDER BY u.upload_id""")
    List<Map<String, Object>> uploads(@Param("reason") String reason);

    /** 上线补偿任务，按任务 ID 排序；成功的任务已删除。 */
    @Select("""
        SELECT event_key AS eventKey, execute_status AS executeStatus, execute_count AS executeCount,
            last_error_code AS lastErrorCode
        FROM t_compensation_event WHERE event_type = 'I18N_RELEASE_ACTIVATE' ORDER BY id""")
    List<Map<String, Object>> activationTasks();

    /** 尚在执行的上线补偿任务数。 */
    @Select("SELECT COUNT(*) FROM t_compensation_event WHERE event_type = 'I18N_RELEASE_ACTIVATE' AND execute_status = 'PROCESSING'")
    long processingActivations();

    /** 使等待中的上线补偿任务立即到期；执行仍经过正式 Scanner 与 Dispatcher。 */
    @Update("""
        UPDATE t_compensation_event SET next_execute_at = UTC_TIMESTAMP(6)
        WHERE event_type = 'I18N_RELEASE_ACTIVATE' AND execute_status IN ('INIT', 'ERROR')""")
    int activationDue();

    /** 删除全部上线补偿任务。 */
    @Delete("DELETE FROM t_compensation_event WHERE event_type = 'I18N_RELEASE_ACTIVATE'")
    int deleteActivationTasks();

    /** 模拟锁租约失效后的并发写入：绕过应用锁把文案修订号加一。 */
    @Update("UPDATE t_i18n_application SET content_revision = content_revision + 1 WHERE application_code = #{code}")
    int bumpContentRevision(@Param("code") String code);

    /** 把已上线的发布恢复为等待检查，构造“检查尚未结束时已手动切到更新发布”的前置状态。 */
    @Update("""
        UPDATE t_i18n_release SET status = 'PENDING_CHECK', ready_time = NULL
        WHERE release_id = #{releaseId} AND status = 'READY'""")
    int markPendingCheck(@Param("releaseId") long releaseId);

    /** 多语言发布与切换的审计事件，按发生时间排序。 */
    @Select("""
        SELECT action_code AS action, resource_type AS resourceType, resource_id AS resourceId, result
        FROM t_audit_event WHERE action_code IN ('I18N_RELEASE_PUBLISH', 'I18N_RELEASE_ACTIVATE')
        ORDER BY occurred_time, audit_event_id""")
    List<Map<String, Object>> releaseAudits();
}
