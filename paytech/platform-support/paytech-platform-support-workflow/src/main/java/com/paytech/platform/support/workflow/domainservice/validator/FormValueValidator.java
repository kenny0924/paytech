package com.paytech.platform.support.workflow.domainservice.validator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.domain.attachment.AttachmentFact;
import com.paytech.platform.support.workflow.domain.form.FormDefinition;
import com.paytech.platform.support.workflow.domain.form.FormField;

import tools.jackson.databind.JsonNode;

/**
 * 按字段定义检查提交值（启动表单与 FORM 动作共用），按字段路径返回中文原因。
 *
 * <p>2026-10-22 起只检查必填与值类型（网关条件依赖类型正确），并保留平台文本长度上限；字段规则中的 maxLength、
 * min / max / scale、文件数量与 MIME 类型不再校验。值形态：TEXT / LONG_TEXT / ENUM / DATE / NUMBER 为字符串（NUMBER
 * 为普通十进制字符串），BOOLEAN 为布尔，FILE 为文件地址（/files/v1/{fileId}）字符串数组。FILE 元素扫描中不挡，
 * 已确认未通过扫描的报告为字段错误；归属由调用方在保存前拒绝。</p>
 */
public final class FormValueValidator {

    /** TEXT 的平台长度上限。 */
    static final int TEXT_LIMIT = 255;

    /** LONG_TEXT 的平台长度上限。 */
    static final int LONG_TEXT_LIMIT = 4000;

    /**
     * 检查提交值并返回请求体字段路径（{@code root.<key>}、文件元素 {@code root.<key>[i]}）到原因，供 422 字段错误使用；
     * 原因与完成动作的检查一致。
     *
     * @param form 字段定义
     * @param values 提交值对象
     * @param files 引用文件的事实，按文件 ID
     * @param root 提交值在请求体中的属性名，如 startValues
     * @return 请求体字段路径到原因；通过时为空
     */
    public Map<String, String> requestErrors(FormDefinition form, JsonNode values, Map<Long, AttachmentFact> files,
        String root) {
        if (values == null || !values.isObject()) {
            return Map.of(root, "必须是 JSON 对象");
        }
        Map<String, String> prefixed = new LinkedHashMap<>();
        fieldErrors(form, values, files).forEach((path, reason) -> prefixed.put(root + "." + path, reason));
        return prefixed;
    }

    /**
     * 按字段检查提交值。
     *
     * @param form 字段定义
     * @param values 提交值对象；调用方已确认是 JSON 对象
     * @param files 引用文件的事实，按文件 ID
     * @return 相对路径（字段 key，文件元素为 {@code key[i]}）到原因；通过时为空
     */
    public Map<String, String> fieldErrors(FormDefinition form, JsonNode values, Map<Long, AttachmentFact> files) {
        Map<String, String> errors = new LinkedHashMap<>();
        Set<String> keys = form.fields().stream().map(FormField::key).collect(Collectors.toSet());
        values.propertyNames().stream().filter(name -> !keys.contains(name))
            .forEach(name -> errors.put(name, "未定义的字段"));
        for (FormField field : form.fields()) {
            fieldErrors(field, values.get(field.key()), files, errors);
        }
        return errors;
    }

    /**
     * 单个字段的问题：空值只看必填；FILE 交给附件规则检查形态与文件可用性；其余检查值类型。
     *
     * @param field 字段定义
     * @param value 提交值；缺失为 null
     * @param files 引用文件的事实
     * @param errors 收集结果
     */
    void fieldErrors(FormField field, JsonNode value, Map<Long, AttachmentFact> files, Map<String, String> errors) {
        if (isEmpty(value)) {
            if (field.required()) {
                errors.put(field.key(), "必填");
            }
            return;
        }
        if (field.type() == FormFieldType.FILE) {
            errors.putAll(AttachmentRules.valueErrors(value, field.key(), files));
            return;
        }
        String problem = valueProblem(field, value);
        if (problem != null) {
            errors.put(field.key(), problem);
        }
    }

    /**
     * 非文件字段的值类型问题；ENUM 须为定义的选项值，网关按该值分支。
     *
     * @param field 字段定义
     * @param value 非空提交值
     * @return 问题说明；合法时为 null
     */
    String valueProblem(FormField field, JsonNode value) {
        return switch (field.type()) {
            case TEXT -> textProblem(value, TEXT_LIMIT);
            case LONG_TEXT -> textProblem(value, LONG_TEXT_LIMIT);
            case NUMBER -> value.isString() && WorkflowValueFormats.isDecimal(value.asString()) ? null
                : "必须是十进制数字字符串";
            case BOOLEAN -> value.isBoolean() ? null : "必须是布尔值";
            case ENUM -> value.isString() && field.rules().options().stream().anyMatch(option -> option.value().equals(
                value.asString())) ? null : "不是可选值";
            case DATE -> value.isString() && WorkflowValueFormats.isDate(value.asString()) ? null
                : "必须是 YYYY-MM-DD 日期";
            case FILE -> null;
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
     * 文本类型与平台长度上限问题。
     *
     * @param value 提交值
     * @param limit 平台长度上限
     * @return 问题说明或 null
     */
    static String textProblem(JsonNode value, int limit) {
        if (!value.isString()) {
            return "必须是文本";
        }
        return value.asString().codePointCount(0, value.asString().length()) > limit ? "超过 " + limit + " 个字符"
            : null;
    }
}
