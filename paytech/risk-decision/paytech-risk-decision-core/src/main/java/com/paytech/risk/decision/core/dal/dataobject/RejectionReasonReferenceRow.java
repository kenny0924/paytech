package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.domain.enums.FlowStatus;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 原因引用统一分页的数据库投影；两种来源的专属列互斥。 */
@Getter
@AllArgsConstructor
public class RejectionReasonReferenceRow {
    /** 保存引用的规则或节点行身份。 */
    private final long sourceRowId;

    /** 来源只允许 RULE_SET 或 FLOW_END。 */
    private final String sourceType;

    /** 共同的决策点编码。 */
    private final String pointCode;

    /** 仅规则来源的管理根身份。 */
    private final Long ruleSetId;

    /** 仅规则来源的根编码。 */
    private final String ruleSetCode;

    /** 准确规则版本名称。 */
    private final String ruleSetName;

    /** 准确规则版本身份。 */
    private final Long ruleSetVersionId;

    /** 仅规则来源的业务版本。 */
    private final Integer versionNo;

    /** 版本内规则编码。 */
    private final String ruleCode;

    /** 版本内规则名称。 */
    private final String ruleName;

    /** 仅规则来源的整体管理状态。 */
    private final RuleSetManagementStatus ruleSetStatus;

    /** 保存版本是否为当前根指针。 */
    private final boolean isCurrentConfiguration;

    /** 仅流程来源的管理根身份。 */
    private final Long flowId;

    /** 仅流程来源的根编码。 */
    private final String flowCode;

    /** 准确流程版本名称。 */
    private final String flowName;

    /** 准确流程配置版本。 */
    private final Long flowVersionId;

    /**
     * 明确流程业务版本。
     * <li>不复用规则版本字段。</li>
     */
    private final Integer flowVersionNo;

    /** 保存时的稳定节点键。 */
    private final String nodeKey;

    /** 保存时节点名称。 */
    private final String nodeName;

    /** 流程整体管理状态。 */
    private final FlowStatus flowStatus;

    /** 规则来源必须具备精确规则版本，并且不携带流程专属字段。 */
    public void verifyRuleSource() {
        if (!"RULE_SET".equals(sourceType) || ruleSetId == null || ruleSetVersionId == null || versionNo == null
            || flowId != null || flowCode != null || flowName != null || flowVersionId != null
            || flowVersionNo != null || nodeKey != null || nodeName != null || flowStatus != null) {
            throw new ErrorException("原因规则来源字段矩阵损坏");
        }
    }

    /** 流程来源必须具备精确节点版本，并且不携带规则专属字段。 */
    public void verifyFlowSource() {
        if (!"FLOW_END".equals(sourceType) || sourceRowId <= 0 || flowId == null || flowId <= 0
            || flowVersionId == null || flowVersionId <= 0 || flowVersionNo == null || flowVersionNo < 1
            || flowCode == null || flowName == null || flowStatus == null || pointCode == null
            || nodeKey == null || nodeName == null || ruleSetId != null || ruleSetVersionId != null
            || ruleSetCode != null || ruleSetName != null || versionNo != null || ruleCode != null
            || ruleName != null || ruleSetStatus != null) {
            throw new ErrorException("原因流程来源字段矩阵损坏");
        }
    }
}
