package com.paytech.risk.decision.domain.query;

import com.paytech.risk.decision.core.dal.dataobject.RuleSubjectTag;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 字典引用查询上下文，保留同一快照内已经校验的当前记录和查询范围。 */
@Getter
@AllArgsConstructor
public class SubjectTagReferencesContext {
    /** 已读取并完成完整性校验的当前字典。 */
    private final RuleSubjectTag tag;
    /**
     * 已校验并应用默认值的页码。
     * <li>从 1 开始。</li>
     */
    private final int page;
    /** 已校验并应用默认值的每页条数。 */
    private final int size;
    /** 已校验的 ALL、CURRENT、HISTORY 或 DELETED 范围。 */
    private final String scope;

    /** 根据已校验分页坐标计算零基偏移量。 */
    public int offset() {
        return Math.multiplyExact(page - 1, size);
    }
}
