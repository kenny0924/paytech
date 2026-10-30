package com.paytech.commercial.test.mocker.channelconnector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.channel.connector.api.enums.ChannelIdentityReadinessReason;
import com.paytech.channel.connector.api.readiness.request.ChannelIdentityReadinessItemBody;
import com.paytech.channel.connector.api.readiness.request.ChannelIdentityReadinessRequestBody;
import com.paytech.channel.connector.api.readiness.response.ChannelIdentityReadinessResponseBody;
import com.paytech.channel.connector.api.readiness.response.ChannelIdentityReadinessResultBody;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;

/**
 * channel-connector 渠道身份就绪接口的 DTO 配置与调用记录
 * <li>按（OE, 支付方式, 币种, MCC）配置就绪或不就绪原因；请求中出现未配置的组合记为意外调用并使请求失败。</li>
 * <li>也可配置整次请求的 HTTP 故障或断连；每次请求记录 OE 与组合键，供用例断言调用次数与内容。</li>
 */
@TestComponent
public class ChannelIdentityReadinessMocker implements Clear {
    /** 一次 HTTP 故障或断连。 */
    public record Fault(int status, ApiProblemResponse problem, boolean disconnect) {
    }

    /** Mock 响应与 Problem 的固定 Request ID。 */
    public static final String REQUEST_ID = "commercial-channel-connector-test";

    /** 配置值：就绪。 */
    private static final String READY = "";

    private final Map<String, String> outcomes = new LinkedHashMap<>();
    private Fault fault;
    private final List<String> calls = new CopyOnWriteArrayList<>();

    /** 一个 OE 下某支付方式在若干币种、某 MCC 下就绪，覆盖已有配置。 */
    public synchronized void mockReady(String entityNo, String paymentMethodCode, String mccCode, String... currencies) {
        for (String currency : currencies) {
            outcomes.put(key(entityNo, paymentMethodCode, currency, mccCode), READY);
        }
    }

    /** 一个 OE 下某支付方式在若干币种、某 MCC 下不就绪，覆盖已有配置。 */
    public synchronized void mockNotReady(String entityNo, String paymentMethodCode, String mccCode,
        ChannelIdentityReadinessReason reason, String... currencies) {
        for (String currency : currencies) {
            outcomes.put(key(entityNo, paymentMethodCode, currency, mccCode), reason.getCode());
        }
    }

    /** channel-connector 返回第一方 Problem（例如配置损坏 500）。 */
    public synchronized void mockProblem(int status, String code) {
        fault = new Fault(status, new ApiProblemResponse(ApiProblemProfile.TYPE, HttpStatus.valueOf(status).getReasonPhrase(),
            "测试依赖拒绝", code, REQUEST_ID, "2026-10-30T00:00:00.000000Z", null), false);
    }

    /** channel-connector 断连。 */
    public synchronized void mockUnavailable() {
        fault = new Fault(0, null, true);
    }

    /** 清除故障，恢复按组合配置返回。 */
    public synchronized void mockRecovered() {
        fault = null;
    }

    /**
     * 由 Mock Controller 调用：记录请求并返回配置的故障、DTO 或 null（存在未配置组合）
     *
     * @param request 实际请求
     * @return Fault、响应 DTO 或 null
     */
    public synchronized Object check(ChannelIdentityReadinessRequestBody request) {
        List<String> keys = request.getItems().stream()
            .map(item -> key(request.getEntityNo(), item.getPaymentMethodCode(), item.getCurrency(), item.getMccCode()))
            .toList();
        calls.add(String.join(";", keys));
        if (fault != null) {
            return fault;
        }
        List<ChannelIdentityReadinessResultBody> results = new ArrayList<>();
        for (int index = 0; index < keys.size(); index++) {
            String outcome = outcomes.get(keys.get(index));
            if (outcome == null) {
                return null;
            }
            results.add(result(request.getItems().get(index), outcome));
        }
        var response = new ChannelIdentityReadinessResponseBody();
        response.setEntityNo(request.getEntityNo());
        response.setItems(results);
        return response;
    }

    /** 就绪查询请求次数。 */
    public int count() {
        return calls.size();
    }

    /** 全部请求，每次为分号连接的组合键（{@code entityNo|paymentMethod|currency|mcc}），按请求顺序。 */
    public List<String> requests() {
        return List.copyOf(calls);
    }

    /** 唯一一次请求的组合键。 */
    public List<String> onlyRequestItems() {
        assertEquals(1, calls.size(), "应只发起一次渠道身份就绪查询");
        return List.of(calls.getFirst().split(";"));
    }

    /** 组合键。 */
    public static String key(String entityNo, String paymentMethodCode, String currency, String mccCode) {
        return entityNo + "|" + paymentMethodCode + "|" + currency + "|" + mccCode;
    }

    /** 一组结论：回显入参。 */
    private static ChannelIdentityReadinessResultBody result(ChannelIdentityReadinessItemBody item, String outcome) {
        var result = new ChannelIdentityReadinessResultBody();
        result.setPaymentMethodCode(item.getPaymentMethodCode());
        result.setCurrency(item.getCurrency());
        result.setMccCode(item.getMccCode());
        result.setReady(READY.equals(outcome));
        result.setReasonCode(READY.equals(outcome) ? null : outcome);
        return result;
    }

    /** 每例清空配置与记录。 */
    @Override
    public synchronized void clear() {
        outcomes.clear();
        fault = null;
        calls.clear();
    }
}
