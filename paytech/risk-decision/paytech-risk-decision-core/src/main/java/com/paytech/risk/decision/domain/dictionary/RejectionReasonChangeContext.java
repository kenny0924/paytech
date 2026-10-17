package com.paytech.risk.decision.domain.dictionary;

import java.time.Instant;

import com.paytech.risk.decision.core.dal.dataobject.RuleRejectReason;
import com.paytech.risk.decision.domain.dictionary.DecisionDictionary.Content;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 字典变更的完整上下文，直接持有当前数据对象及本次拟保存事实。 */
@Getter
@AllArgsConstructor
public class RejectionReasonChangeContext {
    /** 当前已锁定并完成版本检查的字典记录。 */
    private final RuleRejectReason reason;
    /** 本次保存的名称、说明及可选默认文案。 */
    private final Content content;
    /** 本次保存后的字典资格。 */
    private final DictionaryStatus status;
    /** 可信操作者的 Account ID。 */
    private final long actorAccountId;
    /**
     * 本次变更时间。
     * <li>UTC 微秒精度。</li>
     */
    private final Instant changedTime;
}
