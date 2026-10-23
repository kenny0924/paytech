package com.paytech.platform.support.filedocument.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.filedocument.core.dal.dataobject.FileScanPolicyRevision;

/** t_file_scan_policy 的 SQL 映射；SQL 位于 FileScanPolicyMapper.xml。 */
public interface FileScanPolicyMapper {

    /** 全部修订，按策略代码升序、修订号倒序。 */
    List<FileScanPolicyRevision> selectAll();

    /** 策略代码的全部修订，按修订号倒序。 */
    List<FileScanPolicyRevision> selectByCode(@Param("policyCode") String policyCode);

    /** 排他锁定策略代码的全部修订，按修订号倒序。 */
    List<FileScanPolicyRevision> lockByCode(@Param("policyCode") String policyCode);

    /** 按修订主键读取。 */
    FileScanPolicyRevision selectById(@Param("policyId") long policyId);

    /** 排他锁定策略代码的 ACTIVE 修订；没有为空。 */
    FileScanPolicyRevision lockActive(@Param("policyCode") String policyCode);

    /** 插入修订。 */
    int insert(@Param("row") FileScanPolicyRevision row);

    /** 按原版本修改 DRAFT 修订的允许类型与最大字节数。 */
    int updateDraft(@Param("row") FileScanPolicyRevision row, @Param("allowedMimeRules") String allowedMimeRules,
        @Param("maxSizeBytes") long maxSizeBytes, @Param("now") Instant now);

    /** 按原版本与原状态切换修订状态与活动占位。 */
    int updateStatus(@Param("row") FileScanPolicyRevision row, @Param("status") String status,
        @Param("activeSlot") Integer activeSlot, @Param("now") Instant now);

    /** 按原版本删除 DRAFT 修订。 */
    int deleteDraft(@Param("row") FileScanPolicyRevision row);
}
