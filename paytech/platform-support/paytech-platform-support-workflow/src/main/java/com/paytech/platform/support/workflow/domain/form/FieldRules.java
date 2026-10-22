package com.paytech.platform.support.workflow.domain.form;

import java.math.BigDecimal;
import java.util.List;

/**
 * 字段规则；只有与字段类型对应的规则可以出现，其余为 null 或空列表。
 *
 * @param maxLength TEXT / LONG_TEXT 的最大长度
 * @param min NUMBER 的最小值
 * @param max NUMBER 的最大值
 * @param scale NUMBER 的最多小数位
 * @param options ENUM 的选项
 * @param minFiles FILE 的最少文件数
 * @param maxFiles FILE 的最多文件数
 * @param mimeTypes FILE 允许的 MIME 类型；为空表示不限
 */
public record FieldRules(Integer maxLength, BigDecimal min, BigDecimal max, Integer scale, List<EnumOption> options,
    Integer minFiles, Integer maxFiles, List<String> mimeTypes) {

    /** 没有任何规则。 */
    public static final FieldRules NONE = new FieldRules(null, null, null, null, List.of(), null, null, List.of());

    /** 复制列表，保持不可变。 */
    public FieldRules {
        options = List.copyOf(options);
        mimeTypes = List.copyOf(mimeTypes);
    }
}
