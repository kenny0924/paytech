package com.paytech.commercial.integration.impl;

import static com.paytech.commercial.api.enums.CheckoutErrorCode.OWNER_UNAVAILABLE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import com.paytech.channel.connector.api.readiness.ChannelIdentityReadinessApi;
import com.paytech.channel.connector.api.readiness.request.ChannelIdentityReadinessItemBody;
import com.paytech.channel.connector.api.readiness.request.ChannelIdentityReadinessRequestBody;
import com.paytech.channel.connector.api.readiness.response.ChannelIdentityReadinessResponseBody;
import com.paytech.channel.connector.api.readiness.response.ChannelIdentityReadinessResultBody;
import com.paytech.commercial.domain.ChannelIdentityItem;
import com.paytech.commercial.domain.ChannelIdentityReadiness;
import com.paytech.commercial.integration.ChannelIdentityReadinessClient;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/**
 * 经 channel-connector-api 判定渠道身份就绪（REQ-02 technique.md §10.1）
 * <li>失败关闭：任何 4xx / 5xx（含配置损坏 500）、超时、无实例或响应不符合合同一律 OWNER_UNAVAILABLE，不降级、不重试；调用方记 ④ UNKNOWN。</li>
 * <li>每次请求至多 200 组，超出时按顺序分批；响应须按请求顺序逐组回显入参，ready 与原因码须一致（就绪无原因、不就绪有原因）。</li>
 */
@AllArgsConstructor
public class ChannelIdentityReadinessClientImpl implements ChannelIdentityReadinessClient {
    /** channel-connector 单次请求的组合上限。 */
    static final int BATCH_LIMIT = 200;

    private final ChannelIdentityReadinessApi api;

    /** 去重后按上限分批调用（通常一个批次）并合并结论。 */
    @Override
    public List<ChannelIdentityReadiness> checkReadiness(String entityNo, Collection<ChannelIdentityItem> items) {
        List<ChannelIdentityItem> distinct = List.copyOf(new LinkedHashSet<>(items));
        List<ChannelIdentityReadiness> results = new ArrayList<>();
        for (int from = 0; from < distinct.size(); from += BATCH_LIMIT) {
            results.addAll(checkBatch(entityNo, distinct.subList(from, Math.min(distinct.size(), from + BATCH_LIMIT))));
        }
        return results;
    }

    /** 一个批次的远程调用与解析。 */
    private List<ChannelIdentityReadiness> checkBatch(String entityNo, List<ChannelIdentityItem> batch) {
        ChannelIdentityReadinessResponseBody response;
        try {
            response = api.checkReadiness(request(entityNo, batch));
        } catch (RuntimeException failure) {
            throw new ErrorException(OWNER_UNAVAILABLE, "channel-connector 渠道身份就绪查询不可用", failure);
        }
        try {
            return parse(response, entityNo, batch);
        } catch (RuntimeException invalid) {
            throw new ErrorException(OWNER_UNAVAILABLE, "channel-connector 渠道身份就绪响应不符合合同", invalid);
        }
    }

    /** 请求体。 */
    static ChannelIdentityReadinessRequestBody request(String entityNo, List<ChannelIdentityItem> items) {
        var request = new ChannelIdentityReadinessRequestBody();
        request.setEntityNo(entityNo);
        request.setItems(items.stream().map(item -> {
            var body = new ChannelIdentityReadinessItemBody();
            body.setPaymentMethodCode(item.paymentMethodCode());
            body.setCurrency(item.currency());
            body.setMccCode(item.mccCode());
            return body;
        }).toList());
        return request;
    }

    /**
     * 响应须与请求逐组对应
     *
     * @param response channel-connector 响应
     * @param entityNo 请求的 OE
     * @param items 请求组合
     * @return 每组结论
     */
    static List<ChannelIdentityReadiness> parse(ChannelIdentityReadinessResponseBody response, String entityNo,
        List<ChannelIdentityItem> items) {
        List<ChannelIdentityReadinessResultBody> results = Objects.requireNonNull(response.getItems());
        if (!entityNo.equals(response.getEntityNo()) || results.size() != items.size()) {
            throw new IllegalArgumentException("就绪结论与请求不一致");
        }
        List<ChannelIdentityReadiness> parsed = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            parsed.add(readiness(items.get(index), results.get(index)));
        }
        return parsed;
    }

    /** 一组结论：回显入参一致，ready 与原因码一致。 */
    static ChannelIdentityReadiness readiness(ChannelIdentityItem item, ChannelIdentityReadinessResultBody result) {
        ChannelIdentityItem echoed = new ChannelIdentityItem(result.getPaymentMethodCode(), result.getCurrency(),
            result.getMccCode());
        if (!item.equals(echoed) || result.isReady() != (result.getReasonCode() == null)) {
            throw new IllegalArgumentException("就绪结论回显或原因不一致");
        }
        return new ChannelIdentityReadiness(item, result.isReady(), result.getReasonCode());
    }
}
