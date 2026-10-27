package com.paytech.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 一次不可变的命中处置（t_screening_resolution）。 */
@Data
public class ScreeningResolution {
    /** 处置 ID。 */
    private long resolutionId;
    /** 命中 ID。 */
    private long hitId;
    /** 处置版本，从 1 起。 */
    private int resolutionVersion;
    /** 处置结论。 */
    private String resolution;
    /** 处置原因码。 */
    private String reasonCode;
    /** 内部备注。 */
    private String note;
    /** 处置人。 */
    private long reviewerId;
    /** 操作方。 */
    private String operatedBy;
    /** 代操作的责任 PayFac。 */
    private Long onBehalfOfPartnerId;
    /** 处置时间。 */
    private Instant resolvedTime;
    /** 创建 UTC 时间。 */
    private Instant createTime;
}
