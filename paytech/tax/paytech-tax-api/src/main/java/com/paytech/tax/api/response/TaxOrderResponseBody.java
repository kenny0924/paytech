package com.paytech.tax.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 订单的税务全貌（Operations 税务查询页）
 * <li>多表组合的只读结果；页面模型由 BFF 组装。</li>
 */
@Getter
@Setter
public class TaxOrderResponseBody {
    /** 订单号 */
    private String orderNo;

    /** 该订单全部计算，按 sourceRevision 升序；最后一条为当前报价 */
    private List<TaxCalculationResponseBody> calculations;

    /** 正式交易（本期至多一条）；未支付或 NOT_MANAGED 时为空列表 */
    private List<TaxTransactionResponseBody> transactions;

    /** 计算组件与底稿引用到的注册 */
    private List<TaxRegistrationResponseBody> registrations;

    /** 计算组件引用到的地区包 */
    private List<TaxPolicyResponseBody> policies;
}
