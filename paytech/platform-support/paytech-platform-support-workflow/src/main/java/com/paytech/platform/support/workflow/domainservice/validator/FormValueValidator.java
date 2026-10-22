package com.paytech.platform.support.workflow.domainservice.validator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.domain.attachment.AttachmentFact;
import com.paytech.platform.support.workflow.domain.form.FieldRules;
import com.paytech.platform.support.workflow.domain.form.FormDefinition;
import com.paytech.platform.support.workflow.domain.form.FormField;

import tools.jackson.databind.JsonNode;

/**
 * 按字段定义校验提交值（启动表单与 FORM 动作共用，规则 B2 的字段规则验证），返回全部中文问题。
 *
 * <p>值形态：TEXT / LONG_TEXT / ENUM / DATE / NUMBER 为字符串（NUMBER 为普通十进制字符串），BOOLEAN 为布尔，FILE 为文件地址（/files/v1/{fileId}）字符串数组。
 * FILE 按 {@link AttachmentRules} 校验数量、类型与扫描状态，文件事实由调用方事先查询；归属另由调用方在保存前拒绝。</p>
 */
public final class FormValueValidator {

    /**
     * 校验不含文件的提交值；FILE 字段有值时因没有文件事实报告文件不存在。
     *
     * @param form 字段定义
     * @param values 提交值对象
     * @return 问题列表；通过时为空
     */
    public List<String> validate(FormDefinition form, JsonNode values) {
        return validate(form, values, Map.of());
    }

    /**
     * 校验提交值。
     *
     * @param form 字段定义
     * @param values 提交值对象
     * @param files 引用文件的事实，按文件 ID
     * @return 问题列表；通过时为空
     */
    public List<String> validate(FormDefinition form, JsonNode values, Map<Long, AttachmentFact> files) {
        List<String> problems = new ArrayList<>();
        if (values == null || !values.isObject()) {
            return List.of("提交值必须是 JSON 对象");
        }
        Set<String> keys = form.fields().stream().map(FormField::key).collect(Collectors.toSet());
        values.propertyNames().stream().filter(name -> !keys.contains(name))
            .forEach(name -> problems.add("未定义的字段：" + name));
        for (FormField field : form.fields()) {
            String problem = fieldProblem(field, values.get(field.key()), files);
            if (problem != null) {
                problems.add(field.label() + "（" + field.key() + "）" + problem);
            }
        }
        return problems;
    }

    /**
     * 单个字段的问题。
     *
     * @param field 字段定义
     * @param value 提交值；缺失为 null
     * @param files 引用文件的事实
     * @return 问题说明；合法时为 null
     */
    String fieldProblem(FormField field, JsonNode value, Map<Long, AttachmentFact> files) {
        if (isEmpty(value)) {
            return field.required() ? "必填" : null;
        }
        FieldRules rules = field.rules();
        return switch (field.type()) {
            case TEXT -> textProblem(value, rules.maxLength() == null ? FormDefinitionRules.TEXT_LIMIT : rules.maxLength());
            case LONG_TEXT -> textProblem(value,
                rules.maxLength() == null ? FormDefinitionRules.LONG_TEXT_LIMIT : rules.maxLength());
            case NUMBER -> numberProblem(value, rules);
            case BOOLEAN -> value.isBoolean() ? null : "必须是布尔值";
            case ENUM -> value.isString() && rules.options().stream().anyMatch(option -> option.value().equals(
                value.asString())) ? null : "不是可选值";
            case DATE -> value.isString() && WorkflowValueFormats.isDate(value.asString()) ? null
                : "必须是 YYYY-MM-DD 日期";
            case FILE -> AttachmentRules.valueProblem(value, rules.minFiles(), rules.maxFiles(), rules.mimeTypes(), files);
        };
    }

    /**
     * 空值：缺失、null、空字符串或空数组。
     *
     * @param value 提交值
     * @return 为空返回 true
     */
    static boolean isEmpty(JsonNode value) {
        return value == null || value.isNull() || value.isString() && value.asString().isEmpty()
            || value.isArray() && value.isEmpty();
    }

    /**
     * 文本长度问题。
     *
     * @param value 提交值
     * @param limit 最大长度
     * @return 问题说明或 null
     */
    private String textProblem(JsonNode value, int limit) {
        if (!value.isString()) {
            return "必须是文本";
        }
        return value.asString().codePointCount(0, value.asString().length()) > limit ? "超过 " + limit + " 个字符"
            : null;
    }

    /**
     * 数字问题：普通十进制字符串，范围与小数位。
     *
     * @param value 提交值
     * @param rules 字段规则
     * @return 问题说明或 null
     */
    private String numberProblem(JsonNode value, FieldRules rules) {
        if (!value.isString() || !WorkflowValueFormats.isDecimal(value.asString())) {
            return "必须是十进制数字字符串";
        }
        BigDecimal number = new BigDecimal(value.asString());
        if (rules.min() != null && number.compareTo(rules.min()) < 0) {
            return "不能小于 " + rules.min().toPlainString();
        }
        if (rules.max() != null && number.compareTo(rules.max()) > 0) {
            return "不能大于 " + rules.max().toPlainString();
        }
        if (rules.scale() != null && Math.max(number.scale(), 0) > rules.scale()) {
            return "小数位不能超过 " + rules.scale();
        }
        return null;
    }
}
