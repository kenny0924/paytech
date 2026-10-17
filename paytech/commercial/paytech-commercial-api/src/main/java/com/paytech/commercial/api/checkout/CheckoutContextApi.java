package com.paytech.commercial.api.checkout;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.commercial.api.checkout.request.ResolveCheckoutContextRequestBody;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody;

import jakarta.validation.Valid;

/** Commerce 私有最终上下文查询，不保存 Assignment 或解析回执。 */
@HttpExchange(accept = "application/json", contentType = "application/json")
public interface CheckoutContextApi {
    /**
     * 为 Checkout 创建解析完整商业上下文。
     *
     * @param callerService
     *            私网调用范围；仅允许 commerce-service，此 Header 本身不构成身份认证
     * @param request
     *            Client、订单和商业时点；不允许指定 OE
     * @return 首个完整通过的候选，由 Commerce 原子冻结；依赖不可用 503，配置冲突 409
     */
    @PostExchange("/checkout-context/v1/resolutions")
    ResolveCheckoutContextResponseBody resolve(
            @RequestHeader(value = "X-Caller-Service", required = false) String callerService,
        @Valid @RequestBody ResolveCheckoutContextRequestBody request);
}
