package com.paytech.counterparty.api.customer;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.counterparty.api.customer.request.ResolveCustomerRequestBody;
import com.paytech.counterparty.api.customer.response.CardSavingEligibilityResponseBody;
import com.paytech.counterparty.api.customer.response.CustomerSummaryResponseBody;
import com.paytech.counterparty.api.customer.response.ResolveCustomerResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * counterparty 对 acquiring 的内部合同（私网 HTTP，不经 Gateway）；ID 一律为规范正十进制 BIGINT 字符串。
 * <li>Customer 主键与公开编号全局稳定：Payment 已存卡以 customerId 为键，customerNo 作为 Payment 的付款人引用（S6）。</li>
 * <li>调用方必须在本地数据库事务外调用。</li>
 */
@HttpExchange(value = "/counterparty/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface CounterpartyCustomerApi {
    /** 规范正十进制 BIGINT。 */
    String DECIMAL_ID = "^[1-9][0-9]{0,18}$";

    /**
     * 取得 Client 下该引用的有效 Customer，没有则新建；并发新建只保留一个（{@code uk(client_id, active_reference)}，撞唯一键时重读）。
     *
     * @param requestBody Client 与已校验的不透明消费者引用
     * @return Customer 与是否本次新建
     */
    @PostExchange(value = "/customers/resolve", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResolveCustomerResponseBody resolveCustomer(@RequestBody @NotNull @Valid ResolveCustomerRequestBody requestBody);

    /**
     * 按主键读取 Customer，含已删除。
     *
     * @param customerId Customer 主键
     * @return Customer 摘要；不存在时 404 CUSTOMER_NOT_FOUND
     */
    @GetExchange("/customers/{customerId}")
    CustomerSummaryResponseBody getCustomer(@PathVariable("customerId") @NotBlank @Pattern(regexp = DECIMAL_ID) String customerId);

    /**
     * 存卡资格：组合本库 Customer 状态与 client-master 的 Client 已存卡开关，失败关闭。
     *
     * @param clientId 订单所属 Client
     * @param customerId 订单关联的 Customer
     * @return 是否可保存卡或使用已存卡；不可时给出原因
     */
    @GetExchange("/card-saving-eligibility")
    CardSavingEligibilityResponseBody cardSavingEligibility(
        @RequestParam("clientId") @NotBlank @Pattern(regexp = DECIMAL_ID) String clientId,
        @RequestParam("customerId") @NotBlank @Pattern(regexp = DECIMAL_ID) String customerId);
}
