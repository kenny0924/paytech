package com.paytech.risk.decision.api.control.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 上线复核清单快照，随申请冻结
 * <li>系统项（资格、承保、合同与开通项）只由 operations-console-bff 填写，页面提交的同名字段被 BFF 拒绝。</li>
 * <li>满足条件：资格 APPROVED、有有效承保、至少一个 ENABLED 支付方式开通项，且四个人工项全部为 true。</li>
 */
@Getter
@Setter
public class TierChangeChecklistBody {

    /** 系统项：Client 角色资格状态；读取失败为 UNAVAILABLE。 */
    @Size(max = 32)
    @Pattern(regexp = "[A-Z][A-Z_]{0,31}")
    private String qualificationStatus;

    /** 系统项：资格有效期，UTC ISO-8601（如 2027-01-31T00:00:00Z）；无期限为空。 */
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z")
    private String qualificationValidTo;

    /** 系统项：BFF 读到的有效承保版本 ID；无有效承保为空。 */
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String underwritingId;

    /** 系统项：有效承保版本号。 */
    @Min(1)
    @Max(100000)
    private Integer underwritingVersionNo;

    /** 系统项：CLIENT 合同 ID；无合同为空。 */
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String agreementId;

    /** 系统项：合同状态。 */
    @Size(max = 32)
    @Pattern(regexp = "[A-Z][A-Z_]{0,31}")
    private String agreementStatus;

    /** 系统项：执行状态 ENABLED 的支付方式开通项代码，逗号分隔，最长 512 字符。 */
    @Size(max = 512)
    private String enabledPaymentCapabilities;

    /** 系统项：未 ENABLED 的支付方式开通项代码，逗号分隔，最长 512 字符。 */
    @Size(max = 512)
    private String pendingPaymentCapabilities;

    /** 人工项：渠道报备已确认。 */
    private Boolean channelRegistrationConfirmed;

    /** 人工项：技术接入已确认。 */
    private Boolean integrationConfirmed;

    /** 人工项：生产交易验证已完成。 */
    private Boolean productionVerified;

    /** 人工项：未结事项（筛查待复核、证件到期等）已处理。 */
    private Boolean openIssuesCleared;
}
