package com.paytech.platform.support.dataquery.domain;

import java.util.List;

import com.paytech.platform.support.dataquery.core.enums.JointQueryExclusionReason;
import com.paytech.platform.support.dataquery.core.enums.QueryParameterType;
import com.paytech.platform.support.dataquery.core.enums.TemplateProblemLevel;
import com.paytech.platform.support.dataquery.template.QueryParameterDeclaration;
import com.paytech.platform.support.dataquery.template.TemplateProblem;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模板是否参与联合查询（B2）：模板 ACTIVE、数据源 ACTIVE、配置了 joint、键参数必填、其余参数可选或有默认值。
 * <li>纯内存判断，保存提示、模板详情与可达预览共用；字段合法性（键类型存在、参数类型匹配）由保存校验负责。</li>
 */
@Getter
@AllArgsConstructor
public final class JointQueryParticipation {

    /** 是否参与。 */
    private final boolean participating;

    /** 不参与的原因；参与时为 null。 */
    private final JointQueryExclusionReason reason;

    /** 中文说明。 */
    private final String message;

    /** 键参数是否为 LIST：true 时同层同键类型的值合并为一次执行，false 时逐值执行。 */
    private final boolean keyParameterList;

    /**
     * 按版本内容与当前状态评估。
     *
     * @param joint 联合查询配置，不能为 null
     * @param declarations 参数声明
     * @param templateActive 模板是否 ACTIVE
     * @param dataSourceActive 数据源是否存在且 ACTIVE
     * @return 评估结果
     */
    public static JointQueryParticipation evaluate(QueryJointConfig joint, List<QueryParameterDeclaration> declarations,
        boolean templateActive, boolean dataSourceActive) {
        String keyName = joint.getKeyParameter().getName();
        QueryParameterDeclaration key = declarations.stream().filter(item -> item.getName().equals(keyName))
            .findFirst().orElse(null);
        if (key == null || !key.isRequired()) {
            return excluded(JointQueryExclusionReason.KEY_PARAMETER_NOT_REQUIRED, "不参与联合查询：键参数 " + keyName
                + (key == null ? " 未声明" : " 不是必填"));
        }
        QueryParameterDeclaration other = declarations.stream().filter(item -> !item.getName().equals(keyName))
            .filter(item -> item.isRequired() && item.getDefaultValue() == null).findFirst().orElse(null);
        if (other != null) {
            return excluded(JointQueryExclusionReason.OTHER_REQUIRED_PARAMETER, "不参与联合查询：必填键参数不唯一，参数 "
                + other.getName() + " 必填且没有默认值");
        }
        if (!templateActive) {
            return excluded(JointQueryExclusionReason.TEMPLATE_INACTIVE, "不参与联合查询：模板未启用");
        }
        if (!dataSourceActive) {
            return excluded(JointQueryExclusionReason.DATA_SOURCE_INACTIVE, "不参与联合查询：数据源未启用");
        }
        return new JointQueryParticipation(true, null, "参与联合查询，触发键 " + joint.getKeyParameter().getKeyType(),
            key.getType() == QueryParameterType.LIST);
    }

    /**
     * 不参与。
     *
     * @param reason 原因
     * @param message 中文说明
     * @return 结果
     */
    public static JointQueryParticipation excluded(JointQueryExclusionReason reason, String message) {
        return new JointQueryParticipation(false, reason, message, false);
    }

    /**
     * 保存与校验接口返回的提示：参与为 INFO，不参与为 WARNING，字段 joint。
     *
     * @return 提示
     */
    public TemplateProblem problem() {
        return TemplateProblem.field(participating ? TemplateProblemLevel.INFO : TemplateProblemLevel.WARNING, "joint",
            message);
    }
}
