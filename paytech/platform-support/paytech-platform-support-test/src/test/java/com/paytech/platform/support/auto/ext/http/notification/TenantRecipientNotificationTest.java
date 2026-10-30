package com.paytech.platform.support.auto.ext.http.notification;

import static com.paytech.platform.support.delivery.integration.provider.EmailProviderResult.Outcome.DELIVERED;
import static com.paytech.platform.support.test.builder.NotificationRequestBuilder.SOURCE_SYSTEM;
import static com.paytech.platform.support.test.builder.NotificationRequestBuilder.TEMPLATE_CODE;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamPermissions;
import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.enums.AuthenticationRealm;
import com.paytech.platform.support.api.enums.ClientTeamRole;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.api.notification.NotificationCommandApi;
import com.paytech.platform.support.api.notification.request.SubmitNotificationRequestBody;
import com.paytech.platform.support.api.notification.response.SubmitNotificationResponseBody;
import com.paytech.platform.support.test.base.NotificationTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.OperationsTestMapper;
import com.paytech.platform.support.test.dal.TenantRecipientTestMapper;
import com.paytech.platform.support.test.mocker.external.LoginEmailVaultMocker;
import com.paytech.platform.support.test.mocker.local.ClientWorkspaceMocker;
import com.paytech.platform.support.test.mocker.local.ClientWorkspaceMocker.TeamWorkspace;
import com.paytech.platform.support.test.mocker.local.OperationsMocker;

/**
 * 通知内部 HTTP 提交按租户权限解析收件人（tenantRecipients）：经真实 Controller、白名单、组合 Processor、access-control
 * 收件人查询、delivery Processor、补偿发送与本地 MySQL。
 * <li>只发给 ACTIVE 成员中经当前有效角色持有该 ACTIVE 权限、账号 ACTIVE 且邮箱已验证的人；直接复用登录邮箱 Token，受理时不调用
 * Vault Protect。</li>
 * <li>0 人时 200 且不落库；超过 100 人 400；与 recipients 同时给或都不给 422。</li>
 *
 * @see NotificationCommandApi#submitNotification(String, SubmitNotificationRequestBody)
 */
class TenantRecipientNotificationTest extends NotificationTestBase {

    private static final String PATH = "/notifications/v1/requests";
    private static final String MANAGE = ClientTeamPermissions.PAYOUT_ACCOUNT_MANAGE;
    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    @Autowired
    ClientWorkspaceMocker workspaces;
    @Autowired
    OperationsMocker operationsMocker;
    @Autowired
    OperationsTestMapper operationsData;
    @Autowired
    TenantRecipientTestMapper recipientData;

    private TeamWorkspace workspace;
    private long ownerRefId;

    /** 每例：四个系统角色的 Client 工作区，OWNER 为 alice；收件邮箱的 Reveal 使用登录邮箱 Token。 */
    @BeforeEach
    void setUpTenantRecipients() {
        workspace = workspaces.teamWorkspace(ALICE);
        ownerRefId = recipientData.ownerRefId(workspace.tenantId());
        vault.allow(ALICE, LoginEmailVaultMocker.tokenOf(ALICE));
        vault.allow(BOB, LoginEmailVaultMocker.tokenOf(BOB));
    }

    /**
     * OWNER 与管理员持有维护权限，只读成员与开发者不持有：两人各一条投递，Token 为登录邮箱 Token，不调用 Protect；
     * 语言覆盖作用于全部收件人；同一来源键再次提交 409。
     */
    @Test
    void test_normalFlow() {
        workspaces.teamMember(workspace, ClientTeamRole.TENANT_ADMIN, BOB);
        workspaces.teamMember(workspace, ClientTeamRole.TENANT_VIEWER, "carol@example.com");
        workspaces.teamMember(workspace, ClientTeamRole.TENANT_DEVELOPER, "dave@example.com");
        provider.expectSend(ALICE, DELIVERED);
        provider.expectSend(BOB, DELIVERED);

        var response = submit(body(MANAGE, "en-US")).assertOk().as(SubmitNotificationResponseBody.class);

        assertAll(() -> assertTrue(Long.parseLong(response.getRequestId()) > 0),
            () -> assertEquals(2, response.getAcceptedCount()), () -> assertEquals(2, response.getDeliveryCount()));
        fixture.awaitIdle();
        assertEquals(0, vault.protectCount());
        assertEquals(java.util.Set.of(LoginEmailVaultMocker.tokenOf(ALICE), LoginEmailVaultMocker.tokenOf(BOB)),
            java.util.Set.copyOf(recipientData.deliveryTokens()));
        assertEquals(java.util.Set.of(ALICE, BOB),
            java.util.Set.copyOf(provider.calls().stream().map(call -> call.getEmail()).toList()));
        assertTrue(provider.calls().stream().allMatch(call -> call.getSubject().startsWith("Hello ")), "语言覆盖为 en-US");
        assertEquals(10001L, ((Number) mapper.requestSources().getFirst().get("tenant_id")).longValue());

        submit(body(MANAGE, null)).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
    }

    /**
     * 解析排除：成员停用、角色授予已过期、角色上的权限已撤销、邮箱未验证、角色不含该权限；只剩 alice。未给语言覆盖时用默认语言。
     */
    @Test
    void test_resolutionExcludesIneligibleMembers() {
        var suspended = workspaces.teamMember(workspace, ClientTeamRole.TENANT_OWNER, "erin@example.com");
        recipientData.suspendMember(suspended.memberId());
        var expired = workspaces.teamMember(workspace, ClientTeamRole.TENANT_OWNER, "frank@example.com");
        recipientData.expireMemberRoles(expired.memberId(), operationsMocker.now().minusMillis(1));
        var unverified = workspaces.teamMember(workspace, ClientTeamRole.TENANT_OWNER, "gina@example.com");
        recipientData.unverifyEmail(unverified.accountId());
        workspaces.teamMember(workspace, ClientTeamRole.TENANT_ADMIN, BOB);
        recipientData.revokeRolePermission(workspace.roleIds().get(ClientTeamRole.TENANT_ADMIN), MANAGE);
        workspaces.teamMember(workspace, ClientTeamRole.TENANT_VIEWER, "carol@example.com");
        provider.expectSend(ALICE, DELIVERED);

        var response = submit(body(MANAGE, null)).assertOk().as(SubmitNotificationResponseBody.class);

        assertEquals(1, response.getDeliveryCount());
        fixture.awaitIdle();
        assertEquals(List.of(LoginEmailVaultMocker.tokenOf(ALICE)), recipientData.deliveryTokens());
        assertTrue(provider.calls().getFirst().getSubject().startsWith("通知 "), "默认语言 zh-CN");
    }

    /** 没有对应租户、或租户内无人持有该权限：200、requestId 为空、deliveryCount 0，不调用 Vault、不落库，来源键不被占用。 */
    @Test
    void test_noDataFound() {
        var noTenant = body(MANAGE, null);
        noTenant.put("tenantRecipients", criteria(AccessTenantType.CLIENT.getCode(), Long.toString(OperatorBuilder.nextId()),
            MANAGE, null));
        var none = submit(noTenant).assertOk().as(SubmitNotificationResponseBody.class);
        var nobody = submit(body("CLIENT_PERMISSION_NOBODY_HOLDS", null)).assertOk()
            .as(SubmitNotificationResponseBody.class);
        var partner = body(MANAGE, null);
        partner.put("tenantRecipients", criteria(AccessTenantType.PARTNER.getCode(), Long.toString(ownerRefId), MANAGE, null));
        var wrongType = submit(partner).assertOk().as(SubmitNotificationResponseBody.class);

        for (var result : List.of(none, nobody, wrongType)) {
            assertAll(() -> assertNull(result.getRequestId()), () -> assertEquals(0, result.getAcceptedCount()),
                () -> assertEquals(0, result.getDeliveryCount()));
        }
        vault.assertNoCalls();
        assertEquals(0, mapper.requests());
    }

    /** 收件成员超过 100 人：400 业务拒绝，不调用 Vault、不落库。 */
    @Test
    void test_tooManyRecipients() {
        var now = operationsMocker.now();
        long roleId = workspace.roleIds().get(ClientTeamRole.TENANT_VIEWER);
        String scope = Long.toString(workspace.tenantId());
        for (int index = 0; index < 100; index++) {
            long accountId = OperatorBuilder.nextId();
            String email = "member" + index + "@example.com";
            assertEquals(1, operationsData.insertAccount(accountId, AuthenticationRealm.CUSTOMER.getCode(),
                OperationsAccountType.HUMAN.getCode(), null, LoginEmailVaultMocker.tokenOf(email), "m***@example.com", now,
                OperationsAccountStatus.ACTIVE.getCode(), null, now));
            long memberId = OperatorBuilder.nextId();
            assertEquals(1, operationsData.insertMember(memberId, workspace.tenantId(), accountId, "MEMBER", "ACTIVE", now));
            assertEquals(1, operationsData.insertMemberRole(OperatorBuilder.nextId(), memberId, workspace.tenantId(), roleId,
                scope, now.minusSeconds(1), null, memberId, "ACTIVE", now));
        }

        submit(body(ClientTeamPermissions.BALANCE_READ, null)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        vault.assertNoCalls();
        assertEquals(0, mapper.requests());
    }

    /** recipients 与 tenantRecipients 同时给或都不给、租户类型不支持、所有者引用不规范、缺权限码：422，不调用 Vault、不落库。 */
    @Test
    void test_invalidParameter() {
        var both = body(MANAGE, null);
        both.put("recipients", List.of(Map.of("email", ALICE)));
        submit(both).assertProblem(422, CommonErrorCode.VALIDATION_ERROR);
        var neither = body(MANAGE, null);
        neither.remove("tenantRecipients");
        submit(neither).assertProblem(422, CommonErrorCode.VALIDATION_ERROR);
        var internal = body(MANAGE, null);
        internal.put("tenantRecipients", criteria(AccessTenantType.INTERNAL.getCode(), Long.toString(ownerRefId), MANAGE, null));
        submit(internal).assertProblem(422, CommonErrorCode.VALIDATION_ERROR);
        var owner = body(MANAGE, null);
        owner.put("tenantRecipients", criteria(AccessTenantType.CLIENT.getCode(), "0123", MANAGE, null));
        submit(owner).assertProblem(422, CommonErrorCode.VALIDATION_ERROR);
        var permission = body(MANAGE, null);
        permission.put("tenantRecipients", criteria(AccessTenantType.CLIENT.getCode(), Long.toString(ownerRefId), null, null));
        submit(permission).assertProblem(422, CommonErrorCode.VALIDATION_ERROR);

        vault.assertNoCalls();
        assertEquals(0, mapper.requests());
    }

    /** 合法基线 Body：本例 Client 租户内持有给定权限的成员；用 Map 构造以便负例增删字段。 */
    private Map<String, Object> body(String permissionCode, String locale) {
        var body = new HashMap<String, Object>();
        body.put("tenantId", "10001");
        body.put("sourceEventKey", "beneficiary-" + workspace.tenantId());
        body.put("templateCode", TEMPLATE_CODE);
        body.put("defaultLocale", "zh-CN");
        body.put("defaultParameters", Map.of("notice", "Account verified", "name", "Client"));
        body.put("tenantRecipients", criteria(AccessTenantType.CLIENT.getCode(), Long.toString(ownerRefId), permissionCode,
            locale));
        return body;
    }

    /** 租户权限收件条件；值为 null 的键不放入。 */
    private static Map<String, Object> criteria(String tenantType, String ownerRefId, String permissionCode, String locale) {
        var criteria = new HashMap<String, Object>();
        criteria.put("tenantType", tenantType);
        criteria.put("ownerRefId", ownerRefId);
        if (permissionCode != null) {
            criteria.put("permissionCode", permissionCode);
        }
        if (locale != null) {
            criteria.put("locale", locale);
        }
        return criteria;
    }

    /** 以白名单调用方发送一次真实 HTTP 请求；响应不得回显 Token。 */
    private PlatformResponseChecker submit(Map<String, Object> body) {
        String requestId = "plt-notify-tenant-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set(NotificationCommandApi.CALLER_SERVICE_HEADER, SOURCE_SYSTEM);
        var checker = new PlatformResponseChecker(httpTestInvoker.exchange(HttpMethod.POST, PATH, headers, body),
            objectMapper, requestId);
        assertFalse(checker.text().contains(LoginEmailVaultMocker.tokenOf(ALICE)), "响应不得回显邮箱 Token");
        return checker;
    }
}
