package com.paytech.risk.decision.api.control.request;

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
 * 提交限额档位变更申请（L4）
 * <li>允许的迁移：VERIFICATION → OBSERVATION（上线复核，须完整清单与上线说明）、OBSERVATION → STANDARD、
 * OBSERVATION → OBSERVATION（延长观察）、任意 → RESTRICTED、RESTRICTED → 不高于受限前档位。</li>
 * <li>审批级别在申请时按有效承保的 CRR 冻结：LOW 为普通审批，其余（含无承保或未评）为高级审批。</li>
 */
@Getter
@Setter
public class RequestClientTierChangeRequestBody {

    /** 调用方看到的当前档位并发版本；不符返回 409 RISK_LIMIT_TIER_VERSION_CONFLICT。 */
    @NotNull
    @Positive
    private Integer expectedTierVersion;

    /** 目标档位：VERIFICATION、OBSERVATION、STANDARD 或 RESTRICTED。 */
    @NotBlank
    @Pattern(regexp = "VERIFICATION|OBSERVATION|STANDARD|RESTRICTED")
    private String toTier;

    /** 变更理由，1—1000 字符。 */
    @NotBlank
    @Size(max = 1000)
    private String reason;

    /**
     * 上线复核说明，最长 2000 字符
     * <li>VERIFICATION → OBSERVATION 必填，其他迁移可空。</li>
     */
    @Size(max = 2000)
    private String goLiveNote;

    /**
     * 观察期目标天数 1—365
     * <li>只在目标为 OBSERVATION 时使用；缺省取有效承保的观察期天数，无承保为 30；延长观察缺省沿用当前目标。</li>
     */
    @Min(1)
    @Max(365)
    private Integer observationTargetDays;

    /**
     * 观察期目标放行笔数 1—100000
     * <li>只在目标为 OBSERVATION 时使用；缺省取有效承保的观察期笔数，无承保为 100；延长观察缺省沿用当前目标。</li>
     */
    @Min(1)
    @Max(100000)
    private Integer observationTargetTransactions;

    /**
     * 上线复核清单
     * <li>VERIFICATION → OBSERVATION 必填；系统项由 operations-console-bff 从 Owner 重新读取后填入，人工项来自页面。</li>
     */
    @Valid
    private TierChangeChecklistBody checklist;
}
