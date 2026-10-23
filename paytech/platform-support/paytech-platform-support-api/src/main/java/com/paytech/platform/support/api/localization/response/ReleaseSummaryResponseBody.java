package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** 一次发布的摘要。 */
@Getter
@Setter
public class ReleaseSummaryResponseBody {

    /** 应用内发布号，从 1 递增。 */
    private int releaseNo;

    /** 状态：PENDING_CHECK（检查中）、READY（可上线）、FAILED（检查失败）。 */
    private String status;

    /** 是否为应用当前发布。 */
    private boolean current;

    /** 发布包含的 ACTIVE key 数。 */
    private int keyCount;

    /** 发布说明，可为 null。 */
    private String releaseNote;

    /** 检查失败原因；非 FAILED 时为 null。 */
    private String failureReason;

    /** 发布人账号 ID，十进制字符串。 */
    private String createdBy;

    /** 发布时间，UTC ISO-8601 微秒精度。 */
    private String createTime;

    /** 检查通过时间；未通过时为 null。 */
    private String readyTime;

    /**
     * 相对发布时线上发布的变更摘要
     * <li>发布时语言包读取端口未接入且已有线上发布，或为早期发布时为 null。</li>
     */
    private ReleaseChangeSummary changeSummary;
}
