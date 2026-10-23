package com.paytech.platform.support.filedocument.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.filedocument.domain.enums.FileScanType;

import lombok.Getter;
import lombok.Setter;

/** t_file_scan_requirement 映射对象：检查策略修订的一条检查要求；只在 file-document 模块内使用。 */
@Getter
@Setter
public class FileScanPolicyRequirement {

    /** 检查要求主键。 */
    private Long requirementId;

    /** 所属修订 ID。 */
    private Long policyId;

    /** 修订内序号，从 1 开始。 */
    private Integer requirementNo;

    /** 检查器代码。 */
    private String scannerCode;

    /** 检查类型。 */
    private FileScanType scanType;

    /** 最低检查器版本。 */
    private String minimumScannerVersion;

    /** 视为通过的结果代码 JSON 数组。 */
    private String acceptedResultCodes;

    /** 乐观并发版本。 */
    private Integer version;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}
