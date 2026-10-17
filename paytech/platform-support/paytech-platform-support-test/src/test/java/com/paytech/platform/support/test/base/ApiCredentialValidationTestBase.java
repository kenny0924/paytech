package com.paytech.platform.support.test.base;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;

import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;
import com.paytech.platform.support.test.checker.ApiCredentialDatabaseChecker;
import com.paytech.platform.support.test.checker.ApiCredentialResponseChecker;

/** PLT-I03 真实 HTTP 调用和只读断言，共享启动成本但不共享用例数据。 */
@ExtendWith(OutputCaptureExtension.class)
public abstract class ApiCredentialValidationTestBase extends MockTestBase {

    @Autowired
    private ApiCredentialDatabaseChecker databaseChecker;

    /** Header 缺失仍原样发送；请求前后检查持久化事实，错误也不能产生写入。 */
    protected ApiCredentialResponseChecker invoke(String credential) {
        var headers = new HttpHeaders();
        if (credential != null) {
            headers.add(ApiCredentialConstraints.CREDENTIAL_HEADER, credential);
        }
        return invoke(headers);
    }

    /** 保留重复 Header 等非法输入，不通过测试调用器修复请求。 */
    protected ApiCredentialResponseChecker invoke(HttpHeaders headers) {
        String requestId = "plt-i03-" + UUID.randomUUID();
        credentialMocker.registerRequest(requestId);
        headers.getOrEmpty(ApiCredentialConstraints.CREDENTIAL_HEADER).forEach(credentialMocker::protectInput);
        headers.set("X-Request-Id", requestId);
        var before = databaseChecker.snapshot();
        int position = credentialQueryObserver.position();
        var result = httpTestInvoker.post("/api-credentials/v1/validate", headers, null);
        databaseChecker.assertUnchanged(before, requestId);
        if (result.getStatus().value() == 200) {
            credentialQueryObserver.assertSnapshotSince(position);
        }
        credentialMocker.assertNoSecrets(new String(result.getResponseBody(), StandardCharsets.UTF_8));
        return new ApiCredentialResponseChecker(result, objectMapper, requestId);
    }

    /** HTTP 已完成且没有异步业务工作，清理前确认真实 MVC 日志未泄漏认证材料。 */
    @AfterEach
    protected final void assertSafeLogs(CapturedOutput output) {
        credentialMocker.assertNoSecrets(output.getAll());
    }
}
