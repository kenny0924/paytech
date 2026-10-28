package com.paytech.commercial.domain;

import java.util.List;
import java.util.Map;

import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.CapabilityPrerequisiteCode;
import com.paytech.commercial.api.enums.CapabilityPrerequisiteResult;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;

import lombok.Getter;

/**
 * 一个开通项的前置判定：逐项结论与目标执行状态（technique.md §9.2、§9.3）
 * <li>由 {@link CapabilityPrerequisiteContext} 在构造时一次算出；fromExecutionStatus 记录判定时的执行状态，capability 在保存成功后由
 * Repository 同步为目标值。</li>
 */
@Getter
public class CapabilityPrerequisiteDecision {
    /** 开通项（本模块 DataObject） */
    private final AgreementCapability capability;

    /** 可开通项种类 */
    private final CapabilityKind capabilityKind;

    /** 判定时的执行状态；成员资格 DISABLED 时为空 */
    private final AgreementCapabilityExecutionStatus fromExecutionStatus;

    /** 目标执行状态；等于 fromExecutionStatus 表示不迁移 */
    private final AgreementCapabilityExecutionStatus targetExecutionStatus;

    /** 逐项结论，按 QUALIFICATION、AGREEMENT_TERM、CHANNEL_IDENTITY 顺序；成员资格 DISABLED 时为空 */
    private final List<Check> checks;

    /**
     * 构造判定
     *
     * @param capability 开通项
     * @param capabilityKind 种类
     * @param targetExecutionStatus 目标执行状态
     * @param checks 逐项结论
     */
    CapabilityPrerequisiteDecision(AgreementCapability capability, CapabilityKind capabilityKind,
        AgreementCapabilityExecutionStatus targetExecutionStatus, List<Check> checks) {
        this.capability = capability;
        this.capabilityKind = capabilityKind;
        this.fromExecutionStatus = capability.getExecutionStatus();
        this.targetExecutionStatus = targetExecutionStatus;
        this.checks = List.copyOf(checks);
    }

    /** 本次需要迁移执行状态。 */
    public boolean changed() {
        return fromExecutionStatus != targetExecutionStatus;
    }

    /** 任一项为 UNKNOWN（查询失败），本次不迁移。 */
    public boolean unknown() {
        return checks.stream().anyMatch(check -> check.getResult() == CapabilityPrerequisiteResult.UNKNOWN);
    }

    /** 一项前置的结论。 */
    @Getter
    public static class Check {
        /** 前置项 */
        private final CapabilityPrerequisiteCode code;

        /** 结论 */
        private final CapabilityPrerequisiteResult result;

        /** 原因码；SATISFIED 时为空，取值见 CapabilityPrerequisiteReasons */
        private final String reasonCode;

        /** 文案参数；没有时为空 Map */
        private final Map<String, String> params;

        /**
         * 构造结论
         *
         * @param code 前置项
         * @param result 结论
         * @param reasonCode 原因码，可空
         * @param params 文案参数
         */
        Check(CapabilityPrerequisiteCode code, CapabilityPrerequisiteResult result, String reasonCode,
            Map<String, String> params) {
            this.code = code;
            this.result = result;
            this.reasonCode = reasonCode;
            this.params = Map.copyOf(params);
        }

        /** 满足或按满足处理（SKIPPED）。 */
        boolean passed() {
            return result == CapabilityPrerequisiteResult.SATISFIED || result == CapabilityPrerequisiteResult.SKIPPED;
        }
    }
}
