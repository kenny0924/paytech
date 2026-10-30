package com.paytech.channel.connector.ext.rest.converter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.readiness.request.ChannelIdentityReadinessItemBody;
import com.paytech.channel.connector.api.readiness.request.ChannelIdentityReadinessRequestBody;
import com.paytech.channel.connector.api.readiness.response.ChannelIdentityReadinessResponseBody;
import com.paytech.channel.connector.api.readiness.response.ChannelIdentityReadinessResultBody;
import com.paytech.channel.connector.domain.ChannelIdentityReadiness;
import com.paytech.channel.connector.service.processor.request.ChannelIdentityReadinessItem;
import com.paytech.channel.connector.service.processor.request.QueryChannelIdentityReadinessRequest;

/** 渠道身份就绪查询协议转换：原因以稳定 Code 字符串返回。 */
@Component
public class ChannelIdentityReadinessConverter {
    /** 转换请求。 */
    public QueryChannelIdentityReadinessRequest convertRequest(ChannelIdentityReadinessRequestBody body) {
        var request = new QueryChannelIdentityReadinessRequest();
        request.setEntityNo(body.getEntityNo());
        request.setItems(body.getItems().stream().map(ChannelIdentityReadinessConverter::item).toList());
        return request;
    }

    /** 转换响应。 */
    public ChannelIdentityReadinessResponseBody convertResponse(String entityNo, List<ChannelIdentityReadiness> results) {
        var body = new ChannelIdentityReadinessResponseBody();
        body.setEntityNo(entityNo);
        body.setItems(results.stream().map(ChannelIdentityReadinessConverter::result).toList());
        return body;
    }

    /** 单组请求。 */
    static ChannelIdentityReadinessItem item(ChannelIdentityReadinessItemBody body) {
        var item = new ChannelIdentityReadinessItem();
        item.setPaymentMethodCode(body.getPaymentMethodCode());
        item.setCurrency(body.getCurrency());
        item.setMccCode(body.getMccCode());
        return item;
    }

    /** 单组结论。 */
    static ChannelIdentityReadinessResultBody result(ChannelIdentityReadiness readiness) {
        var body = new ChannelIdentityReadinessResultBody();
        body.setPaymentMethodCode(readiness.getItem().getPaymentMethodCode());
        body.setCurrency(readiness.getItem().getCurrency());
        body.setMccCode(readiness.getItem().getMccCode());
        body.setReady(readiness.ready());
        body.setReasonCode(readiness.ready() ? null : readiness.getReason().getCode());
        return body;
    }
}
