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
 * 文件存储实例、存储桶与写入路由的测试数据准备与只读检查。
 *
 * <p>三张表由 PlatformDataCleaner 整表回收。
 */
public interface FileStorageTestMapper {

    /** 插入存储实例。 */
    @Insert("""
        INSERT INTO t_file_storage_instance(instance_id,instance_code,storage_type,endpoint,region,path_style_access,
            trusted_certificate_pem,credential_mode,credential_vault_token,credential_update_time,connect_timeout_ms,
            read_timeout_ms,status,version,create_time,update_time)
        VALUES(#{id},#{code},#{type},#{endpoint},#{region},#{pathStyle},#{certificate},#{mode},#{token},#{now},2000,
            10000,#{status},1,#{now},#{now})
        """)
    int insertInstance(@Param("id") long id, @Param("code") String code, @Param("type") String type,
        @Param("endpoint") String endpoint, @Param("region") String region, @Param("pathStyle") boolean pathStyle,
        @Param("certificate") String certificate, @Param("mode") String mode, @Param("token") String token,
        @Param("status") String status, @Param("now") Instant now);

    /** 插入存储桶；检查结果可空。 */
    @Insert("""
        INSERT INTO t_file_storage_bucket(bucket_id,instance_id,bucket_name,status,last_check_time,last_check_result,
            version,create_time,update_time)
        VALUES(#{id},#{instanceId},#{name},#{status},#{checkTime},#{checkResult},1,#{now},#{now})
        """)
    int insertBucket(@Param("id") long id, @Param("instanceId") long instanceId, @Param("name") String name,
        @Param("status") String status, @Param("checkTime") Instant checkTime, @Param("checkResult") String checkResult,
        @Param("now") Instant now);

    /** 插入写入路由。 */
    @Insert("""
        INSERT INTO t_file_storage_route(route_id,source_system,bucket_id,scan_policy_code,max_upload_seconds,version,
            create_time,update_time)
        VALUES(#{id},#{source},#{bucketId},#{policy},#{seconds},1,#{now},#{now})
        """)
    int insertRoute(@Param("id") long id, @Param("source") String source, @Param("bucketId") long bucketId,
        @Param("policy") String policy, @Param("seconds") int seconds, @Param("now") Instant now);

    /** 直接删除来源系统的路由（含默认路由），构造没有可用路由的场景。 */
    @Delete("DELETE FROM t_file_storage_route WHERE source_system=#{source}")
    int deleteRoute(String source);

    /** 直接改写存储桶状态，构造运营之外的数据场景。 */
    @Update("UPDATE t_file_storage_bucket SET status=#{status} WHERE bucket_id=#{id}")
    int bucketStatus(@Param("id") long id, @Param("status") String status);

    /** 直接改写实例状态，构造运营之外的数据场景。 */
    @Update("UPDATE t_file_storage_instance SET status=#{status} WHERE instance_id=#{id}")
    int instanceStatus(@Param("id") long id, @Param("status") String status);

    /** 直接改写存储桶的最近检查时间，构造过期检查。 */
    @Update("UPDATE t_file_storage_bucket SET last_check_time=#{time} WHERE bucket_id=#{id}")
    int bucketCheckTime(@Param("id") long id, @Param("time") Instant time);

    /** 读取实例全部列。 */
    @Select("SELECT * FROM t_file_storage_instance WHERE instance_code=#{code}")
    Map<String, Object> instance(String code);

    /** 读取实例下的存储桶全部列。 */
    @Select("""
        SELECT b.* FROM t_file_storage_bucket b JOIN t_file_storage_instance i ON i.instance_id=b.instance_id
        WHERE i.instance_code=#{code} AND b.bucket_name=#{bucket}
        """)
    Map<String, Object> bucket(@Param("code") String code, @Param("bucket") String bucket);

    /** 读取路由全部列。 */
    @Select("SELECT * FROM t_file_storage_route WHERE source_system=#{source}")
    Map<String, Object> route(String source);

    /** 三张表全部行的全部文本列，用于检查没有密钥原值。 */
    @Select("""
        SELECT CONCAT_WS('|',instance_code,storage_type,endpoint,region,trusted_certificate_pem,credential_mode,
            credential_vault_token,status) AS text FROM t_file_storage_instance
        UNION ALL SELECT CONCAT_WS('|',bucket_name,status,last_check_result) FROM t_file_storage_bucket
        UNION ALL SELECT CONCAT_WS('|',source_system,scan_policy_code) FROM t_file_storage_route
        """)
    List<String> storageTexts();

    /** 本次运行写入的全部审计属性值。 */
    @Select("SELECT CONCAT_WS('|',attr_key,value_text,value_masked) FROM t_audit_event_attr")
    List<String> auditAttributeTexts();

    /** 按来源系统读取上传。 */
    @Select("""
        SELECT upload_id,source_system,reason_code,uploader_id,tenant_id,storage_provider,
            storage_container_ref,storage_object_ref,display_name,expires_time
        FROM t_file_upload WHERE source_system=#{source} ORDER BY upload_id
        """)
    List<Map<String, Object>> uploadsFrom(String source);
}
