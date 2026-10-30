package com.paytech.commercial.test.mocker.clientmaster;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.client.master.api.classification.response.ClientBusinessClassificationResponseBody;
import com.paytech.foundation.testsupport.api.Clear;

/**
 * client-master Client 经营分类接口的 DTO 配置与调用记录
 * <li>按 Client ID 配置已确认主分类或未确认；未配置的 Client 记为意外调用并使请求失败。</li>
 * <li>也可配置断连；每次请求记录 Client ID。</li>
 */
@TestComponent
public class ClientClassificationMocker implements Clear {
    /** Mock 响应的固定 Request ID。 */
    public static final String REQUEST_ID = "commercial-client-classification-test";

    private final Map<String, ClientBusinessClassificationResponseBody> classifications = new LinkedHashMap<>();
    private boolean unavailable;
    private final List<String> calls = new CopyOnWriteArrayList<>();

    /** Client 分类已确认，主分类 MCC 为给定值。 */
    public synchronized void mockConfirmed(long clientId, String mccCode) {
        classifications.put(Long.toString(clientId), response(clientId, true, "SOFTWARE", mccCode));
    }

    /** Client 分类未确认（或没有经营资料）。 */
    public synchronized void mockUnconfirmed(long clientId) {
        classifications.put(Long.toString(clientId), response(clientId, false, null, null));
    }

    /** client-master 断连。 */
    public synchronized void mockUnavailable() {
        unavailable = true;
    }

    /**
     * 由 Mock Controller 调用：记录请求并返回配置的 DTO；断连返回 Boolean.FALSE，未配置返回 null
     *
     * @param clientId 路径中的 Client ID
     * @return DTO、Boolean.FALSE 或 null
     */
    public synchronized Object classification(String clientId) {
        calls.add(clientId);
        if (unavailable) {
            return Boolean.FALSE;
        }
        return classifications.get(clientId);
    }

    /** 经营分类请求次数。 */
    public int count() {
        return calls.size();
    }

    /** 一条响应。 */
    private static ClientBusinessClassificationResponseBody response(long clientId, boolean confirmed, String industryCode,
        String mccCode) {
        var response = new ClientBusinessClassificationResponseBody();
        response.setClientId(Long.toString(clientId));
        response.setConfirmed(confirmed);
        response.setIndustryCode(industryCode);
        response.setMccCode(mccCode);
        return response;
    }

    /** 每例清空配置与记录。 */
    @Override
    public synchronized void clear() {
        classifications.clear();
        unavailable = false;
        calls.clear();
    }
}
