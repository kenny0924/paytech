package com.paytech.payment.integration.risk;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.payment.integration.risk.request.PaymentRiskAssessmentRequestBody;
import com.paytech.payment.integration.risk.response.PaymentRiskAssessmentResponseBody;

/**
 * Risk 支付评估合同草案（PayerMax 卡直付对接技术方案第 6 节）：Risk 尚未提供支付评估接口，此处为 Payment 侧草案。
 * <li>Risk 实现并在 risk-api 发布合同后，Payment 改为依赖该合同并删除本草案。</li>
 */
@HttpExchange(url = "/risk-assessment/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface PaymentRiskAssessmentApi {

    /**
     * 评估一次支付尝试
     *
     * @param body 评估输入
     * @return 评估结论
     *         <li>httpCode=200 表示已评估；其他状态视为风控不可用，调用方失败关闭。</li>
     */
    // TODO by CLAUDE: 待 Risk Owner 确认支付评估的路径、字段与幂等身份后迁移到 risk-api。
    @PostExchange("/payment-assessments")
    PaymentRiskAssessmentResponseBody assess(@RequestBody PaymentRiskAssessmentRequestBody body);
}
