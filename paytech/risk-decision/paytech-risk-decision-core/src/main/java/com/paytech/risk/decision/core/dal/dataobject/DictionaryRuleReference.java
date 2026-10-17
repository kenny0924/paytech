package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.domain.enums.DictionaryType;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 配置关系的数据库投影，非 HTTP DTO。 */
@Getter
@AllArgsConstructor
public class DictionaryRuleReference {
    /** 保存该引用的规则标识。 */
    private final long ruleId;

    /** 稳定规则集根标识。 */
    private final long ruleSetId;

    /** 规则所属决策点代码。 */
    private final String pointCode;

    /** 规则集永久占用的业务编码。 */
    private final String ruleSetCode;

    /** 该版本保存的规则集名称。 */
    private final String ruleSetName;

    /** 引用的不可变规则集版本标识。 */
    private final long ruleSetVersionId;

    /** 规则集业务版本序号。 */
    private final int versionNo;

    /** 版本内规则编码。 */
    private final String ruleCode;

    /** 该版本保存的规则名称。 */
    private final String ruleName;

    /**
     * 标签的持久化动作序号。
     * <li>原因引用为空。</li>
     */
    private final Integer actionSequenceNo;

    /**
     * 标签动作保存的工程主体绑定。
     * <li>原因引用为空。</li>
     */
    private final String targetBindingCode;

    /** 规则集根的配置管理状态。 */
    private final RuleSetManagementStatus ruleSetStatus;

    /**
     * 与根当前指针比较。
     * <li>删除根也可为真。</li>
     */
    private final boolean isCurrentConfiguration;

    /** 校验保存项坐标，标签还必须保留动作序号和目标绑定。 */
    public void verifyIntegrity(DictionaryType type) {
        if (ruleId <= 0 || ruleSetVersionId <= 0 || versionNo <= 0 || ruleSetStatus == null
            || type == DictionaryType.SUBJECT_TAG
                && (actionSequenceNo == null || actionSequenceNo <= 0 || targetBindingCode == null)) {
            throw new ErrorException("字典引用坐标损坏");
        }
    }
}
