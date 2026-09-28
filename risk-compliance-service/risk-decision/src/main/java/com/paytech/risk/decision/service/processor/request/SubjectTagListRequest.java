package com.paytech.risk.decision.service.processor.request;

import com.paytech.risk.decision.domain.enums.DictionaryStatus;

import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 拒绝标签当前值列表的内部查询参数。 */
@Getter
@AllArgsConstructor
public class SubjectTagListRequest {

    /**
     * 页码。
     * <li>缺省为 1；范围由公共分页能力校验。</li>
     */
    private final Integer page;

    /**
     * 每页条数。
     * <li>缺省为 10；可选值由公共分页能力校验。</li>
     */
    private final Integer size;

    /**
     * 可选字面搜索关键词。
     * <li>1–100 个 Unicode 码点，不允许首尾空白、控制字符或孤立代理项。</li>
     */
    @Pattern(regexp = "(?!\\p{javaWhitespace})[^\\p{Cc}\\p{Cs}]{1,100}(?<!\\p{javaWhitespace})",
        message = "字典文本格式或长度无效")
    private final String keyword;

    /**
     * 可选 ACTIVE 或 DISABLED 状态。
     * <li>未提供时查询全部。</li>
     */
    private final DictionaryStatus status;
}
