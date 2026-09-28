package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

/** 活动发布的只读左连接投影；保留缺失引用供完整性校验拒绝损坏关系。 */
@Data
@AllArgsConstructor
public class RuleSetReleaseReference {

    /**
     * 发布身份。
     * <li>必须为正值。</li>
     */
    private final long releaseId;

    /** 发布声明的所属决策点。 */
    private final long releaseDecisionPointId;

    /**
     * 发布当前状态。
     * <li>非 ACTIVE 占槽属于数据错误。</li>
     */
    @NonNull
    private final DecisionReleaseStatus releaseStatus;

    /**
     * 原始活动槽数值。
     * <li>保留数据库 null；仅 ACTIVE 允许占槽且必须为 1。</li>
     */
    private final Integer activeSlot;

    /** 发布引用的事件点版本身份。 */
    private final long pointVersionId;

    /**
     * 被引用版本实际所属决策点。
     * <li>左连接引用缺失时为空。</li>
     */
    private final Long pointDecisionPointId;

    /**
     * 事件点版本状态。
     * <li>必须已冻结；左连接引用缺失时为空。</li>
     */
    private final DecisionPointVersionStatus pointStatus;

    /**
     * 配置绑定身份。
     * <li>不存在时为空，不丢弃左连接行。</li>
     */
    private final Long bindingId;

    /**
     * 被绑定配置版本身份。
     * <li>左连接引用缺失时为空。</li>
     */
    private final Long ruleSetVersionId;

    /**
     * 配置版本实际所属决策点。
     * <li>左连接引用缺失时为空。</li>
     */
    private final Long ruleDecisionPointId;

    /**
     * 配置版本实际所属规则集编码。
     * <li>左连接引用缺失时为空。</li>
     */
    private final String ruleSetCode;

    /**
     * 被使用的配置版本状态。
     * <li>必须已冻结；左连接引用缺失时为空。</li>
     */
    private final RuleSetVersionStatus ruleVersionStatus;

    /**
     * 配置版本所属管理根。
     * <li>迁移或关联损坏时为空。</li>
     */
    private final Long ruleSetId;

    /**
     * 规则集整体管理状态。
     * <li>活动发布不得引用已删除规则集；左连接引用缺失时为空。</li>
     */
    private final RuleSetManagementStatus managementStatus;

    /** 校验全部活动占槽和关联坐标，缺失引用不得被解释为未使用。 */
    public void verifyIntegrity() {
        if (releaseId <= 0 || releaseStatus != DecisionReleaseStatus.ACTIVE
            || !Integer.valueOf(1).equals(activeSlot) || pointVersionId <= 0
            || !Long.valueOf(releaseDecisionPointId).equals(pointDecisionPointId)
            || !Long.valueOf(releaseDecisionPointId).equals(ruleDecisionPointId)
            || pointStatus != DecisionPointVersionStatus.FROZEN || bindingId == null || bindingId <= 0
            || ruleSetVersionId == null || ruleSetVersionId <= 0
            || ruleVersionStatus != RuleSetVersionStatus.FROZEN || ruleSetId == null || ruleSetId <= 0
            || managementStatus != RuleSetManagementStatus.CONFIGURABLE) {
            throw new ErrorException("活动发布的槽位、规则集或事件点绑定损坏");
        }
    }
}
