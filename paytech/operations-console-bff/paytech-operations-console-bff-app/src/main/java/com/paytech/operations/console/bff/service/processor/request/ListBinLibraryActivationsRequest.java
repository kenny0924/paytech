package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** BIN 库激活历史的内部请求。 */
@Getter
@AllArgsConstructor
public class ListBinLibraryActivationsRequest {

    /**
     * 分页参数
     * <li>入口已按公共分页规则补默认值并校验。</li>
     */
    @NotNull(message = "分页参数不能为空")
    private final PagingRequestBody paging;
}
