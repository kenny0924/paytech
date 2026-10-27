package com.paytech.compliance.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 一次名单筛查执行及其命中。 */
@Getter
@Setter
public class ClientReviewScreeningRunResponseBody {

    /** 筛查执行 ID。 */
    private String runId;

    /** 筛查类型：SANCTIONS、PEP 或 ADVERSE_MEDIA。 */
    private String screeningType;

    /** 服务渠道编码（例如 S_SCREENING_MOCK）。 */
    private String providerCode;

    /** STARTED、SUCCEEDED、FAILED 或 TIMED_OUT（结果未知）。 */
    private String status;

    /** 触发来源：CASE（立案）或 RERUN（审核员重跑）。 */
    private String triggerSource;

    /** 开始时间。 */
    private String startedAt;

    /** 完成时间；未完成为空。 */
    private String completedAt;

    /** 命中。 */
    private List<ClientReviewScreeningHitResponseBody> hits;
}
