package com.paytech.compliance.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.CaseStateChange;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 撤回或过期：一组案件迁移到同一状态及其状态变更；过期时同事务登记结果发布。 */
@Getter
@AllArgsConstructor
public class CaseTransitionContext {
    /** 主案件。 */
    private final DueDiligenceCase mainCase;
    /** 要迁移的案件（主案件在最后）。 */
    private final List<DueDiligenceCase> cases;
    /** 目标状态。 */
    private final String toStatus;
    /** 每个案件一条状态变更。 */
    private final List<CaseStateChange> stateChanges;
    /** 是否登记 ClientReviewDecided 发布（过期发布，撤回不发布）。 */
    private final boolean publishResult;
    /** 迁移时间。 */
    private final Instant now;
}
