package com.paytech.risk.decision.domain.query;

import java.time.Instant;

import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 规则集管理根、决策点及不可变版本共同形成的历史摘要。 */
@Getter
@AllArgsConstructor
public class RuleSetHistoryView {

    /** 规则集整体根 ID。 */
    private final long ruleSetId;

    /** 精确规则版本 ID。 */
    private final long ruleSetVersionId;

    /** 所属决策点编码。 */
    private final String pointCode;

    /** 决策点内的规则集编码。 */
    private final String ruleSetCode;

    /** 该版本保存时的规则集名称。 */
    private final String ruleSetName;

    /** 规则集内递增的业务版本号。 */
    private final int versionNo;

    /** 精确规则版本的生命周期状态。 */
    private final RuleSetVersionStatus status;

    /** 是否为管理根当前配置，不表示正在使用。 */
    private final boolean current;

    /** 创建该版本的 Account ID。 */
    private final long createdBy;

    /** 该版本保存时填写的变更原因。 */
    private final String changeReason;

    /** 该版本保存时的 UTC 微秒时间。 */
    private final Instant createTime;
}
