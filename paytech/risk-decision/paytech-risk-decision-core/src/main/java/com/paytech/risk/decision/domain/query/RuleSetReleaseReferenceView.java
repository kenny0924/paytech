package com.paytech.risk.decision.domain.query;

import java.time.Instant;

import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发布、决策点版本和规则集版本共同形成的精确引用。 */
@Getter
@AllArgsConstructor
public class RuleSetReleaseReferenceView {

    /**
     * 发布记录 ID。
     */
    private final long releaseId;

    /**
     * 发布业务编号。
     */
    private final String releaseNo;

    /**
     * 发布所在环境。
     */
    private final DecisionEnvironment environmentCode;

    /**
     * 发布生命周期状态。
     */
    private final DecisionReleaseStatus status;

    /**
     * 规则所属决策点编码。
     */
    private final String pointCode;

    /**
     * 发布引用的决策点版本 ID。
     */
    private final long pointVersionId;

    /**
     * 决策点业务版本号。
     */
    private final int pointVersionNo;

    /**
     * 绑定的精确规则集版本 ID。
     */
    private final long ruleSetVersionId;

    /**
     * 规则集业务版本号。
     */
    private final int versionNo;

    /**
     * 激活时间。
     * <li>UTC 微秒时间；尚未激活时为空。</li>
     */
    private final Instant activatedTime;

    /**
     * 被替代时间。
     * <li>UTC 微秒时间；尚未被替代时为空。</li>
     */
    private final Instant supersededTime;
}
