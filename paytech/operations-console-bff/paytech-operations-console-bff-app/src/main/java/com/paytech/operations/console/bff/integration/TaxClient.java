package com.paytech.operations.console.bff.integration;

import com.paytech.tax.api.response.TaxOrderResponseBody;

/**
 * tax 订单税务查询的本地端口；计算、正式交易、底稿与单据的事实归 tax，BFF 只读取并组装页面
 * <li>单次调用，不重试；查询不携带操作人。</li>
 */
public interface TaxClient {

    /**
     * 按订单号读取税务全貌
     *
     * @param orderNo 入口已校验格式的公开订单号
     * @return 订单号与请求一致、至少一条计算且各列表非空的 tax 响应；没有任何计算时 tax 返回 404 TAX_ORDER_NOT_FOUND
     */
    TaxOrderResponseBody orderTax(String orderNo);
}
