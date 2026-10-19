package com.paytech.commercial.api.price.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 不可变价格对象：父链、画像、条件、组件、档位与结算（stage3 §2.3，technique.md §2.4 修订）
 * <li>同一对象用于命中结果、按 priceId 回读与固定版本价格全集；不存在的属性显式为 null。</li>
 * <li>金额与费率为十进制字符串；金额按币种精度补齐小数位。</li>
 */
@Getter
@Setter
public class CommercialPriceResponseBody {
    /** 条款类型 */
    private String termType;

    /** 版本冻结的合同 ID */
    private String agreementId;

    /** 方案 ID */
    private String planId;

    /** 版本 ID */
    private String termVersionId;

    /** 准确 OE */
    private String entityNo;

    /** Client Owner；Partner 条款为空 */
    private String clientId;

    /** Local Partner Owner；Client 条款为空 */
    private String localPartnerId;

    /** 价格 ID */
    private String priceId;

    /** 定价画像代码 */
    private String pricingProfileCode;

    /** 画像绑定的 Payment Method；非支付方式画像为空 */
    private String paymentMethodCode;

    /** 同 OE + 币种内的优先级 */
    private Integer priorityNo;

    /** 交易币种即费用币种 */
    private String currency;

    /** 版本定价模型 */
    private String pricingModel;

    /** 版本费用含税口径 */
    private String feeTaxTreatment;

    /** 画像条件，按 dimensionCode、conditionValue 排序 */
    private List<CommercialPriceConditionResponseBody> conditions;

    /** 计算组件，按 componentNo 排序 */
    private List<CommercialPriceComponentResponseBody> components;

    /** Client 条款的结算四字段；Partner 条款为 null */
    private CommercialPriceSettlementResponseBody settlement;
}
