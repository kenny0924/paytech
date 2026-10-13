package com.paytech.risk.auto;

import com.paytech.risk.api.evaluation.ManualPayoutEligibilityApi;
import com.paytech.risk.api.evaluation.request.ManualPayoutEligibilityRequestBody;
import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 通过真实 HTTP 验证 RCO-I09 输入合同校验，以及评估链接入前合法请求一律 Fail-Closed。
 *
 * @see ManualPayoutEligibilityApi#evaluate(ManualPayoutEligibilityRequestBody)
 */
class ManualPayoutEligibilityAutoTest extends RiskServiceAutoTestBase {

    private static final String CLIENT_CREATE_HASH = "22f478943a9622e52a408d8234b306b6dbbfb41304187039dd9752d09b4356fb";
    private static final String PARTNER_APPROVAL_HASH = "af45f2041ff238b156cb5afb0308b36039854a9c363a26b6514454728b171b2a";

    @Test
    void test_validClientRequestFailsClosed() throws Exception {
        HttpResponse<String> response = post(request("cmd-client", "PAYOUT_CREATION", "CLIENT", "PAYOUT", 1,
            CLIENT_CREATE_HASH));

        RiskDecisionHttpClient.assertProblem(response, 503, "SERVICE_UNAVAILABLE");
    }

    @Test
    void test_validLocalPartnerRequestFailsClosed() throws Exception {
        HttpResponse<String> response = post(request("cmd-partner", "PAYOUT_FINAL_APPROVAL", "LOCAL_PARTNER", "PAYOUT",
            8, PARTNER_APPROVAL_HASH));

        RiskDecisionHttpClient.assertProblem(response, 503, "SERVICE_UNAVAILABLE");
    }

    @Test
    void test_checkpointSourceMismatchIsRejected() throws Exception {
        HttpResponse<String> response = post(request("cmd-invalid-source", "PAYOUT_CREATION", "CLIENT",
            "PAYOUT_EXECUTION", 1, CLIENT_CREATE_HASH));

        RiskDecisionHttpClient.assertProblem(response, 400, "INVALID_REQUEST");
    }

    @Test
    void test_creationVersionOtherThanOneIsRejected() throws Exception {
        HttpResponse<String> response = post(request("cmd-invalid-version", "PAYOUT_EXECUTION_CREATION", "CLIENT",
            "PAYOUT_EXECUTION", 2, CLIENT_CREATE_HASH));

        RiskDecisionHttpClient.assertProblem(response, 400, "INVALID_REQUEST");
    }

    @Test
    void test_unknownRoleTypeIsRejected() throws Exception {
        HttpResponse<String> response = post(request("cmd-invalid-role", "PAYOUT_CREATION", "MERCHANT", "PAYOUT", 1,
            CLIENT_CREATE_HASH));

        RiskDecisionHttpClient.assertProblem(response, 400, "INVALID_REQUEST");
    }

    @Test
    void test_inputHashMismatchIsRejected() throws Exception {
        HttpResponse<String> response = post(request("cmd-invalid-hash", "PAYOUT_CREATION", "CLIENT", "PAYOUT", 1,
            "a".repeat(64)));

        RiskDecisionHttpClient.assertProblem(response, 400, "INVALID_REQUEST");
    }

    private String request(String commandKey, String checkpoint, String roleType, String sourceType,
        int sourceVersion, String inputHash) {
        return """
            {"commandKey":"%s","checkpointType":"%s","roleType":"%s","roleId":"40001",
             "operatingEntityId":"50001","grossAmount":"102","currency":"AED","bankCountry":"AE",
             "sourceFactType":"%s","sourceFactId":"92001","sourceFactVersion":%d,"inputHash":"%s"}
            """.formatted(commandKey, checkpoint, roleType, sourceType, sourceVersion, inputHash);
    }

    private HttpResponse<String> post(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/manual-payout-eligibility/v1/evaluations"))
            .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
