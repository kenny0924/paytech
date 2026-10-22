package com.paytech.platform.support.workflow.domainservice.convert;

import java.util.Set;

import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.domain.form.EnumOption;
import com.paytech.platform.support.workflow.domain.form.FieldRules;
import com.paytech.platform.support.workflow.domain.form.FormDefinition;
import com.paytech.platform.support.workflow.domain.form.FormField;

import tools.jackson.databind.JsonNode;

/**
 * 解析字段定义 {@code {"fields":[...]}}（技术方案 3.2），启动表单与 FORM 动作配置共用。
 *
 * <p>结构不符以 {@link WorkflowJsonFieldException} 拒绝；字段 key、数量与规则取值是否合法由发布校验报告。</p>
 */
public final class FormDefinitionJsonReader {

    private static final Set<String> FIELD_FIELDS = Set.of("key", "label", "type", "required", "rules");

    private static final Set<String> RULE_FIELDS = Set.of("maxLength", "min", "max", "scale", "options", "minFiles",
        "maxFiles", "mimeTypes");

    /**
     * 解析 start_form_json。
     *
     * @param json 字段定义文本
     * @return 表单定义
     * @throws WorkflowJsonFieldException JSON 不合法或结构不符
     */
    public FormDefinition read(String json) {
        return readObject(StrictJson.parseObject(json, "start_form_json"), "start_form_json");
    }

    /**
     * 解析已取出的字段定义对象。
     *
     * @param form 含 fields 的对象
     * @param path JSON 路径
     * @return 表单定义
     */
    FormDefinition readObject(JsonNode form, String path) {
        StrictJson.allowOnly(form, path, Set.of("fields"));
        return new FormDefinition(StrictJson.array(form, "fields", path, this::readField));
    }

    /**
     * 解析一个字段。
     *
     * @param field 字段 JSON
     * @param path JSON 路径
     * @return 字段定义
     */
    private FormField readField(JsonNode field, String path) {
        StrictJson.object(field, path);
        StrictJson.allowOnly(field, path, FIELD_FIELDS);
        FormFieldType type = StrictJson.code(field, "type", path, FormFieldType.class);
        if (type == null) {
            throw new WorkflowJsonFieldException(path + ".type", "不能为空");
        }
        return new FormField(StrictJson.text(field, "key", path), StrictJson.text(field, "label", path), type,
            StrictJson.bool(field, "required", path, false),
            readRules(StrictJson.present(field, "rules"), path + ".rules"));
    }

    /**
     * 解析字段规则。
     *
     * @param rules 规则 JSON；可能为 null
     * @param path JSON 路径
     * @return 字段规则
     */
    private FieldRules readRules(JsonNode rules, String path) {
        if (rules == null) {
            return FieldRules.NONE;
        }
        StrictJson.object(rules, path);
        StrictJson.allowOnly(rules, path, RULE_FIELDS);
        return new FieldRules(StrictJson.integer(rules, "maxLength", path), StrictJson.decimal(rules, "min", path),
            StrictJson.decimal(rules, "max", path), StrictJson.integer(rules, "scale", path),
            StrictJson.array(rules, "options", path, this::readOption), StrictJson.integer(rules, "minFiles", path),
            StrictJson.integer(rules, "maxFiles", path), StrictJson.texts(rules, "mimeTypes", path));
    }

    /**
     * 解析枚举选项。
     *
     * @param option 选项 JSON
     * @param path JSON 路径
     * @return 选项
     */
    private EnumOption readOption(JsonNode option, String path) {
        StrictJson.object(option, path);
        StrictJson.allowOnly(option, path, Set.of("value", "label"));
        return new EnumOption(StrictJson.text(option, "value", path), StrictJson.text(option, "label", path));
    }
}
