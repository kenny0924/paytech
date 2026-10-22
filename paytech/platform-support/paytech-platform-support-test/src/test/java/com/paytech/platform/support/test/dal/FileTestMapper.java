package com.paytech.platform.support.test.dal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 入驻文件的测试数据准备与只读检查。
 *
 * <p>上传、文件、Owner 与结果由 PlatformDataCleaner 整表回收；扫描策略与要求可能含本机开发配置，只按本例标识删除。
 */
public interface FileTestMapper {

    /** 插入一个 ACTIVE 扫描策略修订。 */
    @Insert("""
        INSERT INTO t_file_scan_policy(policy_id,policy_code,revision_no,rule_schema_version,allowed_mime_rules,
            max_size_bytes,effective_from,effective_to,status,active_slot,version,create_time,update_time)
        VALUES(#{id},#{code},1,1,#{mimeRules},#{maxSize},#{now},NULL,'ACTIVE',1,1,#{now},#{now})
        """)
    int insertPolicy(@Param("id") long id, @Param("code") String code, @Param("mimeRules") String mimeRules,
        @Param("maxSize") long maxSize, @Param("now") Instant now);

    /** 插入策略的一项扫描要求。 */
    @Insert("""
        INSERT INTO t_file_scan_requirement(requirement_id,policy_id,requirement_no,scanner_code,scan_type,
            minimum_scanner_version,accepted_result_codes,version,create_time,update_time)
        VALUES(#{id},#{policyId},#{no},#{scannerCode},#{type},#{minimumVersion},#{acceptedCodes},1,#{now},#{now})
        """)
    int insertRequirement(@Param("id") long id, @Param("policyId") long policyId, @Param("no") int no,
        @Param("scannerCode") String scannerCode, @Param("type") String type,
        @Param("minimumVersion") String minimumVersion, @Param("acceptedCodes") String acceptedCodes,
        @Param("now") Instant now);

    /** 插入已完成的上传目标。 */
    @Insert("""
        INSERT INTO t_file_upload(upload_id,tenant_id,uploader_id,source_system,reason_code,expected_size,expected_mime,
            display_name,storage_provider,storage_container_ref,storage_object_ref,
            scan_policy_id,expires_time,status,version,create_time,update_time)
        VALUES(#{id},#{tenantId},#{uploaderId},'platform-support','CLIENT_ONBOARDING_MATERIAL',#{size},#{mime},
            #{displayName},#{provider},#{container},#{objectRef},#{policyId},#{expiresAt},'COMPLETED',2,#{now},#{now})
        """)
    int insertUpload(@Param("id") long id, @Param("tenantId") long tenantId, @Param("uploaderId") long uploaderId,
        @Param("size") long size, @Param("mime") String mime,
        @Param("displayName") String displayName, @Param("provider") String provider,
        @Param("container") String container, @Param("objectRef") String objectRef,
        @Param("policyId") long policyId, @Param("expiresAt") Instant expiresAt, @Param("now") Instant now);

    /** 插入尚未完成、仍在授权期内的上传目标。 */
    @Insert("""
        INSERT INTO t_file_upload(upload_id,tenant_id,uploader_id,source_system,reason_code,expected_size,expected_mime,
            display_name,storage_provider,storage_container_ref,storage_object_ref,
            scan_policy_id,expires_time,status,version,create_time,update_time)
        VALUES(#{id},#{tenantId},#{uploaderId},'platform-support','CLIENT_ONBOARDING_MATERIAL',#{size},#{mime},
            #{displayName},#{provider},#{container},#{objectRef},#{policyId},#{expiresAt},'CREATED',1,#{now},#{now})
        """)
    int insertOpenUpload(@Param("id") long id, @Param("tenantId") long tenantId, @Param("uploaderId") long uploaderId,
        @Param("size") long size, @Param("mime") String mime,
        @Param("displayName") String displayName, @Param("provider") String provider,
        @Param("container") String container, @Param("objectRef") String objectRef,
        @Param("policyId") long policyId, @Param("expiresAt") Instant expiresAt, @Param("now") Instant now);

    /** 读取账号发起的全部上传，按上传 ID 排序。 */
    @Select("""
        SELECT upload_id,tenant_id,uploader_id,source_system,reason_code,expected_size,expected_mime,
            display_name,storage_provider,storage_container_ref,storage_object_ref,scan_policy_id,expires_time,status,
            version
        FROM t_file_upload WHERE uploader_id=#{uploaderId} ORDER BY upload_id
        """)
    List<Map<String, Object>> uploadsOf(long uploaderId);

    /** 读取上传当前状态与版本。 */
    @Select("SELECT status,version FROM t_file_upload WHERE upload_id=#{uploadId}")
    Map<String, Object> upload(long uploadId);

    /** 读取任一来源上传的完整行。 */
    @Select("""
        SELECT upload_id,tenant_id,uploader_id,source_system,reason_code,
            display_name,storage_provider,storage_container_ref,storage_object_ref,scan_policy_id,expires_time,status,version
        FROM t_file_upload WHERE upload_id=#{uploadId}
        """)
    Map<String, Object> uploadRow(long uploadId);

    /** 读取上传登记的文件对象。 */
    @Select("""
        SELECT file_id,source_system,reason_code,storage_provider,storage_container_ref,object_version,content_hash,
            size_bytes,mime_type,storage_encryption_key_ref,scan_policy_id,scan_status,object_status,create_time
        FROM t_file_object WHERE upload_id=#{uploadId} ORDER BY file_id
        """)
    List<Map<String, Object>> filesOfUpload(long uploadId);

    /** 统计指定类型尚未结束（INIT / PROCESSING）的补偿任务。 */
    @Select("SELECT COUNT(*) FROM t_compensation_event WHERE event_type=#{eventType} AND execute_status IN ('INIT','PROCESSING')")
    long activeTasks(String eventType);

    /** 读取指定类型的全部补偿任务业务键，按任务 ID 排序。 */
    @Select("SELECT event_key FROM t_compensation_event WHERE event_type=#{eventType} ORDER BY id")
    List<String> taskKeys(String eventType);

    /** 删除指定类型的全部补偿任务。 */
    @Delete("DELETE FROM t_compensation_event WHERE event_type=#{eventType}")
    int deleteTasks(String eventType);

    /** 插入由上传完成登记的文件对象。 */
    @Insert("""
        INSERT INTO t_file_object(file_id,creation_source,upload_id,source_system,reason_code,storage_provider,
            storage_container_ref,storage_object_ref,object_version,content_hash,size_bytes,mime_type,
            storage_encryption_key_ref,classification,scan_policy_id,scan_status,object_status,available_time,version,
            create_time,update_time)
        VALUES(#{id},'UPLOAD',#{uploadId},'platform-support','CLIENT_ONBOARDING_MATERIAL',#{provider},#{container},#{objectRef},#{version},#{hash},#{size},#{mime},
            #{encryption},'RESTRICTED',#{policyId},#{scanStatus},#{objectStatus},#{availableTime},1,#{now},#{now})
        """)
    int insertFile(@Param("id") long id, @Param("uploadId") long uploadId, @Param("provider") String provider,
        @Param("container") String container, @Param("objectRef") String objectRef, @Param("version") String version,
        @Param("hash") String hash, @Param("size") long size, @Param("mime") String mime,
        @Param("encryption") String encryption, @Param("policyId") long policyId,
        @Param("scanStatus") String scanStatus, @Param("objectStatus") String objectStatus,
        @Param("availableTime") Instant availableTime, @Param("now") Instant now);

    /** 统计指定策略代码的修订数，确认测试策略没有残留。 */
    @Select("SELECT COUNT(*) FROM t_file_scan_policy WHERE policy_code=#{code}")
    long countPolicies(String code);

    /** 删除本例插入的扫描策略。 */
    @Delete("DELETE FROM t_file_scan_policy WHERE policy_id=#{id}")
    int deletePolicy(long id);

    /** 删除本例插入策略下的全部扫描要求。 */
    @Delete("DELETE FROM t_file_scan_requirement WHERE policy_id=#{policyId}")
    int deleteRequirements(long policyId);

    /** 读取文件当前扫描与对象状态。 */
    @Select("SELECT scan_status,object_status,available_time,version FROM t_file_object WHERE file_id=#{fileId}")
    Map<String, Object> file(long fileId);

    /** 读取文件的全部扫描结果，按要求与尝试排序。 */
    @Select("""
        SELECT scan_requirement_id,scan_policy_id,attempt_no,scanner_runtime_version,result_code,scanned_time
        FROM t_file_scan_result WHERE file_id=#{fileId} ORDER BY scan_requirement_id,attempt_no
        """)
    List<Map<String, Object>> results(long fileId);
}
