package com.paytech.platform.support.test.dal;

import java.time.Instant;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 工作流附件测试的文件前置数据：直接插入已完成的上传与文件对象，来源、原因、上传人与扫描状态由用例指定；
 * 只用于提交校验与重新验证场景，真实上传链路另由 MinIO 用例覆盖。两表由 PlatformDataCleaner 整表回收。
 */
public interface WorkflowFileTestMapper {

    /** 插入已完成的上传记录。 */
    @Insert("""
        INSERT INTO t_file_upload(upload_id,tenant_id,uploader_id,source_system,reason_code,expected_size,expected_mime,
            display_name,storage_provider,storage_container_ref,storage_object_ref,
            scan_policy_id,expires_time,status,version,create_time,update_time)
        VALUES(#{id},NULL,#{uploaderId},#{source},#{reason},#{size},#{mime},#{displayName},'UT_MINIO',
            #{container},#{objectRef},#{policyId},#{now},'COMPLETED',2,#{now},#{now})
        """)
    int insertUpload(@Param("id") long id, @Param("uploaderId") Long uploaderId, @Param("source") String source,
        @Param("reason") String reason, @Param("size") long size, @Param("mime") String mime,
        @Param("displayName") String displayName, @Param("container") String container,
        @Param("objectRef") String objectRef, @Param("policyId") long policyId, @Param("now") Instant now);

    /** 插入对应的文件对象。 */
    @Insert("""
        INSERT INTO t_file_object(file_id,creation_source,upload_id,source_system,reason_code,storage_provider,
            storage_container_ref,storage_object_ref,object_version,content_hash,size_bytes,mime_type,
            storage_encryption_key_ref,classification,scan_policy_id,scan_status,object_status,available_time,version,
            create_time,update_time)
        VALUES(#{id},'UPLOAD',#{uploadId},#{source},#{reason},'UT_MINIO',#{container},#{objectRef},'v1',
            REPEAT('a',64),#{size},#{mime},'AES256','RESTRICTED',#{policyId},#{scanStatus},'ACTIVE',NULL,1,#{now},#{now})
        """)
    int insertFile(@Param("id") long id, @Param("uploadId") long uploadId, @Param("source") String source,
        @Param("reason") String reason, @Param("size") long size, @Param("mime") String mime,
        @Param("container") String container, @Param("objectRef") String objectRef, @Param("policyId") long policyId,
        @Param("scanStatus") String scanStatus, @Param("now") Instant now);

    /** 模拟扫描结束：改写扫描状态。 */
    @Update("UPDATE t_file_object SET scan_status = #{scanStatus}, version = version + 1 WHERE file_id = #{fileId}")
    int updateScanStatus(@Param("fileId") long fileId, @Param("scanStatus") String scanStatus);
}
