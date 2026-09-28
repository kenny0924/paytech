package com.paytech.platform.support.auto.ext.http.apicredential.validate;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;
import com.paytech.platform.support.api.apicredential.ApiCredentialValidationApi;
import com.paytech.platform.support.test.base.ApiCredentialValidationTestBase;
import com.paytech.platform.support.test.builder.CredentialFixture;
import com.paytech.platform.support.test.builder.CredentialScopeFixture;
import com.paytech.platform.support.test.checker.ApiCredentialDatabaseChecker;
import com.paytech.platform.support.test.checker.ApiCredentialResponseChecker;

/**
 * 认证过程中并发提交新的授权，仍只能返回同一数据库快照中的身份与 Scope。
 *
 * @see ApiCredentialValidationApi#validate(String)
 */
class ApiCredentialValidationConsistencyTest extends ApiCredentialValidationTestBase {

    @Autowired
    private ApiCredentialDatabaseChecker databaseChecker;

    private CredentialFixture fixture;

    /** 每例只有一个原始授权，不共享可变状态。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = credentialMocker.create(CredentialFixture.template().build());
        credentialMocker.scope(fixture, CredentialScopeFixture.template("orders.read").build());
    }

    /** 第一次候选查询后插入并提交新 Scope，当前请求不可把新授权拼进旧快照。 */
    @Test
    void test_concurrentScopeCommitDoesNotMixSnapshots() throws Exception {
        String requestId = "plt-i03-" + UUID.randomUUID();
        credentialMocker.registerRequest(requestId);
        var headers = new HttpHeaders();
        headers.set("X-Request-Id", requestId);
        headers.set(ApiCredentialConstraints.CREDENTIAL_HEADER, fixture.credential());
        int position = credentialQueryObserver.position();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var gate = credentialQueryObserver.pauseAfterCandidate();
            var request = executor.submit(() -> httpTestInvoker.post("/api-credentials/v1/validate", headers, null));
            try (gate) {
                gate.awaitCandidate();
                credentialMocker.scope(fixture, CredentialScopeFixture.template("payments.read").build());
            }
            var afterCommit = databaseChecker.snapshot();
            var response = request.get(5, TimeUnit.SECONDS);
            new ApiCredentialResponseChecker(response, objectMapper, requestId).assertIdentity(fixture, "orders.read");
            credentialQueryObserver.assertSnapshotSince(position);
            databaseChecker.assertUnchanged(afterCommit, requestId);
        }
        invoke(fixture.credential()).assertIdentity(fixture, "orders.read", "payments.read");
    }
}
