package com.paytech.platform.support.workflow.domain.form;

import com.paytech.platform.support.workflow.core.enums.FormFieldType;

/**
 * 表单字段定义，启动表单与 FORM 动作共用。
 *
 * @param key 字段 key，表单内唯一，同时是变量名后缀
 * @param label 显示名
 * @param type 字段类型
 * @param required 是否必填
 * @param rules 字段规则
 */
public record FormField(String key, String label, FormFieldType type, boolean required, FieldRules rules) {
}
