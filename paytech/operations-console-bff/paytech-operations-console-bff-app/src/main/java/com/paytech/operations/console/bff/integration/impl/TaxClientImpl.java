package com.paytech.operations.console.bff.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.operations.console.bff.integration.TaxClient;
import com.paytech.tax.api.TaxOrderQueryApi;
import com.paytech.tax.api.response.TaxOrderResponseBody;

import lombok.AllArgsConstructor;

/**
 * tax 订单查询的 HTTP 转发：单次调用并核对响应形状
 * <li>订单号须与请求一致，计算至少一条，交易、注册与地区包列表不得缺失；否则按上游响应无效 502 处理，不把残缺数据当作"无登记"展示。</li>
 */
@Component
@AllArgsConstructor
public class TaxClientImpl implements TaxClient {
    private final TaxOrderQueryApi queryApi;

    /** 单次查询并核对订单身份与列表完整性。 */
    @Override
    public TaxOrderResponseBody orderTax(String orderNo) {
        var response = queryApi.getOrderTax(orderNo);
        require(response != null && orderNo.equals(response.getOrderNo()));
        require(response.getCalculations() != null && !response.getCalculations().isEmpty());
        require(response.getTransactions() != null && response.getRegistrations() != null
            && response.getPolicies() != null);
        return response;
    }

    /** 上游响应不满足合同时按 502 失败。 */
    private static void require(boolean valid) {
        if (!valid) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
    }
}
