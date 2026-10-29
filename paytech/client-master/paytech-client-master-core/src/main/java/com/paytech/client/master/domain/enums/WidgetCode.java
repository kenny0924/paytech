package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 采集 Schema 字段的前端控件代码。 */
public enum WidgetCode implements CodeEnum {
    /** 单行文本。 */
    TEXT,

    /** 多行文本。 */
    TEXTAREA,

    /** 日期。 */
    DATE,

    /** 国家选择。 */
    COUNTRY,

    /** 目录选择。 */
    SELECT,

    /** 目录多选；取值为规范化逗号串（大写、去重、按代码升序）。 */
    MULTI_SELECT,

    /** 法律标识（类型、号码、签发国、日期）。 */
    IDENTIFIER,

    /** 按地址格式组件渲染的地址。 */
    ADDRESS,

    /** 姓名。 */
    NAME,

    /** 关联人员列表。 */
    PERSON_LIST,

    /** 材料槽。 */
    DOCUMENT_SLOT,

    /** 声明勾选。 */
    DECLARATION,

    /** 网址。 */
    URL,

    /** 金额。 */
    MONEY,

    /** 邮箱。 */
    EMAIL,

    /** 电话。 */
    PHONE,

    /** 是否选择。 */
    BOOLEAN;

    /** 稳定 Code，等于常量名。 */
    @Override
    public String getCode() {
        return name();
    }

    /**
     * 按 Code 解析
     *
     * @param code 待解析的 Code
     * @return 对应常量；不认识或为空时返回 null，由调用方按自身合同处理
     */
    public static WidgetCode fromCode(String code) {
        for (WidgetCode value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
