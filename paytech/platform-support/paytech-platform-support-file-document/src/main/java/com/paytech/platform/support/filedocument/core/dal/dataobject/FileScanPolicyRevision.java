package com.paytech.platform.support.filedocument.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_file_scan_policy 映射对象：检查策略的一个修订；只在 file-document 模块内使用。 */
@Getter
@Setter
public class FileScanPolicyRevision {

    /** 修订主键，上传冻结的 scan_policy_id。 */
    private Long policyId;

    /** 策略代码。 */
    private String policyCode;

    /** 修订号，从 1 开始。 */
    private Integer revisionNo;

    /** 规则结构版本；当前为 1。 */
    private Integer ruleSchemaVersion;

    /** 允许媒体类型规则 JSON：{"mimeTypes":[...]}。 */
    private String allowedMimeRules;

    /** 最大字节数。 */
    private Long maxSizeBytes;

    /** 生效开始时间；为空不限制。 */
    private Instant effectiveFrom;

    /** 生效结束时间；为空不限制。 */
    private Instant effectiveTo;

    /** 修订状态：DRAFT、ACTIVE、RETIRED。 */
    private String status;

    /** 活动修订占位：ACTIVE 为 1，其余为空。 */
    private Integer activeSlot;

    /** 乐观并发版本。 */
    private Integer version;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}
