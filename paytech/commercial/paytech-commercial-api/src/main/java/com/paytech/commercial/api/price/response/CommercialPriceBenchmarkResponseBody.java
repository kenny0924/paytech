package com.paytech.commercial.api.price.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 合同价格组件与来源模板同组件的比较
 * <li>费率 / 固定额 / 最低收费 / 最高收费任一低于模板值即 BELOW_STANDARD；模板有最低收费而合同为空也算，最高收费为空不算。</li>
 * <li>阶梯：任一档费率或固定额低于模板同档为 BELOW_STANDARD；档位结构不同为 NO_BENCHMARK。</li>
 */
@Getter
@Setter
public class CommercialPriceBenchmarkResponseBody {
    /** BELOW_STANDARD、AT_OR_ABOVE、NO_BENCHMARK */
    private String status;

    /** 模板费率，没有为空 */
    private String standardRate;

    /** 模板固定金额，没有为空 */
    private String standardFixedAmount;

    /** 模板最低收费，没有为空 */
    private String standardMinAmount;

    /** 模板最高收费，没有为空 */
    private String standardMaxAmount;
}
