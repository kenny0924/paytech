package com.paytech.tax.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.tax.api.response.TaxOrderResponseBody;

import jakarta.validation.constraints.Pattern;

/**
 * 按订单查询税务全貌
 * <li>内部只读接口，调用方为 Operations BFF（财务 → 税务查询页）；不开显式事务。</li>
 */
@HttpExchange(url = "/tax/v1/orders", accept = "application/json")
public interface TaxOrderQueryApi {

    /**
     * 查询订单的全部税务计算、正式交易、申报底稿、单据与所引用的注册和地区包
     *
     * @param orderNo acquiring 公开订单号（计算请求的 sourceObjectNo）
     * @return 计算按 sourceRevision 升序；没有任何计算时 404 TAX_ORDER_NOT_FOUND
     */
    @GetExchange("/{orderNo}")
    TaxOrderResponseBody getOrderTax(
        @PathVariable("orderNo") @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}") String orderNo);
}
