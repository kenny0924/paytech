package com.paytech.platform.support.workflow.domainservice.validator;

import java.math.BigDecimal;
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
 * 校验字段定义（技术方案 3.2），启动表单与 FORM 动作配置共用：最多 50 个字段，key 合法且唯一，
 * 规则只出现在对应类型上且取值不越界。问题的 itemKey 为字段 key。
 */
final class FormDefinitionRules {

    /** 每个表单的字段上限。 */
    static final int MAX_FIELDS = 50;

    /** TEXT 最大长度。 */
    static final int TEXT_LIMIT = 255;

    /** LONG_TEXT 最大长度。 */
    static final int LONG_TEXT_LIMIT = 4000;

    /** NUMBER 最多小数位。 */
    static final int MAX_SCALE = 8;

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
     * 返回字段规则的问题说明。
     *
     * @param type 字段类型
     * @param rules 字段规则
     * @return 问题说明；合法时为 null
     */
    static String ruleProblem(FormFieldType type, FieldRules rules) {
        String foreign = foreignRule(type, rules);
        if (foreign != null) {
            return foreign + " 不能用于 " + type.getCode() + " 字段";
        }
        return switch (type) {
            case TEXT -> lengthProblem(rules.maxLength(), TEXT_LIMIT);
            case LONG_TEXT -> lengthProblem(rules.maxLength(), LONG_TEXT_LIMIT);
            case NUMBER -> numberProblem(rules);
            case ENUM -> optionProblem(rules);
            case FILE -> fileCountProblem(rules.minFiles(), rules.maxFiles(), rules.mimeTypes().stream()
                .anyMatch(mime -> !WorkflowValueFormats.isMimeType(mime)));
            default -> null;
        };
    }

    /**
     * 找出不属于该类型的规则名。
     *
     * @param type 字段类型
     * @param rules 字段规则
     * @return 第一个不适用的规则名；全部适用时为 null
     */
    private static String foreignRule(FormFieldType type, FieldRules rules) {
        boolean text = type == FormFieldType.TEXT || type == FormFieldType.LONG_TEXT;
        boolean number = type == FormFieldType.NUMBER;
        boolean file = type == FormFieldType.FILE;
        if (!text && rules.maxLength() != null) {
            return "maxLength";
        }
        if (!number && (rules.min() != null || rules.max() != null || rules.scale() != null)) {
            return "min / max / scale";
        }
        if (type != FormFieldType.ENUM && !rules.options().isEmpty()) {
            return "options";
        }
        if (!file && (rules.minFiles() != null || rules.maxFiles() != null || !rules.mimeTypes().isEmpty())) {
            return "minFiles / maxFiles / mimeTypes";
        }
        return null;
    }

    /**
     * 文本长度规则问题。
     *
     * @param maxLength 配置的最大长度
     * @param limit 类型上限
     * @return 问题说明或 null
     */
    private static String lengthProblem(Integer maxLength, int limit) {
        if (maxLength != null && (maxLength < 1 || maxLength > limit)) {
            return "maxLength 必须在 1 到 " + limit + " 之间";
        }
        return null;
    }

    /**
     * 数字规则问题。
     *
     * @param rules 字段规则
     * @return 问题说明或 null
     */
    private static String numberProblem(FieldRules rules) {
        BigDecimal min = rules.min();
        BigDecimal max = rules.max();
        if (min != null && max != null && min.compareTo(max) > 0) {
            return "min 不能大于 max";
        }
        if (rules.scale() != null && (rules.scale() < 0 || rules.scale() > MAX_SCALE)) {
            return "scale 必须在 0 到 " + MAX_SCALE + " 之间";
        }
        return null;
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

    /**
     * 文件数量与类型规则问题；上传文件动作配置复用。
     *
     * @param minFiles 最少文件数
     * @param maxFiles 最多文件数
     * @param invalidMime 是否有不合法的 MIME 类型
     * @return 问题说明或 null
     */
    static String fileCountProblem(Integer minFiles, Integer maxFiles, boolean invalidMime) {
        if (minFiles != null && minFiles < 0) {
            return "minFiles 不能小于 0";
        }
        if (maxFiles != null && maxFiles < 1) {
            return "maxFiles 不能小于 1";
        }
        if (minFiles != null && maxFiles != null && minFiles > maxFiles) {
            return "minFiles 不能大于 maxFiles";
        }
        return invalidMime ? "mimeTypes 含有不合法的 MIME 类型" : null;
    }
}
