package com.paytech.platform.support.workflow.domainservice.validator;

import java.util.HashSet;
import java.util.Set;

import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.form.EnumOption;
import com.paytech.platform.support.workflow.domain.form.FieldRules;
import com.paytech.platform.support.workflow.domain.form.FormDefinition;
import com.paytech.platform.support.workflow.domain.form.FormField;

/**
 * 校验字段定义（技术方案 3.2），启动表单与 FORM 动作配置共用：最多 50 个字段，key 合法且唯一，显示名非空，
 * 枚举字段的选项合法。问题的 itemKey 为字段 key。
 */
final class FormDefinitionRules {

    /** 每个表单的字段上限。 */
    static final int MAX_FIELDS = 50;

    private final ProblemCollector problems;

    /**
     * 绑定问题收集。
     *
     * @param problems 问题收集
     */
    FormDefinitionRules(ProblemCollector problems) {
        this.problems = problems;
    }

    /**
     * 校验一个表单。
     *
     * @param form 表单定义
     * @param owner 表单归属的中文说明，如“启动表单”
     */
    void check(FormDefinition form, String owner) {
        if (form.fields().size() > MAX_FIELDS) {
            problems.graph(WorkflowProblemCode.TOO_MANY_FIELDS,
                owner + "有 " + form.fields().size() + " 个字段，超过上限 " + MAX_FIELDS + " 个");
        }
        Set<String> keys = new HashSet<>();
        for (FormField field : form.fields()) {
            if (!WorkflowValueFormats.isKey(field.key())) {
                problems.item(WorkflowProblemCode.FIELD_KEY_INVALID, null, field.key(),
                    owner + "字段 key 必须以字母开头，只含字母、数字或下划线，最长 64：" + field.key());
            } else if (!keys.add(field.key())) {
                problems.item(WorkflowProblemCode.FIELD_KEY_DUPLICATE, null, field.key(), owner + "字段 key 重复：" + field.key());
            }
            if (WorkflowValueFormats.isBlank(field.label())) {
                problems.item(WorkflowProblemCode.FIELD_LABEL_MISSING, null, field.key(), owner + "字段缺少显示名");
            }
            String invalid = ruleProblem(field.type(), field.rules());
            if (invalid != null) {
                problems.item(WorkflowProblemCode.FIELD_RULE_INVALID, null, field.key(),
                    owner + "字段 " + field.key() + " 的规则不合法：" + invalid);
            }
        }
    }

    /**
     * 返回字段规则的问题说明：只校验网关条件需要的枚举选项；maxLength、min / max / scale、minFiles / maxFiles /
     * mimeTypes 等校验键 2026-10-22 起忽略（存量配置照常接受，不再校验取值）。
     *
     * @param type 字段类型
     * @param rules 字段规则
     * @return 问题说明；合法时为 null
     */
    static String ruleProblem(FormFieldType type, FieldRules rules) {
        return type == FormFieldType.ENUM ? optionProblem(rules) : null;
    }

    /**
     * 枚举选项问题：至少一个，值非空且不重复，显示名非空。
     *
     * @param rules 字段规则
     * @return 问题说明或 null
     */
    private static String optionProblem(FieldRules rules) {
        if (rules.options().isEmpty()) {
            return "枚举字段至少需要一个选项";
        }
        Set<String> values = new HashSet<>();
        for (EnumOption option : rules.options()) {
            if (WorkflowValueFormats.isBlank(option.value()) || WorkflowValueFormats.isBlank(option.label())) {
                return "选项的值与显示名不能为空";
            }
            if (!values.add(option.value())) {
                return "选项值重复：" + option.value();
            }
        }
        return null;
    }
}
