package com.paytech.consumer.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.client.master.api.storedcard.response.ClientStoredCardSettingResponseBody;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/** client-master 已存卡开关 Mock（第 04 步合同）：只配置响应 DTO 与故障，记录被查询的 Client；未配置的查询记为意外。 */
@TestComponent
public class ClientMasterStoredCardSettingMocker implements Clear {
    /** 故障响应的 Request ID。 */
    public static final String REQUEST_ID = "client-master-test";

    private final Map<String, Object> settings = new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private int unexpected;

    /** 配置 Client 的开关。 */
    public synchronized void mockEnabled(long clientId, boolean enabled) {
        settings.put(Long.toString(clientId), new ClientStoredCardSettingResponseBody(Long.toString(clientId), enabled));
    }

    /** 配置读取故障或不合法响应。 */
    public synchronized void mockFailure(long clientId, MockHttpFault fault) {
        settings.put(Long.toString(clientId), fault);
    }

    /** 503 Problem。 */
    public static MockHttpFault unavailable() {
        return MockHttpFault.json(HttpStatus.SERVICE_UNAVAILABLE.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Service Unavailable", "合成故障", "SERVICE_UNAVAILABLE", REQUEST_ID, "2026-10-18T00:00:00.000000Z", null));
    }

    /** 记录并返回已配置的开关或故障。 */
    public synchronized ClientStoredCardSettingResponseBody answer(String clientId) {
        calls.add(clientId);
        var configured = settings.get(clientId);
        if (configured instanceof MockHttpFault fault) {
            throw fault;
        }
        if (configured == null) {
            unexpected++;
            throw MockHttpFault.json(HttpStatus.INTERNAL_SERVER_ERROR.value(), new ApiProblemResponse(
                ApiProblemProfile.TYPE, "Internal Server Error", "未配置的开关查询", "INTERNAL_ERROR", REQUEST_ID,
                "2026-10-18T00:00:00.000000Z", null));
        }
        return (ClientStoredCardSettingResponseBody)configured;
    }

    /** 被查询的 Client。 */
    public synchronized List<String> calls() {
        return List.copyOf(calls);
    }

    /** 全局 AfterEach：不允许出现未配置的开关查询。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 client-master 开关查询");
    }

    /** 每例清除配置与调用记录。 */
    @Override
    public synchronized void clear() {
        settings.clear();
        calls.clear();
        unexpected = 0;
    }
}
