package com.paytech.operations.console.bff.service.model.risklimittier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 页面提交档位变更申请（L4）：只含目标档位、理由、上线说明、观察目标与四个人工确认项
 * <li>复核清单系统项（资格、承保、合同与开通项）由 BFF 在提交时重新读取后附带；页面带系统项按未知属性拒绝。</li>
 */
@Getter
@Setter
public class RequestTierChangePageRequestBody {
    /** 页面看到的当前档位并发版本。 */
    @NotNull(message = "期望档位版本必填")
    @Positive(message = "期望档位版本须为正整数")
    private Integer expectedTierVersion;

    /** 目标档位：VERIFICATION、OBSERVATION、STANDARD 或 RESTRICTED。 */
    @NotBlank(message = "目标档位必填")
    @Pattern(regexp = "VERIFICATION|OBSERVATION|STANDARD|RESTRICTED", message = "目标档位取值无效")
    private String toTier;

    /** 变更理由，1—1000 字符。 */
    @NotBlank(message = "理由必填")
    @Size(max = 1000, message = "理由不超过 1000 个字符")
    private String reason;

    /** 上线复核说明，最长 2000 字符；上线复核必填（由 Risk 校验）。 */
    @Size(max = 2000, message = "上线说明不超过 2000 个字符")
    private String goLiveNote;

    /** 观察期目标天数 1—365；缺省沿用。 */
    @Min(value = 1, message = "目标天数须在 1—365 之间")
    @Max(value = 365, message = "目标天数须在 1—365 之间")
    private Integer observationTargetDays;

    /** 观察期目标放行笔数 1—100000；缺省沿用。 */
    @Min(value = 1, message = "目标笔数须在 1—100000 之间")
    @Max(value = 100000, message = "目标笔数须在 1—100000 之间")
    private Integer observationTargetTransactions;

    /** 四个人工确认项；只在上线复核（VERIFICATION → OBSERVATION）时转发。 */
    @Valid
    private TierChangeManualChecklistPageBody checklist;
}
