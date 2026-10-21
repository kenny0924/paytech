package com.paytech.commercial.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeVersion;
import com.paytech.commercial.domain.enums.BusinessChangeReason;

import lombok.Getter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 商业条款版本本轮审批事实：Maker、Checker 与提交时刻，由版本 Child 的不可变快照推导
 * <li>从最新快照向前：客户签署 / 排期 / 激活 / 替代快照跳过；批准快照记 Checker；遇到提交快照取 Maker（快照操作者）与提交时刻后结束；
 * 先遇到驳回、创建或改价快照说明本轮未提交，全部为空。纯函数，不访问数据库。</li>
 */
@Getter
public class CommercialTermReviewFacts {
    /** 没有审批事实。 */
    public static final CommercialTermReviewFacts NONE = new CommercialTermReviewFacts(null, null, null);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Maker Account ID，可空 */
    private final Long makerAccountId;

    /** Checker Account ID，可空 */
    private final Long checkerAccountId;

    /** 提交时刻，可空 */
    private final Instant submittedTime;

    /**
     * 直接给出事实
     *
     * @param makerAccountId Maker
     * @param checkerAccountId Checker
     * @param submittedTime 提交时刻
     */
    CommercialTermReviewFacts(Long makerAccountId, Long checkerAccountId, Instant submittedTime) {
        this.makerAccountId = makerAccountId;
        this.checkerAccountId = checkerAccountId;
        this.submittedTime = submittedTime;
    }

    /**
     * 由一个版本 Child 的全部快照推导本轮审批事实
     *
     * @param snapshots 该版本的快照，顺序任意
     * @return 审批事实；没有有效提交时为 {@link #NONE}
     */
    public static CommercialTermReviewFacts from(List<BusinessChangeVersion> snapshots) {
        Long checker = null;
        List<BusinessChangeVersion> latestFirst = snapshots.stream()
            .sorted(Comparator.comparing(BusinessChangeVersion::getVersionNo).reversed()).toList();
        for (BusinessChangeVersion snapshot : latestFirst) {
            JsonNode envelope = JSON.readTree(snapshot.getSnapshotJson());
            String reason = envelope.path("_meta").path("reasonCode").asString(null);
            if (BusinessChangeReason.TERM_VERSION_SUBMITTED.getCode().equals(reason)) {
                return new CommercialTermReviewFacts(snapshot.getCreateBy(), checker, snapshot.getCreateTime());
            }
            if (BusinessChangeReason.TERM_VERSION_APPROVED.getCode().equals(reason)) {
                checker = snapshot.getCreateBy();
                continue;
            }
            if (!isPostApproval(reason)) {
                return NONE;
            }
        }
        return NONE;
    }

    /** 批准之后的排期与激活（含替代）快照不改变本轮审批事实。 */
    static boolean isPostApproval(String reason) {
        return BusinessChangeReason.TERM_VERSION_ACCEPTED.getCode().equals(reason)
            || BusinessChangeReason.TERM_VERSION_SCHEDULED.getCode().equals(reason)
            || BusinessChangeReason.TERM_VERSION_ACTIVATED.getCode().equals(reason);
    }
}
