package com.paytech.risk.api.evaluation.request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 结账风控点（CHECKOUT_PREPARE，输入合同 ORDER_PREPARATION v5）的调用方输入
 * <li>由 Commerce 在创建结账、订单落库前构造，经 {@link #toRequest(String)} 生成统一评估请求；业务身份为 Commerce 预分配的订单 ID。</li>
 * <li>ID 为正整数十进制字符串，金额为订单币种主单位十进制字符串；可选字段未知时传 null，请求中省略。</li>
 * <li>结账评估没有附加输出；结论只有 ALLOW、REVIEW、DENY、FAILED、TIMED_OUT。</li>
 *
 * @param clientId Client ID
 * @param entityNo Operating Entity 编号，5 位大写字母或数字
 * @param entitySourceType 经营主体来源：PLATFORM、CLIENT 或 PARTNER
 * @param localPartnerId Local Partner ID；仅 PARTNER 来源必填，其他来源为空
 * @param consumerCountryCode 订单消费者国家 ISO 3166-1 alpha-2
 * @param currency ISO 4217 币种
 * @param merchandiseAmount 优惠前商品总额
 * @param discountAmount 商品优惠总额，无折扣为 0
 * @param netAmount 税前商品净额
 * @param industryCode 行业编码
 * @param mccCode 4 位 MCC
 * @param lineCount 订单商品明细行数
 */
public record CheckoutPrepareRiskInput(String clientId, String entityNo, String entitySourceType, String localPartnerId,
    String consumerCountryCode, String currency, String merchandiseAmount, String discountAmount, String netAmount,
    String industryCode, String mccCode, String lineCount) {

    /** 风控点编码。 */
    public static final String POINT_CODE = "CHECKOUT_PREPARE";

    /** 输入合同版本。 */
    public static final int CONTRACT_VERSION = 5;

    /**
     * 生成统一评估请求
     *
     * @param orderId Commerce 预分配的订单 ID，作为业务身份
     * @return 风控点、合同版本、业务身份与非空输入字段
     */
    public EvaluateDecisionRequestBody toRequest(String orderId) {
        EvaluateDecisionRequestBody body = new EvaluateDecisionRequestBody();
        body.setPointCode(POINT_CODE);
        body.setContractVersion(CONTRACT_VERSION);
        body.setBusinessKey(orderId);
        body.setInputs(inputs());
        return body;
    }

    /** 按合同字段编码组装输入，省略空值。 */
    public Map<String, String> inputs() {
        Map<String, String> values = new LinkedHashMap<>();
        put(values, "clientId", clientId);
        put(values, "entityNo", entityNo);
        put(values, "entitySourceType", entitySourceType);
        put(values, "localPartnerId", localPartnerId);
        put(values, "consumerCountryCode", consumerCountryCode);
        put(values, "currency", currency);
        put(values, "merchandiseAmount", merchandiseAmount);
        put(values, "discountAmount", discountAmount);
        put(values, "netAmount", netAmount);
        put(values, "industryCode", industryCode);
        put(values, "mccCode", mccCode);
        put(values, "lineCount", lineCount);
        return values;
    }

    /** 只放入非空值。 */
    private static void put(Map<String, String> values, String key, String value) {
        if (value != null) {
            values.put(key, value);
        }
    }
}
