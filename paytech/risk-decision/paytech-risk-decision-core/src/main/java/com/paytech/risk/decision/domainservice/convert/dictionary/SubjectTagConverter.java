package com.paytech.risk.decision.domainservice.convert.dictionary;

import com.paytech.risk.decision.core.dal.dataobject.RuleSubjectTag;
import com.paytech.risk.decision.domain.dictionary.DecisionDictionary.Entry;
import com.paytech.risk.decision.domain.enums.DictionaryType;

/** 将拒绝标签持久化事实转换为已有领域合同，不执行查询或业务校验。 */
public final class SubjectTagConverter {

    /** 转换方法不持有实例状态。 */
    private SubjectTagConverter() { }

    /** 保留当前内容、状态和审计字段，不向模块外暴露数据库主键。 */
    public static Entry toEntry(RuleSubjectTag tag) {
        return new Entry(DictionaryType.SUBJECT_TAG, tag.getTagCode(), tag.getTagName(),
            tag.getDescription(), null, tag.getStatus(), tag.getVersion(),
            tag.getCreatedBy(), tag.getUpdatedBy(), tag.getCreateTime(), tag.getUpdateTime());
    }
}
