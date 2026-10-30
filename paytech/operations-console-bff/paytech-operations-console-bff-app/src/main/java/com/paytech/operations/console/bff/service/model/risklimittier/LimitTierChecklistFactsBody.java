package com.paytech.operations.console.bff.service.model.risklimittier;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 复核清单系统项的事实值；来源不可用的项为 null 或空集合。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LimitTierChecklistFactsBody {
    /** 资格状态：APPROVED（当前有效）、实时判定的其他状态或 NOT_REGISTERED；不可用为 null。 */
    private String qualificationStatus;

    /** 资格有效期 UTC 时间；无期限或不可用为 null。 */
    private String qualificationValidTo;

    /** 有效承保版本 ID；没有为 null。 */
    private String underwritingId;

    /** 有效承保版本号；没有为 null。 */
    private Integer underwritingVersionNo;

    /** 有效承保快照的 CRR；没有为 null。 */
    private String crr;

    /** CLIENT 合同 ID；没有为 null。 */
    private String agreementId;

    /** 合同状态；没有为 null。 */
    private String agreementStatus;

    /** 执行状态 ENABLED 的开通项代码，按代码排序。 */
    private List<String> enabledPaymentCapabilities;

    /** 其他执行状态的开通项代码，按代码排序。 */
    private List<String> pendingPaymentCapabilities;
}
