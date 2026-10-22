package com.paytech.platform.support.dataquery.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板相关的系统硬上限，由调用方从 data-query.limits 传入。 */
@Getter
@AllArgsConstructor
public final class TemplateLimits {

    /** 正文最大长度，按 UTF-8 字节计（max-body-length，默认 48 KB）。 */
    private final int maxBodyLength;

    /** 集合参数最大元素数（max-list-size，默认 1000）。 */
    private final int maxListSize;

    /** 展开校验全列举的组合数上限，超过时改用覆盖集（max-validation-combinations，默认 256）。 */
    private final int maxValidationCombinations;
}
