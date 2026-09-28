package com.paytech.risk.auto;

import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 验证默认环境没有规则与持久化时不会返回 ALLOW。
 */
class ManualPayoutEligibilityFailClosedAutoTest extends RiskServiceAutoTestBase {

    @Test
    void test_defaultProfileFailsClosed() throws Exception {
        String body = """
            {"commandKey":"cmd-default","checkpointType":"PAYOUT_CREATION","roleType":"CLIENT",
             "roleId":"40001","operatingEntityId":"50001","grossAmount":"102","currency":"AED",
             "bankCountry":"AE","sourceFactType":"PAYOUT","sourceFactId":"92001","sourceFactVersion":1,
             "inputHash":"%s"}
            """.formatted("22f478943a9622e52a408d8234b306b6dbbfb41304187039dd9752d09b4356fb");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/manual-payout-eligibility/v1/evaluations"))
            .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            RiskDecisionHttpClient.assertProblem(response, 503, "SERVICE_UNAVAILABLE");
        }
    }
}
