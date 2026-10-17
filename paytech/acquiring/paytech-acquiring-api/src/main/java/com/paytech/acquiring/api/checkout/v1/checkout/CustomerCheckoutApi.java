package com.paytech.commerce.customer.api.v1.checkout;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.commerce.customer.api.v1.checkout.request.SaveConsumerRequestBody;
import com.paytech.commerce.customer.api.v1.checkout.response.GetCheckoutResponseBody;
import com.paytech.commerce.customer.api.v1.checkout.response.SaveConsumerResponseBody;

import jakarta.validation.Valid;

/** Commerce 拥有的两个消费者操作；支付提交由同服务 CustomerPaymentApi 承接。 */
@HttpExchange(url = "/customer-gateway-api/v1/checkouts", accept = "application/json")
public interface CustomerCheckoutApi {

    /**
     * 加载或轮询原 Checkout 的当前展示。
     * <li>无效 Token 返回 CHECKOUT_NOT_FOUND，到期返回 CHECKOUT_EXPIRED。</li>
     * <li>仅显式 includeConsumer=true 且已保存资料时回填消费者原值。</li>
     * <li>Payment 查询不可用时只允许轮询，不推断为可重新支付。</li>
     *
     * @param checkoutToken
     *            原公开 Checkout Token，通过 Path 传入
     * @param includeConsumer
     *            是否回填消费者原值，通过 Query 传入，默认 false
     * @return 当前授权的公开视图；不写入初始化信息、支付或接受记录，不延长原期限
     */
    @GetExchange("/{checkoutToken}")
    GetCheckoutResponseBody getCheckout(@PathVariable("checkoutToken") String checkoutToken,
        @RequestParam(name = "includeConsumer", defaultValue = "false") boolean includeConsumer);

    /**
     * 首次提交支付前完整替换消费者资料，可选地址成员省略或 null 表示清空。
     * <li>每次请求按当前订单状态保存，不要求幂等键或重放历史响应。</li>
     * <li>首次法律接受保存后拒绝保存；响应不回显原值或保护引用。</li>
     */
    @PutExchange("/{checkoutToken}/consumer")
    SaveConsumerResponseBody saveConsumer(@PathVariable("checkoutToken") String checkoutToken,
        @Valid @RequestBody SaveConsumerRequestBody body);
}
