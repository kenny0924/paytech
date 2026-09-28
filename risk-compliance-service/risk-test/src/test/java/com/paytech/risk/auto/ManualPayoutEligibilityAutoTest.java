package com.paytech.risk.auto;

import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

/**
 * 通过真实 HTTP 验证 RCO-I09 本地联调实现的输入矩阵与幂等边界。
 */
@ActiveProfiles({"auto", "local-evaluation-bootstrap"})
class ManualPayoutEligibilityAutoTest extends RiskServiceAutoTestBase {

    private static final String CLIENT_CREATE_HASH = "22f478943a9622e52a408d8234b306b6dbbfb41304187039dd9752d09b4356fb";
    private static final String PARTNER_APPROVAL_HASH = "af45f2041ff238b156cb5afb0308b36039854a9c363a26b6514454728b171b2a";

    @Test
    void test_clientEvaluationReturnsStablePairAndMinimumRequirement() throws Exception {
        String body = request("cmd-create-1", "PAYOUT_CREATION", "CLIENT", "PAYOUT", 1);
        HttpResponse<String> first = post(body);
        HttpResponse<String> replay = post(body);

        Assertions.assertEquals(200, first.statusCode());
        Assertions.assertEquals(first.body(), replay.body());
        Assertions.assertTrue(first.body().contains("\"decision\":\"ALLOW\""));
        Assertions.assertTrue(first.body().contains("\"requiredAmount\":\"0\""));
        Assertions.assertTrue(first.body().contains("\"requiredCurrency\":\"AED\""));
        Assertions.assertTrue(first.body().contains("\"controlSetHash\":\""));
    }

    @Test
    void test_differentCommandCreatesNewImmutablePair() throws Exception {
        HttpResponse<String> first = post(request("cmd-create-a", "PAYOUT_CREATION", "CLIENT", "PAYOUT", 1));
        HttpResponse<String> second = post(request("cmd-create-b", "PAYOUT_CREATION", "CLIENT", "PAYOUT", 1));

        Assertions.assertEquals(200, first.statusCode());
        Assertions.assertEquals(200, second.statusCode());
        Assertions.assertNotEquals(first.body(), second.body());
    }

    @Test
    void test_localPartnerDoesNotReturnClientDepositRequirement() throws Exception {
        String body = request("cmd-partner-1", "PAYOUT_FINAL_APPROVAL", "LOCAL_PARTNER", "PAYOUT", 8);
        HttpResponse<String> response = post(body);

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.body().contains("\"requiredAmount\":null"));
        Assertions.assertTrue(response.body().contains("\"requiredCurrency\":null"));
    }

    @Test
    void test_checkpointSourceMismatchFailsClosed() throws Exception {
        HttpResponse<String> response = post(
            request("cmd-invalid-source", "PAYOUT_CREATION", "CLIENT", "PAYOUT_EXECUTION", 1));

        RiskDecisionHttpClient.assertProblem(response, 400, "INVALID_REQUEST");
    }

    @Test
    void test_creationVersionOtherThanOneIsRejected() throws Exception {
        HttpResponse<String> response = post(
            request("cmd-invalid-version", "PAYOUT_EXECUTION_CREATION", "CLIENT", "PAYOUT_EXECUTION", 2));

        RiskDecisionHttpClient.assertProblem(response, 400, "INVALID_REQUEST");
    }

    @Test
    void test_inputHashMismatchIsRejected() throws Exception {
        HttpResponse<String> response = post(
            request("cmd-invalid-hash", "PAYOUT_CREATION", "CLIENT", "PAYOUT", 1)
                .replace(CLIENT_CREATE_HASH, "a".repeat(64)));

        RiskDecisionHttpClient.assertProblem(response, 400, "INVALID_REQUEST");
    }

    @Test
    void test_sameCommandWithDifferentInputConflicts() throws Exception {
        HttpResponse<String> first = post(request("cmd-conflict", "PAYOUT_CREATION", "CLIENT", "PAYOUT", 1));
        HttpResponse<String> changed = post(request("cmd-conflict", "PAYOUT_CREATION", "CLIENT", "PAYOUT", 1)
            .replace("\"grossAmount\":\"102\"", "\"grossAmount\":\"103\"")
            .replace(CLIENT_CREATE_HASH, "74f820cbfca3cd9c05734fb7dd4f9ae160ad6633123e03415d9f285ee9f86467"));

        Assertions.assertEquals(200, first.statusCode());
        RiskDecisionHttpClient.assertProblem(changed, 409, "CONCURRENT_REQUEST");
    }

    private String request(String commandKey, String checkpoint, String roleType, String sourceType,
        int sourceVersion) {
        String inputHash = roleType.equals("LOCAL_PARTNER") ? PARTNER_APPROVAL_HASH : CLIENT_CREATE_HASH;
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
