package com.paytech.platform.support.auto.ext.http.client;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.accesscontrol.domain.enums.ApiCredentialStatus;
import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;
import com.paytech.platform.support.api.apicredential.response.ValidatedCredentialResponseBody;
import com.paytech.platform.support.api.authorization.request.ClientAuthorizationRequestBody;
import com.paytech.platform.support.api.authorization.response.ClientAuthorizationResponseBody;
import com.paytech.platform.support.api.clientapicredential.request.CreateClientApiCredentialRequestBody;
import com.paytech.platform.support.api.clientapicredential.request.RevokeClientApiCredentialRequestBody;
import com.paytech.platform.support.api.clientapicredential.request.RotateClientApiCredentialRequestBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiCredentialResponseBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiCredentialSecretResponseBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiCredentialsResponseBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiScopesResponseBody;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.ClientDeveloperTestMapper;
import com.paytech.platform.support.test.dal.ConfigurationTestMapper;
import com.paytech.platform.support.test.mocker.local.ClientWorkspaceMocker;
import com.paytech.platform.support.test.mocker.local.ClientWorkspaceMocker.Workspace;

/**
 * 工作区成员自助管理 API 凭证，以及 CLIENT 权限判断：创建后可经 PLT-I03 认证，轮换宽限、撤销、上限、工作区隔离与权限。
 *
 * @see com.paytech.platform.support.api.clientapicredential.ClientApiCredentialApi
 * @see com.paytech.platform.support.api.authorization.ClientAuthorizationApi
 */
class ClientApiCredentialSelfServiceTest extends OperationsApiTestBase {

    private static final String BASE = "/client-api-credentials/v1";

    private static final String SCOPE_READ = "orders:read";

    private static final String SCOPE_WRITE = "orders:write";

    private static final String[] ALL = ClientDeveloperPermissions.OWNER_DEFAULTS.toArray(String[]::new);

    @Autowired
    ClientWorkspaceMocker workspaces;

    @Autowired
    ConfigurationTestMapper configurationData;

    @Autowired
    ClientDeveloperTestMapper developerData;

    /** 两个 Scope 目录项；Scope 表由整表回收。 */
    @BeforeEach
    void setUpScopes() {
        Instant now = operationsMocker.now();
        assertEquals(1, configurationData.insertApiScope(OperatorBuilder.nextId(), SCOPE_READ, "查询订单", null, now));
        assertEquals(1, configurationData.insertApiScope(OperatorBuilder.nextId(), SCOPE_WRITE, "创建订单", "下单", now));
    }

    /** 创建只返回一次密钥且不缓存；密钥可经 PLT-I03 认证为本工作区；列表与详情不含密钥，库中只有 PHC。 */
    @Test
    void test_normalFlow() {
        var owner = workspaces.owner(workspaces.tenant(), ALL);

        var scopes = get(owner, BASE + "/scopes").assertOk().as(ClientApiScopesResponseBody.class).getItems();
        assertEquals(List.of(SCOPE_READ, SCOPE_WRITE), scopes.stream().map(ClientApiScopesResponseBody.Item::getScopeCode).toList());

        var created = create(owner, " Production server ", List.of(SCOPE_READ), 90).assertStatus(201)
            .assertHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
            .as(ClientApiCredentialSecretResponseBody.class);

        assertTrue(created.getSecret().matches("ck-[A-Za-z0-9]{20}\\.[A-Za-z0-9_-]{43}"));
        var credential = created.getCredential();
        assertEquals("Production server", credential.getName());
        assertEquals(created.getSecret().substring(0, created.getSecret().indexOf('.')), credential.getKeyPrefix());
        assertEquals(ApiCredentialStatus.ACTIVE.getCode(), credential.getStatus());
        assertEquals(List.of(SCOPE_READ), credential.getScopeCodes());
        assertEquals(Duration.ofDays(90),
            Duration.between(Instant.parse(credential.getCreatedAt()), Instant.parse(credential.getExpiresAt())));
        var validated = validate(created.getSecret()).assertOk().as(ValidatedCredentialResponseBody.class);
        assertEquals(Long.toString(owner.tenantId()), validated.getTenantId());
        assertEquals(credential.getCredentialId(), validated.getCredentialId());
        assertEquals(List.of(SCOPE_READ), validated.getEffectiveScopeCodes());

        var list = get(owner, BASE + "/credentials").assertOk();
        assertFalse(list.text().contains(created.getSecret().substring(created.getSecret().indexOf('.') + 1)));
        assertEquals(List.of(credential.getCredentialId()), list.as(ClientApiCredentialsResponseBody.class).getItems()
            .stream().map(ClientApiCredentialResponseBody::getCredentialId).toList());
        assertEquals("Production server", get(owner, BASE + "/credentials/" + credential.getCredentialId()).assertOk()
            .as(ClientApiCredentialResponseBody.class).getName());
        var row = developerData.credentials(owner.tenantId()).getFirst();
        assertNotEquals(created.getSecret(), row.get("key_verifier_phc"));
        assertTrue(String.valueOf(row.get("key_verifier_phc")).startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));
    }

    /** 轮换：新凭证继承名称、Scope 与有效期；旧凭证宽限内仍可认证、截止后失败；宽限 0 立即撤销；版本不符 409。 */
    @Test
    void test_rotateWithGrace() {
        var owner = workspaces.owner(workspaces.tenant(), ALL);
        var first = create(owner, "Server", List.of(SCOPE_READ, SCOPE_WRITE), 30).assertStatus(201)
            .as(ClientApiCredentialSecretResponseBody.class);
        long firstId = Long.parseLong(first.getCredential().getCredentialId());

        rotate(owner, firstId, 9, 60).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        var second = rotate(owner, firstId, 1, 60).assertStatus(201)
            .assertHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
            .as(ClientApiCredentialSecretResponseBody.class);

        var replacement = second.getCredential();
        assertEquals("Server", replacement.getName());
        assertEquals(first.getCredential().getCredentialId(), replacement.getRotatedFromId());
        assertEquals(List.of(SCOPE_READ, SCOPE_WRITE), replacement.getScopeCodes());
        assertEquals(Duration.ofDays(30),
            Duration.between(Instant.parse(replacement.getCreatedAt()), Instant.parse(replacement.getExpiresAt())));
        validate(second.getSecret()).assertOk();
        validate(first.getSecret()).assertOk();
        var old = get(owner, BASE + "/credentials/" + firstId).assertOk().as(ClientApiCredentialResponseBody.class);
        assertEquals(Duration.ofMinutes(60),
            Duration.between(Instant.parse(replacement.getCreatedAt()), Instant.parse(old.getExpiresAt())));
        assertEquals(2, old.getVersion());
        assertEquals(1, developerData.expire(firstId, operationsMocker.now().minusSeconds(1)));
        validate(first.getSecret()).assertStatus(401);
        assertEquals(ApiCredentialStatus.EXPIRED.getCode(),
            get(owner, BASE + "/credentials/" + firstId).assertOk().as(ClientApiCredentialResponseBody.class).getStatus());
        rotate(owner, firstId, 2, 60).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        long secondId = Long.parseLong(replacement.getCredentialId());
        var third = rotate(owner, secondId, 1, 0).assertStatus(201).as(ClientApiCredentialSecretResponseBody.class);

        validate(second.getSecret()).assertStatus(401);
        validate(third.getSecret()).assertOk();
        assertEquals(ApiCredentialStatus.REVOKED.getCode(),
            get(owner, BASE + "/credentials/" + secondId).assertOk().as(ClientApiCredentialResponseBody.class).getStatus());
    }

    /** 撤销立即生效；再次撤销返回原结果；状态筛选按推导状态。 */
    @Test
    void test_revoke() {
        var owner = workspaces.owner(workspaces.tenant(), ALL);
        var created = create(owner, "Server", List.of(SCOPE_READ), 365).assertStatus(201)
            .as(ClientApiCredentialSecretResponseBody.class);
        long id = Long.parseLong(created.getCredential().getCredentialId());

        var revoked = revoke(owner, id, 1).assertOk().as(ClientApiCredentialResponseBody.class);

        assertEquals(ApiCredentialStatus.REVOKED.getCode(), revoked.getStatus());
        assertEquals(2, revoked.getVersion());
        validate(created.getSecret()).assertStatus(401);
        assertEquals(ApiCredentialStatus.REVOKED.getCode(),
            revoke(owner, id, 1).assertOk().as(ClientApiCredentialResponseBody.class).getStatus());
        assertEquals(1, get(owner, BASE + "/credentials?status=REVOKED").assertOk()
            .as(ClientApiCredentialsResponseBody.class).getItems().size());
        assertTrue(get(owner, BASE + "/credentials?status=ACTIVE").assertOk()
            .as(ClientApiCredentialsResponseBody.class).getItems().isEmpty());
    }

    /** 有效凭证最多 10 个；第 11 个被拒绝，轮换不受上限阻止。 */
    @Test
    void test_activeLimit() {
        var owner = workspaces.owner(workspaces.tenant(), ALL);
        ClientApiCredentialSecretResponseBody last = null;
        for (int index = 0; index < 10; index++) {
            last = create(owner, "Server " + index, List.of(SCOPE_READ), 30).assertStatus(201)
                .as(ClientApiCredentialSecretResponseBody.class);
        }

        create(owner, "Server 11", List.of(SCOPE_READ), 30).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        rotate(owner, Long.parseLong(last.getCredential().getCredentialId()), 1, 60).assertStatus(201);
        assertEquals(11, developerData.credentials(owner.tenantId()).size());
    }

    /** 其他工作区的凭证一律 404；账号上下文 403；只读成员不能写；无权限成员不能读。 */
    @Test
    void test_workspaceIsolationAndPermission() {
        var owner = workspaces.owner(workspaces.tenant(), ALL);
        var other = workspaces.owner(workspaces.tenant(), ALL);
        long foreign = Long.parseLong(create(other, "Other", List.of(SCOPE_READ), 30).assertStatus(201)
            .as(ClientApiCredentialSecretResponseBody.class).getCredential().getCredentialId());

        get(owner, BASE + "/credentials/" + foreign).assertProblem(404, CommonErrorCode.NOT_FOUND);
        get(owner, BASE + "/credentials/not-a-number").assertProblem(404, CommonErrorCode.NOT_FOUND);
        rotate(owner, foreign, 1, 60).assertProblem(404, CommonErrorCode.NOT_FOUND);
        revoke(owner, foreign, 1).assertProblem(404, CommonErrorCode.NOT_FOUND);
        assertTrue(get(owner, BASE + "/credentials").assertOk().as(ClientApiCredentialsResponseBody.class).getItems()
            .isEmpty());

        var account = new Workspace(owner.accountId(), workspaces.accountSession(owner.accountId()), owner.tenantId(), 0);
        get(account, BASE + "/credentials").assertProblem(403, ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE);

        var reader = workspaces.owner(workspaces.tenant(), ClientDeveloperPermissions.API_CREDENTIAL_READ);
        get(reader, BASE + "/credentials").assertOk();
        create(reader, "Reader", List.of(SCOPE_READ), 30)
            .assertProblem(403, ClientAuthenticationErrorCode.CLIENT_PERMISSION_DENIED);
        var nobody = workspaces.owner(workspaces.tenant());
        get(nobody, BASE + "/credentials").assertProblem(403, ClientAuthenticationErrorCode.CLIENT_PERMISSION_DENIED);
        call(HttpMethod.GET, BASE + "/credentials", headers(owner.actor(), "abc"), null)
            .assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
    }

    /** 名称、有效期、Scope 与宽限的输入校验：字段约束 422，业务规则 400；不写库。 */
    @Test
    void test_invalidParameter() {
        var owner = workspaces.owner(workspaces.tenant(), ALL);

        create(owner, "   ", List.of(SCOPE_READ), 30).assertProblem(422, CommonErrorCode.VALIDATION_ERROR);
        create(owner, "x".repeat(65), List.of(SCOPE_READ), 30).assertProblem(422, CommonErrorCode.VALIDATION_ERROR);
        create(owner, "Ser\tver", List.of(SCOPE_READ), 30).assertStatus(400);
        create(owner, "Server", List.of(SCOPE_READ), 45).assertStatus(400);
        create(owner, "Server", List.of(SCOPE_READ, SCOPE_READ), 30).assertStatus(400);
        create(owner, "Server", List.of("orders:unknown"), 30).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        long id = Long.parseLong(create(owner, "Server", List.of(SCOPE_READ), 30).assertStatus(201)
            .as(ClientApiCredentialSecretResponseBody.class).getCredential().getCredentialId());
        rotate(owner, id, 1, 30).assertStatus(400);
        get(owner, BASE + "/credentials?status=UNKNOWN").assertStatus(400);

        assertEquals(1, developerData.credentials(owner.tenantId()).size());
    }

    /** CLIENT 权限判断返回已授予子集；账号上下文 403。 */
    @Test
    void test_authorize() {
        var reader = workspaces.owner(workspaces.tenant(), ClientDeveloperPermissions.API_CREDENTIAL_READ,
            ClientDeveloperPermissions.WEBHOOK_READ);

        var granted = call(HttpMethod.POST, "/authorization/v1/client/authorize", headers(reader.actor(), reader.session()),
            new ClientAuthorizationRequestBody(List.of(ClientDeveloperPermissions.API_CREDENTIAL_MANAGE,
                ClientDeveloperPermissions.WEBHOOK_READ, ClientDeveloperPermissions.API_CREDENTIAL_READ)))
            .assertOk().as(ClientAuthorizationResponseBody.class).getGranted();

        assertEquals(List.of(ClientDeveloperPermissions.WEBHOOK_READ, ClientDeveloperPermissions.API_CREDENTIAL_READ),
            granted);
        var account = Long.toString(workspaces.accountSession(reader.accountId()));
        call(HttpMethod.POST, "/authorization/v1/client/authorize", headers(reader.actor(), account),
            new ClientAuthorizationRequestBody(List.of(ClientDeveloperPermissions.WEBHOOK_READ)))
            .assertProblem(403, ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE);
    }

    /** 以工作区会话 GET。 */
    PlatformResponseChecker get(Workspace workspace, String path) {
        return call(HttpMethod.GET, path, headers(workspace.actor(), workspace.session()), null);
    }

    /** 创建凭证。 */
    PlatformResponseChecker create(Workspace workspace, String name, List<String> scopes, int days) {
        return call(HttpMethod.POST, BASE + "/credentials", headers(workspace.actor(), workspace.session()),
            new CreateClientApiCredentialRequestBody(name, scopes, days));
    }

    /** 轮换凭证。 */
    PlatformResponseChecker rotate(Workspace workspace, long credentialId, int expectedVersion, int graceMinutes) {
        return call(HttpMethod.POST, BASE + "/credentials/" + credentialId + "/rotation",
            headers(workspace.actor(), workspace.session()),
            new RotateClientApiCredentialRequestBody(expectedVersion, graceMinutes));
    }

    /** 撤销凭证。 */
    PlatformResponseChecker revoke(Workspace workspace, long credentialId, int expectedVersion) {
        return call(HttpMethod.POST, BASE + "/credentials/" + credentialId + "/revocation",
            headers(workspace.actor(), workspace.session()), new RevokeClientApiCredentialRequestBody(expectedVersion));
    }

    /** 以 Gateway 方式调用 PLT-I03。 */
    PlatformResponseChecker validate(String credential) {
        HttpHeaders headers = headers(null, null);
        headers.set(ApiCredentialConstraints.CREDENTIAL_HEADER, credential);
        return call(HttpMethod.POST, "/api-credentials/v1/validate", headers, null);
    }
}
