package com.paytech.operations.console.bff.integration.impl;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingResponseBody;

/** 名单与执行发布回执的身份和分页核对；业务语义由 Risk 判断。 */
final class RiskControlResponsePolicy {
    private RiskControlResponsePolicy() { }

    /** 分页回执的页码、条数与条目上限必须与请求一致。 */
    static void requirePage(PagingResponseBody<?> response, int page, int size) {
        require(response != null && response.page() == page && response.size() == size && response.items().size() <= size);
    }

    /** 上游回执不符合合同时按上游无效响应处理。 */
    static void require(boolean valid) {
        if (!valid) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
    }
}
