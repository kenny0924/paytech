package com.paytech.commercial.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.paytech.commercial.api.enums.CommercialTermOwnerType;
import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;

import lombok.Getter;

/**
 * 方案列表一行：方案、方案 BCR 根版本与当前 ACTIVE / 已排期 SCHEDULED 版本的多表组合
 * <li>不携带 DataObject，供 app 入站适配器转换为 ResponseBody。</li>
 */
@Getter
public class CommercialTermPlanSummaryView {
    /** 方案 ID */
    private final long planId;

    /** 方案编号 */
    private final String planCode;

    /** 条款类型 */
    private final String termType;

    /** 方案 Owner 类型 */
    private final CommercialTermOwnerType ownerType;

    /** 模板显示名，仅模板 */
    private final String templateName;

    /** 合同 ID，可空 */
    private final Long agreementId;

    /** OE 编号，可空 */
    private final String entityNo;

    /** Client ID，可空 */
    private final Long clientId;

    /** Local Partner ID，可空 */
    private final Long localPartnerId;

    /** 方案状态 */
    private final CommercialTermPlanStatus status;

    /** 方案 BCR 根当前版本 */
    private final long changeVersion;

    /** 当前 ACTIVE 版本，可空 */
    private final VersionPointer activeVersion;

    /** 已排期 SCHEDULED 版本，可空 */
    private final VersionPointer scheduledVersion;

    /** 方案创建时间 */
    private final Instant createTime;

    /** 方案最近更新时间 */
    private final Instant updateTime;

    /**
     * 由方案、根版本与该方案的 ACTIVE / SCHEDULED 版本构造
     * <li>ACTIVE 由 active_slot 唯一约束保证至多一条；SCHEDULED 正常至多一条，出现多条时取生效起点最早者。</li>
     *
     * @param plan 方案
     * @param changeVersion 方案 BCR 根当前版本
     * @param versions 该方案状态为 ACTIVE 或 SCHEDULED 的版本
     */
    public CommercialTermPlanSummaryView(CommercialTermPlan plan, long changeVersion, List<CommercialTermVersion> versions) {
        this.ownerType = plan.getOwnerType();
        this.templateName = plan.getTemplateName();
        this.planId = plan.getTermPlanId();
        this.planCode = plan.getPlanCode();
        this.termType = plan.getTermType();
        this.agreementId = plan.getAgreementId();
        this.entityNo = plan.getEntityNo();
        this.clientId = plan.getClientId();
        this.localPartnerId = plan.getLocalPartnerId();
        this.status = plan.getStatus();
        this.changeVersion = changeVersion;
        this.activeVersion = earliest(versions, CommercialTermVersionStatus.ACTIVE);
        this.scheduledVersion = earliest(versions, CommercialTermVersionStatus.SCHEDULED);
        this.createTime = plan.getCreateTime();
        this.updateTime = plan.getUpdateTime();
    }

    /** 指定状态中生效起点最早（再按版本号）的一条，没有为 null。 */
    static VersionPointer earliest(List<CommercialTermVersion> versions, CommercialTermVersionStatus status) {
        return versions.stream().filter(version -> version.getStatus() == status)
            .min(Comparator.comparing(CommercialTermVersion::getEffectiveFrom,
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CommercialTermVersion::getVersionNo))
            .map(VersionPointer::new).orElse(null);
    }

    /** 方案下某一版本的定位信息。 */
    @Getter
    public static class VersionPointer {
        /** 版本 ID */
        private final long versionId;

        /** 版本号 */
        private final long versionNo;

        /** 生效起点 */
        private final Instant effectiveFrom;

        /**
         * 由版本行构造
         *
         * @param version 版本
         */
        VersionPointer(CommercialTermVersion version) {
            this.versionId = version.getTermVersionId();
            this.versionNo = version.getVersionNo();
            this.effectiveFrom = version.getEffectiveFrom();
        }
    }
}
