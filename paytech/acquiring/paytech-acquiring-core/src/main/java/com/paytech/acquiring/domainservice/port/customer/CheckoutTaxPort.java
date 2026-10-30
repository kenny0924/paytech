package com.paytech.acquiring.domainservice.port.customer;

import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxCalculation;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxParties;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxQuoteRequest;

/** 结账报价的 tax 计算边界：事务外调用，结果不可变并由 tax 按订单 + revision 保存。 */
public interface CheckoutTaxPort {
    /**
     * 按订单与 revision 计算一次税务报价
     * <li>同一订单 + revision 内容相同返回原计算；BLOCKED 是正常结果，不抛异常、不以零税兜底。</li>
     *
     * @param request 订单身份、阶段、国家证据、计税主体与商品行
     * @return 已核对归属与金额结构的计算结果
     */
    TaxCalculation calculate(TaxQuoteRequest request);

    /**
     * 读取 tax 保存的某次计算冻结的计税主体
     * <li>只用于没有商业关系快照的订单（CLIENT 来源）在重算时取回建单冻结的合同业务模式；本库不保存该字段。</li>
     *
     * @param calculationId 本订单已有的计算 ID
     * @return 该计算冻结的业务模式、卖方与责任方
     */
    TaxParties frozenParties(long calculationId);
}
