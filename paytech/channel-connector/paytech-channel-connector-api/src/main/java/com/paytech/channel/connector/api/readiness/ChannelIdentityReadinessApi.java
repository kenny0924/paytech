package com.paytech.channel.connector.api.readiness;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.channel.connector.api.readiness.request.ChannelIdentityReadinessRequestBody;
import com.paytech.channel.connector.api.readiness.response.ChannelIdentityReadinessResponseBody;

import jakarta.validation.Valid;

/**
 * 渠道身份就绪查询（服务发现名 {@code channel-connector-service}，无 context path；服务间只读调用，不经 Gateway）
 * <li>与运行期 MID 候选查询同一口径：MID、渠道账号、渠道均 ACTIVE，ENABLED 能力行支付方式与 MCC 精确、币种精确或 *，
 * 并在某个体系 / 消费者国家取值下声明 ACTION.PAY 或 ACTION.AUTHORIZE 为 true；门禁时点未知的体系与国家任一取值命中即可。</li>
 * <li>POST 只因批量入参，接口无副作用，不保存结论。配置损坏（悬空关联、能力冲突或未注册）返回 500；请求校验失败返回 422。</li>
 */
@HttpExchange("/channel-connector/v1")
public interface ChannelIdentityReadinessApi {
    /**
     * 判定一个 OE 下多组支付方式 × 币种 × MCC 的渠道身份是否就绪
     *
     * @param requestBody OE 与 1–200 组待判定的支付方式、币种、MCC
     * @return 按请求顺序逐组回显入参与结论；同组重复返回同一结论
     */
    @PostExchange("/channel-identities/readiness")
    ChannelIdentityReadinessResponseBody checkReadiness(@Valid @RequestBody ChannelIdentityReadinessRequestBody requestBody);
}
