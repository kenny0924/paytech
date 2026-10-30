package com.paytech.commercial.api.agreement.response;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * 合同开通项前置核对结果：核对后的执行状态与逐项结论
 * <li>结论只在本次响应中返回，不落库；原因码为开放字符串，前端按 commercial.enum.CapabilityPrerequisiteReason.&lt;CODE&gt; 取文案，
 * 未知码显示原值。</li>
 */
@Getter
@Setter
public class CapabilityPrerequisiteCheckResponseBody {
    /** 合同 ID */
    private String agreementId;

    /** 核对后的合同 BCR Root 当前版本；本次有状态迁移时已加一 */
    private Long changeVersion;

    /** 合同全部开通项，按开通项 ID 升序 */
    private List<Capability> capabilities;

    /** 一个开通项的核对结果。 */
    @Getter
    @Setter
    public static class Capability {
        /** 开通项 ID */
        private String agreementCapabilityId;

        /** 准确 OE */
        private String entityNo;

        /** 可开通项代码 */
        private String capabilityCode;

        /** 可开通项种类：PAYMENT_METHOD、FEATURE、PAYOUT_RAIL、FX、DISPUTE、PERIODIC_SERVICE */
        private String capabilityKind;

        /** 合同成员资格：ACTIVE、DISABLED */
        private String status;

        /**
         * 核对后的执行状态
         * <li>成员资格 DISABLED 时为空。</li>
         */
        private String executionStatus;

        /**
         * 逐项结论，按 QUALIFICATION、AGREEMENT_TERM、CHANNEL_IDENTITY 顺序
         * <li>成员资格 DISABLED 的开通项为空数组。</li>
         */
        private List<Check> checks;
    }

    /** 一项前置的结论。 */
    @Getter
    @Setter
    public static class Check {
        /** 前置项：QUALIFICATION、AGREEMENT_TERM、CHANNEL_IDENTITY */
        private String code;

        /** 结论：SATISFIED、UNSATISFIED、UNKNOWN、SKIPPED */
        private String result;

        /**
         * 原因码
         * <li>SATISFIED 时一般为空（CHANNEL_IDENTITY 部分币种不就绪时为 CHANNEL_IDENTITY_PARTIAL）；取值如 CLIENT_NOT_QUALIFIED、
         * OPERATING_ENTITY_NOT_QUALIFIED、AGREEMENT_NOT_ACTIVE、
         * CAPABILITY_NOT_EFFECTIVE、TERM_VERSION_NOT_ACTIVE、PRICE_NOT_COVERED、CHECK_UNAVAILABLE、NOT_APPLICABLE。</li>
         * <li>CHANNEL_IDENTITY 另有 AWAITING_PRICE（SKIPPED）、CLIENT_MCC_MISSING、CHANNEL_IDENTITY_PARTIAL（SATISFIED，部分币种不就绪），
         * 以及全部币种不就绪时的原因 MID_NOT_FOUND、PAYMENT_METHOD_NOT_SUPPORTED、MCC_NOT_COVERED、CURRENCY_NOT_SUPPORTED、
         * PAYMENT_ACTION_NOT_SUPPORTED。</li>
         */
        private String reasonCode;

        /**
         * 文案参数
         * <li>键如 subjectType、subjectId、entityNo、termType、agreementStatus、currencies、clientId、paymentMethodCode、mccCode；
         * 没有参数时为空对象。</li>
         */
        private Map<String, String> params;
    }
}
