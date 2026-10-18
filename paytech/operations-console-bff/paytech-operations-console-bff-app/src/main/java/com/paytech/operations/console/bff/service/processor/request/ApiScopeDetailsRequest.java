package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.domain.enums.ApiScopeReferenceKind;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** API Scope 详情页的内部请求；查询参数原样承载，引用类型与分页组合由 Processor 校验。 */
@Getter
@AllArgsConstructor
public class ApiScopeDetailsRequest {

    /** 入站边界已校验的操作人身份 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /**
     * Scope 编码
     * <li>格式由 Platform 校验，BFF 不追加规则。</li>
     */
    @NotNull(message = "Scope 编码不能为空")
    private final String scopeCode;

    /**
     * 引用类型 Code
     * <li>可空，缺省视为 NONE。</li>
     */
    private final String referenceKind;

    /**
     * 引用分页页码
     * <li>可空；referenceKind 为 NONE 时必须为空。</li>
     */
    private final Integer page;

    /**
     * 引用分页条数
     * <li>可空；referenceKind 为 NONE 时必须为空，其他类型最多 100。</li>
     */
    private final Integer size;

    /**
     * 解析引用类型。
     *
     * @return 缺省为 NONE；未知 Code 返回 null，由 Processor 按参数错误拒绝
     */
    public ApiScopeReferenceKind resolvedReferenceKind() {
        return referenceKind == null ? ApiScopeReferenceKind.NONE : ApiScopeReferenceKind.fromCode(referenceKind);
    }
}
