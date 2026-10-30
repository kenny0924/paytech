package com.paytech.operations.console.bff.service.processor;

import java.util.List;
import java.util.stream.Stream;

import com.paytech.compliance.api.enums.QualificationEvaluationStatus;
import com.paytech.compliance.api.response.QualificationResponseBody;
import com.paytech.operations.console.bff.domain.enums.LimitTierCheckStatus;
import com.paytech.operations.console.bff.domain.enums.LimitTierSystemCheck;
import com.paytech.operations.console.bff.service.model.risklimittier.LimitTierCheckItemBody;
import com.paytech.operations.console.bff.service.model.risklimittier.LimitTierChecklistBody;
import com.paytech.operations.console.bff.service.model.risklimittier.LimitTierChecklistFactsBody;
import com.paytech.operations.console.bff.service.model.risklimittier.LimitTierUnderwritingBody;
import com.paytech.risk.decision.api.control.request.TierChangeChecklistBody;
import com.paytech.risk.decision.api.control.response.ClientUnderwritingCurrentResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 上线复核清单三个来源的一次读取结果及其纯计算：系统项状态、事实值与转发给 Risk 的清单系统字段
 * <li>来源为 null 表示读取失败或超时（UNAVAILABLE）；合同读取成功但没有 CLIENT 合同为 {@link AgreementReading#NONE}。</li>
 * <li>资格：存在实时判定 ELIGIBLE 的资格即 APPROVED；否则取第一条的实时状态，没有资格为 NOT_REGISTERED。</li>
 */
@Getter
@AllArgsConstructor
public class LimitTierChecklistReading {
    /** 资格当前有效时转发给 Risk 的状态值（Risk 要求 APPROVED）。 */
    static final String QUALIFICATION_APPROVED = "APPROVED";

    /** 读取失败时转发的资格状态。 */
    static final String QUALIFICATION_UNAVAILABLE = "UNAVAILABLE";

    /** commercial 开通项执行状态 ENABLED。 */
    static final String ENABLED = "ENABLED";

    /** 当前承保；不可用为 null。 */
    private final ClientUnderwritingCurrentResponseBody current;

    /** Client 资格；不可用为 null。 */
    private final List<QualificationResponseBody> qualifications;

    /** CLIENT 合同与开通项；不可用为 null，没有合同为 NONE。 */
    private final AgreementReading agreement;

    /**
     * CLIENT 合同与开通项的读取结果
     *
     * @param agreementId 合同 ID；没有合同为 null
     * @param status 合同状态；没有合同为 null
     * @param enabled ENABLED 的开通项代码
     * @param pending 其他执行状态的开通项代码
     */
    public record AgreementReading(String agreementId, String status, List<String> enabled, List<String> pending) {
        /** 没有 CLIENT 合同。 */
        static final AgreementReading NONE = new AgreementReading(null, null, List.of(), List.of());
    }

    /** 三个来源都读取成功。 */
    public boolean available() {
        return current != null && qualifications != null && agreement != null;
    }

    /** 有效承保；当前承保不可用或没有有效版本为 null。 */
    UnderwritingResponseBody effective() {
        return current == null ? null : current.getEffective();
    }

    /** 四个系统项，固定顺序。 */
    public List<LimitTierCheckItemBody> items() {
        return Stream.of(LimitTierSystemCheck.values())
            .map(check -> new LimitTierCheckItemBody(check.getCode(), status(check).getCode(), check.source())).toList();
    }

    /** 一个系统项的核对结果。 */
    LimitTierCheckStatus status(LimitTierSystemCheck check) {
        boolean unavailable = switch (check) {
            case QUALIFICATION -> qualifications == null;
            case UNDERWRITING -> current == null;
            case AGREEMENT, CAPABILITIES -> agreement == null;
        };
        if (unavailable) {
            return LimitTierCheckStatus.UNAVAILABLE;
        }
        boolean ok = switch (check) {
            case QUALIFICATION -> QUALIFICATION_APPROVED.equals(qualificationStatus());
            case UNDERWRITING -> effective() != null;
            case AGREEMENT -> LimitTierChecklistAssembler.ACTIVE.equals(agreement.status());
            case CAPABILITIES -> !agreement.enabled().isEmpty();
        };
        return ok ? LimitTierCheckStatus.OK : LimitTierCheckStatus.NOT_OK;
    }

    /** 资格状态；不可用为 UNAVAILABLE。 */
    String qualificationStatus() {
        if (qualifications == null) {
            return QUALIFICATION_UNAVAILABLE;
        }
        if (LimitTierChecklistAssembler.eligible(qualifications) != null) {
            return QUALIFICATION_APPROVED;
        }
        return qualifications.isEmpty() ? QualificationEvaluationStatus.NOT_REGISTERED.getCode()
            : qualifications.getFirst().getEffectiveStatus();
    }

    /** 当前有效资格的结束时间；没有为 null。 */
    String qualificationValidTo() {
        var eligible = qualifications == null ? null : LimitTierChecklistAssembler.eligible(qualifications);
        return eligible == null ? null : eligible.getValidTo();
    }

    /** 页面事实值。 */
    public LimitTierChecklistFactsBody facts() {
        UnderwritingResponseBody effective = effective();
        return new LimitTierChecklistFactsBody(qualifications == null ? null : qualificationStatus(), qualificationValidTo(),
            effective == null ? null : effective.getUnderwritingId(), effective == null ? null : effective.getVersionNo(),
            effective == null || effective.getInput() == null ? null : effective.getInput().getCrr(),
            agreement == null ? null : agreement.agreementId(), agreement == null ? null : agreement.status(),
            agreement == null ? List.of() : agreement.enabled(), agreement == null ? List.of() : agreement.pending());
    }

    /** 页面清单系统部分。 */
    public LimitTierChecklistBody checklist() {
        return new LimitTierChecklistBody(items(), facts());
    }

    /** 有效承保摘要；没有为 null。 */
    public LimitTierUnderwritingBody underwriting() {
        UnderwritingResponseBody effective = effective();
        if (effective == null) {
            return null;
        }
        var decision = effective.getDecision();
        return new LimitTierUnderwritingBody(effective.getUnderwritingId(), effective.getVersionNo(),
            effective.getInput() == null ? null : effective.getInput().getCrr(),
            decision == null ? null : decision.getObservationDays(), decision == null ? null : decision.getObservationTransactions(),
            decision == null ? null : decision.getSuggestedSettlementDays(), effective.getDecisionSource());
    }

    /**
     * 转发给 Risk 的清单：系统项来自本次读取，人工项由调用方另行填写
     *
     * @return 只含系统项的清单
     */
    public TierChangeChecklistBody systemChecklist() {
        UnderwritingResponseBody effective = effective();
        TierChangeChecklistBody body = new TierChangeChecklistBody();
        body.setQualificationStatus(qualificationStatus());
        body.setQualificationValidTo(qualificationValidTo());
        body.setUnderwritingId(effective == null ? null : effective.getUnderwritingId());
        body.setUnderwritingVersionNo(effective == null ? null : effective.getVersionNo());
        if (agreement != null) {
            body.setAgreementId(agreement.agreementId());
            body.setAgreementStatus(agreement.status());
            body.setEnabledPaymentCapabilities(agreement.enabled().isEmpty() ? null : String.join(",", agreement.enabled()));
            body.setPendingPaymentCapabilities(agreement.pending().isEmpty() ? null : String.join(",", agreement.pending()));
        }
        return body;
    }
}
