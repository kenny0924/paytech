package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 角色列表页的内部请求。 */
@Getter
@AllArgsConstructor
public class RoleListRequest {

    /** 入站边界已校验的操作人身份 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /**
     * 分页参数
     * <li>入口已按公共分页规则补默认值并校验。</li>
     */
    @NotNull(message = "分页参数不能为空")
    private final PagingRequestBody paging;
}
