package com.paytech.risk.decision.domain.runtime;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.control.domain.list.RiskSubjectKeys;
import com.paytech.risk.control.domain.list.RiskSubjectType;

/**
 * 一次结账评估（CHECKOUT_PREPARE，ORDER_PREPARATION v5）的冻结输入；字段格式已在 HTTP 入口校验
 * <li>经营主体来源为 PARTNER 时必须有 Local Partner，其他来源必须没有；违反时以参数错误拒绝，不产生评估。</li>
 * <li>实时累计与名单命中的主体键只有 Client 与经营主体，其他主体为空；累计金额为税前净额，币种为订单币种。</li>
 *
 * @param orderId
 *            Commerce 预分配的订单 ID，业务唯一身份
 * @param clientId
 *            Client ID
 * @param entityNo
 *            Operating Entity 编号
 * @param entitySourceType
 *            经营主体来源：PLATFORM、CLIENT 或 PARTNER
 * @param localPartnerId
 *            Local Partner ID；仅 PARTNER 来源非空
 * @param consumerCountryCode
 *            订单消费者国家
 * @param currency
 *            订单币种
 * @param merchandiseAmount
 *            优惠前商品总额，币种主单位
 * @param discountAmount
 *            商品优惠总额，币种主单位
 * @param netAmount
 *            税前商品净额，币种主单位
 * @param industryCode
 *            行业代码
 * @param mccCode
 *            四位 MCC
 * @param lineCount
 *            订单商品明细行数
 */
public record CheckoutRiskInput(long orderId, long clientId, String entityNo, String entitySourceType,
    Long localPartnerId, String consumerCountryCode, String currency, BigDecimal merchandiseAmount,
    BigDecimal discountAmount, BigDecimal netAmount, String industryCode, String mccCode, int lineCount)
    implements RuntimeRiskInput {

    /** PARTNER 来源的经营主体。 */
    public static final String PARTNER_SOURCE = "PARTNER";

    /** 经营主体来源与 Local Partner 必须一致；在读取发布与写入评估之前校验。 */
    public CheckoutRiskInput {
        if (PARTNER_SOURCE.equals(entitySourceType) != (localPartnerId != null)) {
            throw new InvalidParameterException("Local Partner 只能且必须随 PARTNER 来源的经营主体提供: " + orderId);
        }
    }

    /**
     * 结账输入合同字段的规范值
     *
     * @return 字段编码到规范值；金额与行数为去尾零的十进制文本，没有 Local Partner 时不出现该字段
     */
    @Override
    public Map<String, Object> contractValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("client_id", Long.toString(clientId));
        values.put("entity_no", entityNo);
        values.put("entity_source_type", entitySourceType);
        if (localPartnerId != null) {
            values.put("local_partner_id", Long.toString(localPartnerId));
        }
        values.put("consumer_country_code", consumerCountryCode);
        values.put("currency", currency);
        values.put("merchandise_amount", plain(merchandiseAmount));
        values.put("discount_amount", plain(discountAmount));
        values.put("net_amount", plain(netAmount));
        values.put("industry_code", industryCode);
        values.put("mcc_code", mccCode);
        values.put("line_count", Integer.toString(lineCount));
        return values;
    }

    /**
     * 主体在本次结账中的规范化主体键
     *
     * @param type 主体类型
     * @return Client 与经营主体的规范化主体键；结账没有其他主体，返回空
     */
    @Override
    public String subjectKey(RiskSubjectType type) {
        return switch (type) {
            case CLIENT -> RiskSubjectKeys.normalize(type, Long.toString(clientId));
            case OPERATING_ENTITY -> RiskSubjectKeys.normalize(type, entityNo);
            default -> null;
        };
    }

    /** 本次评估的金额取税前商品净额。 */
    @Override
    public BigDecimal amount() {
        return netAmount;
    }

    /**
     * 金额的规范文本
     *
     * @param amount 金额
     * @return 去尾零的普通十进制文本
     */
    public static String plain(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
