package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 字典精确引用分页，缺省范围为 ALL。 */
@Getter
@AllArgsConstructor
public class RejectionReasonReferencesRequest {
    /**
     * 永久字典编码。
     * <li>缺失或格式错误在查询前拒绝。</li>
     */
    @NotNull
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String reasonCode;
    /**
     * 页码。
     * <li>缺省 1，由公共分页能力验证。</li>
     */
    private final Integer page;
    /**
     * 每页条数。
     * <li>缺省 10，由公共分页能力验证。</li>
     */
    private final Integer size;
    /**
     * 可选 ALL、CURRENT、HISTORY、DELETED。
     * <li>未提供时查询全部。</li>
     */
    @Pattern(regexp = "ALL|CURRENT|HISTORY|DELETED")
    private final String scope;
}
