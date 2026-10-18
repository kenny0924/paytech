package com.paytech.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** CCT 运营确认后传入的任务经营分类（t_dd_case_classification），确认后不可修改。 */
@Data
public class CaseClassification {
    /** 分类行 ID。 */
    private long caseClassificationId;

    /** 主案件 ID。 */
    private long caseId;

    /** 行业代码。 */
    private String industryCode;

    /** 四位 MCC。 */
    private String mccCode;

    /** 主要分类为 1，其他为空。 */
    private Integer primarySlot;

    /** 确认人 Operations 账号 ID。 */
    private long confirmedBy;

    /** 确认 UTC 时间。 */
    private Instant confirmedTime;

    /** 创建 UTC 时间。 */
    private Instant createTime;
}
