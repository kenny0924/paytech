package com.paytech.risk.decision.domainservice.convert.dictionary;

import com.paytech.risk.decision.core.dal.dataobject.RuleRejectReason;
import com.paytech.risk.decision.domain.dictionary.DecisionDictionary.Entry;
import com.paytech.risk.decision.domain.enums.DictionaryType;

/** 将拒绝原因持久化事实转换为已有领域合同，不执行查询或业务校验。 */
public final class RejectionReasonConverter {

    /** 转换方法不持有实例状态。 */
    private RejectionReasonConverter() { }

    /** 保留当前内容、状态和审计字段，不向模块外暴露数据库主键。 */
    public static Entry toEntry(RuleRejectReason reason) {
        return new Entry(DictionaryType.REJECTION_REASON, reason.getReasonCode(), reason.getReasonName(),
            reason.getDescription(), reason.getDefaultMessage(), reason.getStatus(), reason.getVersion(),
            reason.getCreatedBy(), reason.getUpdatedBy(), reason.getCreateTime(), reason.getUpdateTime());
    }
}
