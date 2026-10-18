package com.paytech.platform.support.test.base;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.platform.support.test.dal.NotificationTestMapper;
import com.paytech.platform.support.test.mocker.external.SesMocker;
import com.paytech.platform.support.test.notification.MockEmailProvider;
import com.paytech.platform.support.test.notification.NotificationFixture;
import com.paytech.platform.support.test.notification.NotificationVaultMock;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Client 认证邮件入口测试基类：认证 Processor 提交后经 Delivery 即时发送，真实 SES 适配器访问测试 SES 端点。
 * <li>类级准备只插入 SES 测试账号与两个认证模板，并在 Vault Mock 中放入合成发送凭据</li>
 * <li>指标在同一测试上下文中累计，用例按前后差值断言</li>
 */
public abstract class AuthenticationEmailTestBase extends OperationsApiTestBase {

    /** 与 application-mock.yml 的 notification.ses.credential-token 一致。 */
    protected static final String CREDENTIAL_TOKEN = "vlt_sesCredentialTokenForLoopbackMock000000";

    /** Vault 中的合成 IAM 密钥对。 */
    protected static final String VAULT_CREDENTIAL =
        "{\"accessKeyId\":\"AKIAVAULTSYNTHETIC01\",\"secretAccessKey\":\"VAULT-SECRET-MARKER\"}";

    /** 与 application-mock.yml 的 notification.ses.from-address 一致。 */
    protected static final String FROM_ADDRESS = "noreply@example.com";

    /** 与 application-mock.yml 的 client.authentication.public-origin 一致。 */
    protected static final String PUBLIC_ORIGIN = "https://client.example.test";

    /** 认证邮件投递指标。 */
    protected static final String METRIC = "client.authentication.email.delivery";

    private static final Pattern LINK = Pattern.compile("challengeNo=([0-9a-f-]{36})&secret=([A-Za-z0-9_-]{43})");

    @Autowired protected NotificationFixture fixture;
    @Autowired protected NotificationVaultMock vault;
    @Autowired protected MockEmailProvider provider;
    @Autowired protected NotificationTestMapper notificationData;
    @Autowired protected MeterRegistry meters;

    /** 父类全局初始化后插入认证模板与发送凭据。 */
    @BeforeEach
    void setUpAuthenticationEmail() {
        fixture.prepareAuthentication();
        vault.allowCredential(CREDENTIAL_TOKEN, VAULT_CREDENTIAL);
    }

    /** 即时发送没有后台任务；只检查 Vault Mock 与测试 Provider 的意外调用。 */
    @AfterEach
    void checkAuthenticationEmailCalls() {
        assertAll(vault::assertExpected, provider::assertExpected);
    }

    /** 把注册验证模板改路由到测试 Mock 账号，用于构造 SES 不会返回的供应商结果。 */
    protected void routeVerificationToMockProvider() {
        notificationData.provider();
        notificationData.useMockProviderForVerification();
    }

    /** 本例唯一一次 SES 发送请求。 */
    protected SesMocker.Received onlySesRequest() {
        assertEquals(1, ses.requests().size(), "只发送一次 SES 请求");
        return ses.requests().getFirst();
    }

    /** 邮件正文中的查找编号与秘密。 */
    protected static String[] link(SesMocker.Received request) {
        var matcher = LINK.matcher(request.text());
        assertTrue(matcher.find(), "邮件中没有认证链接");
        return new String[] {matcher.group(1), matcher.group(2)};
    }

    /** 即时发送不产生任何通知持久化事实。 */
    protected void assertNothingPersisted() {
        assertEquals(0, notificationData.persistedNotificationCount(), "认证邮件不写通知表与补偿任务");
    }

    /** 当前累计的成功次数。 */
    protected double accepted() {
        Counter counter = meters.find(METRIC).tag("result", "accepted").counter();
        return counter == null ? 0 : counter.count();
    }

    /** 当前累计的某原因失败次数。 */
    protected double failed(String reason) {
        Counter counter = meters.find(METRIC).tag("result", "failed").tag("reason", reason).counter();
        return counter == null ? 0 : counter.count();
    }
}
