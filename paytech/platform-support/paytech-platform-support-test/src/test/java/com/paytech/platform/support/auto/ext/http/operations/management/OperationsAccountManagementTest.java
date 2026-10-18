package com.paytech.platform.support.auto.ext.http.operations.management;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.operations.OperationsManagementApi;
import com.paytech.platform.support.api.operations.request.*;
import com.paytech.platform.support.api.operations.response.AccountResponseBody;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.OperationsCatalog;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.ConfigurationMocker;

import tools.jackson.core.type.TypeReference;

/**
 * Operations 人员生命周期：列表、创建、停用、启用、删除与角色分配，覆盖授予上限、末位管理员保护与版本 CAS。
 *
 * @see OperationsManagementApi#accounts(String, String, Integer, Integer)
 * @see OperationsManagementApi#createAccount(String, String, CreateAccountRequestBody)
 * @see OperationsManagementApi#disableAccount(String, String, String, DisableAccountRequestBody)
 * @see OperationsManagementApi#enableAccount(String, String, String, EnableAccountRequestBody)
 * @see OperationsManagementApi#deleteAccount(String, String, String, DeleteAccountRequestBody)
 * @see OperationsManagementApi#assignRoles(String, String, String, AssignRolesRequestBody)
 */
class OperationsAccountManagementTest extends OperationsApiTestBase {

    private static final String ACCOUNTS = "/operations/v1/accounts";
    private static final String ADMIN = Long.toString(OperationsCatalog.ADMIN_ROLE_ID);
    private static final String VIEWER = Long.toString(OperationsCatalog.VIEWER_ROLE_ID);

    @Autowired
    private ConfigurationMocker configuration;

    private Operator admin;

    /** 正式目录的权限引用与一位平台管理员。 */
    @BeforeEach
    void setUpClassMocks() {
        configuration.permissionReferences();
        admin = operationsMocker.operator(OperatorBuilder.administrator());
    }

    /** 列表按账号排序返回人员、成员版本与角色；初始化账号与已删除人员不出现。 */
    @Test
    void test_listAccounts() {
        Operator viewer = operationsMocker.operator(OperatorBuilder.viewer());
        operationsMocker.operator(OperatorBuilder.bootstrap());
        operationsMocker.operator(OperatorBuilder.administrator().accountStatus("CLOSED"));

        var page = get(ACCOUNTS + "?page=1&size=10", viewer).assertOk()
            .as(new TypeReference<PagingResponseBody<AccountResponseBody>>() {});

        assertEquals(2, page.total());
        assertEquals(List.of(admin.actor(), viewer.actor()).stream().sorted().toList(),
            page.items().stream().map(AccountResponseBody::getAccountId).toList());
        var row =
            page.items().stream().filter(item -> item.getAccountId().equals(admin.actor())).findFirst().orElseThrow();
        assertAll(() -> assertEquals("ACTIVE", row.getStatus()), () -> assertEquals(1, row.getVersion()),
            () -> assertEquals(Long.toString(admin.memberId()), row.getMemberId()),
            () -> assertEquals(List.of("PLATFORM_ADMIN"),
                row.getRoles().stream().map(role -> role.getRoleCode()).toList()));
        get(ACCOUNTS + "?page=1&size=11", viewer).assertProblem(400, "INVALID_REQUEST");
    }

    /** 没有人员读取权限时拒绝。 */
    @Test
    void test_listRequiresPermission() {
        long role = operationsMocker.customRole("AUDITOR", "OPERATIONS_AUDIT_READ");
        Operator auditor = operationsMocker.operator(OperatorBuilder.administrator().roles(role));

        get(ACCOUNTS, auditor).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
    }

    /** 创建人员保存账号、强制改密的凭据、成员与角色，邮箱只以 Vault Token 与脱敏值保存。 */
    @Test
    void test_createAccount() {
        String email = "new.person@example.test";
        loginEmailVault.allow(email);

        var created = create(admin, email, List.of(VIEWER)).assertOk().as(AccountResponseBody.class);

        long accountId = Long.parseLong(created.getAccountId());
        assertAll(() -> assertEquals("n***@example.test", created.getEmailMasked()),
            () -> assertEquals("ACTIVE", created.getStatus()), () -> assertTrue(created.getPasswordChangeRequired()),
            () -> assertEquals(1, created.getVersion()), () -> assertEquals(1, created.getMemberVersion()),
            () -> assertEquals(List.of("OPERATIONS_VIEWER"),
                created.getRoles().stream().map(role -> role.getRoleCode()).toList()));
        Map<String, Object> account = operationsData.account(accountId);
        assertAll(() -> assertEquals(loginEmailVaultTokenOf(email), account.get("email_vault_token")),
            () -> assertFalse(String.valueOf(account).contains(email), "账号行不得保存邮箱原文"));
        var grants = operationsData.memberRoles(Long.parseLong(created.getMemberId()));
        assertEquals(1, grants.size());
        assertEquals(admin.accountId(), ((Number)grants.getFirst().get("granted_by")).longValue());
        auditChecker.assertEvent(requestId, "OPERATIONS_ACCOUNT_CREATE", "SUCCEEDED", admin.actor(), "USER_ACCOUNT",
            created.getAccountId());
    }

    /** 已被使用的邮箱拒绝创建。 */
    @Test
    void test_createDuplicateEmailRejected() {
        create(admin, admin.email(), List.of(VIEWER)).assertProblem(400, "BUSINESS_CHECK_FAILED");

        assertEquals(1, operationsData.accounts("OPERATIONS").size());
    }

    /** 操作者不能授予超出自身权限上限的角色，也不能选择不存在或重复的角色。 */
    @Test
    void test_createRoleBoundaries() {
        long creatorRole =
            operationsMocker.customRole("ACCOUNT_CREATOR", "OPERATIONS_ACCOUNT_CREATE", "OPERATIONS_ACCOUNT_READ");
        Operator creator = operationsMocker.operator(OperatorBuilder.administrator().roles(creatorRole));
        loginEmailVault.allow("limited@example.test");

        create(creator, "limited@example.test", List.of(ADMIN)).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        create(admin, "limited@example.test", List.of("12345")).assertProblem(400, "BUSINESS_CHECK_FAILED");
        create(admin, "limited@example.test", List.of(VIEWER, VIEWER)).assertProblem(400, "INVALID_REQUEST");
        create(admin, "limited@example.test", List.of()).assertProblem(422, "VALIDATION_ERROR")
            .assertFieldError("roleIds");
        create(viewerOperator(), "limited@example.test", List.of(VIEWER)).assertProblem(403,
            "OPERATIONS_PERMISSION_DENIED");

        assertEquals(3, operationsData.accounts("OPERATIONS").size(), "失败不创建账号");
    }

    /** 初始密码与邮箱格式在保存前校验。 */
    @Test
    void test_createInvalidCredentials() {
        post(ACCOUNTS, admin, new CreateAccountRequestBody("bad email", OperatorBuilder.PASSWORD, List.of(VIEWER), "r"))
            .assertProblem(400, "INVALID_REQUEST");
        post(ACCOUNTS, admin, new CreateAccountRequestBody("short@example.test", "short", List.of(VIEWER), "r"))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");

        assertEquals(0, loginEmailVault.protectCount(), "校验失败不调用 Vault");
    }

    /** 停用推进认证纪元并撤销该人员全部会话。 */
    @Test
    void test_disableAccount() {
        Operator target = operationsMocker.operator(OperatorBuilder.viewer());

        post(ACCOUNTS + "/" + target.actor() + "/disable", admin, new DisableAccountRequestBody(1, "离职"))
            .assertNoContent();

        Map<String, Object> account = operationsData.account(target.accountId());
        assertAll(() -> assertEquals("DISABLED", account.get("status")),
            () -> assertEquals(2, ((Number)account.get("auth_epoch")).intValue()),
            () -> assertEquals(2, ((Number)account.get("version")).intValue()),
            () -> assertEquals("ACCOUNT_DISABLED", operationsData.session(target.sessionId()).get("revocation_reason")),
            () -> assertEquals("REVOKED", operationsData.refreshes(target.sessionId()).getFirst().get("status")));
        auditChecker.assertEvent(requestId, "OPERATIONS_ACCOUNT_DISABLE", "SUCCEEDED", admin.actor(), "USER_ACCOUNT",
            target.actor());
    }

    /** 不能停用本人、旧版本拒绝、非管理员不能停用最后一位管理员。 */
    @Test
    void test_disableGuards() {
        Operator target = operationsMocker.operator(OperatorBuilder.viewer());
        post(ACCOUNTS + "/" + admin.actor() + "/disable", admin, new DisableAccountRequestBody(1, "r"))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");
        post(ACCOUNTS + "/" + target.actor() + "/disable", admin, new DisableAccountRequestBody(2, "r"))
            .assertProblem(409, "CONCURRENT_REQUEST");

        long managerRole =
            operationsMocker.customRole("ACCOUNT_MANAGER", "OPERATIONS_ACCOUNT_DISABLE", "OPERATIONS_ACCOUNT_DELETE");
        Operator manager = operationsMocker.operator(OperatorBuilder.administrator().roles(managerRole));
        post(ACCOUNTS + "/" + admin.actor() + "/disable", manager, new DisableAccountRequestBody(1, "r"))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");
        post(ACCOUNTS + "/" + bootstrapId() + "/disable", admin, new DisableAccountRequestBody(1, "r"))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");

        assertEquals("ACTIVE", operationsData.account(admin.accountId()).get("status"));
        assertEquals("ACTIVE", operationsData.account(target.accountId()).get("status"));
    }

    /** 启用已停用人员恢复原授权，但不恢复旧会话。 */
    @Test
    void test_enableAccount() {
        Operator target = operationsMocker.operator(OperatorBuilder.viewer());
        post(ACCOUNTS + "/" + target.actor() + "/disable", admin, new DisableAccountRequestBody(1, "r"))
            .assertNoContent();

        post(ACCOUNTS + "/" + target.actor() + "/enable", admin, new EnableAccountRequestBody(2, "复职"))
            .assertNoContent(204);

        Map<String, Object> account = operationsData.account(target.accountId());
        assertAll(() -> assertEquals("ACTIVE", account.get("status")),
            () -> assertEquals(3, ((Number)account.get("auth_epoch")).intValue()),
            () -> assertEquals("REVOKED", operationsData.session(target.sessionId()).get("status")));
        auditChecker.assertEvent(requestId, "OPERATIONS_ACCOUNT_ENABLE", "SUCCEEDED", admin.actor(), "USER_ACCOUNT",
            target.actor());
    }

    /** 只能启用已停用人员，且其凭据与成员关系必须仍有效。 */
    @Test
    void test_enableGuards() {
        Operator active = operationsMocker.operator(OperatorBuilder.viewer());
        post(ACCOUNTS + "/" + active.actor() + "/enable", admin, new EnableAccountRequestBody(1, "r"))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");

        Operator revoked =
            operationsMocker.operator(OperatorBuilder.viewer().accountStatus("DISABLED").passwordStatus("REVOKED"));
        post(ACCOUNTS + "/" + revoked.actor() + "/enable", admin, new EnableAccountRequestBody(1, "r"))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");

        Operator suspended =
            operationsMocker.operator(OperatorBuilder.viewer().accountStatus("DISABLED").memberStatus("SUSPENDED"));
        post(ACCOUNTS + "/" + suspended.actor() + "/enable", admin, new EnableAccountRequestBody(1, "r"))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");

        Operator stale = operationsMocker.operator(OperatorBuilder.viewer().accountStatus("DISABLED"));
        post(ACCOUNTS + "/" + stale.actor() + "/enable", admin, new EnableAccountRequestBody(5, "r")).assertProblem(409,
            "CONCURRENT_REQUEST");
    }

    /** 删除关闭账号并撤销成员、授权、密码与会话，保留历史行。 */
    @Test
    void test_deleteAccount() {
        Operator target = operationsMocker.operator(OperatorBuilder.viewer());

        call(org.springframework.http.HttpMethod.DELETE, ACCOUNTS + "/" + target.actor(), headers(admin),
            new DeleteAccountRequestBody(1, 1, "离职")).assertNoContent(204);

        assertAll(() -> assertEquals("CLOSED", operationsData.account(target.accountId()).get("status")),
            () -> assertEquals("REVOKED", operationsData.members(target.accountId()).getFirst().get("status")),
            () -> assertEquals("REVOKED", operationsData.memberRoles(target.memberId()).getFirst().get("status")),
            () -> assertEquals("REVOKED", operationsData.password(target.accountId()).get("status")),
            () -> assertEquals("ACCOUNT_DELETED", operationsData.session(target.sessionId()).get("revocation_reason")));
        auditChecker.assertEvent(requestId, "OPERATIONS_ACCOUNT_DELETE", "SUCCEEDED", admin.actor(), "USER_ACCOUNT",
            target.actor());
        call(org.springframework.http.HttpMethod.DELETE, ACCOUNTS + "/" + target.actor(), headers(admin),
            new DeleteAccountRequestBody(2, 2, "r")).assertProblem(400, "BUSINESS_CHECK_FAILED");
    }

    /** 不能删除本人，成员版本过期时拒绝。 */
    @Test
    void test_deleteGuards() {
        Operator target = operationsMocker.operator(OperatorBuilder.viewer());
        call(org.springframework.http.HttpMethod.DELETE, ACCOUNTS + "/" + admin.actor(), headers(admin),
            new DeleteAccountRequestBody(1, 1, "r")).assertProblem(400, "BUSINESS_CHECK_FAILED");
        call(org.springframework.http.HttpMethod.DELETE, ACCOUNTS + "/" + target.actor(), headers(admin),
            new DeleteAccountRequestBody(1, 3, "r")).assertProblem(409, "CONCURRENT_REQUEST");

        assertEquals("ACTIVE", operationsData.account(target.accountId()).get("status"));
    }

    /** 分配角色整体替换当前授权并推进成员版本。 */
    @Test
    void test_assignRoles() {
        Operator target = operationsMocker.operator(OperatorBuilder.viewer());

        put(ACCOUNTS + "/" + target.actor() + "/roles", admin, new AssignRolesRequestBody(1, List.of(ADMIN), "晋升"))
            .assertNoContent();

        var grants = operationsData.memberRoles(target.memberId());
        assertEquals(List.of("ACTIVE"),
            grants.stream().filter(row -> ((Number)row.get("role_id")).longValue() == OperationsCatalog.ADMIN_ROLE_ID)
                .map(row -> row.get("status")).toList());
        assertEquals(List.of("REVOKED"),
            grants.stream().filter(row -> ((Number)row.get("role_id")).longValue() == OperationsCatalog.VIEWER_ROLE_ID)
                .map(row -> row.get("status")).toList());
        assertEquals(2, ((Number)operationsData.members(target.accountId()).getFirst().get("version")).intValue());
        auditChecker.assertEvent(requestId, "OPERATIONS_ACCOUNT_ROLES", "SUCCEEDED", admin.actor(), "USER_ACCOUNT",
            target.actor());
    }

    /** 移除最后一位管理员的管理员角色、使用旧成员版本或超出授予上限时拒绝。 */
    @Test
    void test_assignRolesGuards() {
        put(ACCOUNTS + "/" + admin.actor() + "/roles", admin, new AssignRolesRequestBody(1, List.of(VIEWER), "r"))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");
        Operator target = operationsMocker.operator(OperatorBuilder.viewer());
        put(ACCOUNTS + "/" + target.actor() + "/roles", admin, new AssignRolesRequestBody(9, List.of(ADMIN), "r"))
            .assertProblem(409, "CONCURRENT_REQUEST");
        long assignerRole = operationsMocker.customRole("ROLE_ASSIGNER", "OPERATIONS_ACCOUNT_ASSIGN_ROLES");
        Operator assigner = operationsMocker.operator(OperatorBuilder.administrator().roles(assignerRole));
        put(ACCOUNTS + "/" + target.actor() + "/roles", assigner, new AssignRolesRequestBody(1, List.of(ADMIN), "r"))
            .assertProblem(403, "OPERATIONS_PERMISSION_DENIED");

        assertEquals(1, ((Number)operationsData.members(target.accountId()).getFirst().get("version")).intValue());
    }

    private PlatformResponseChecker create(Operator actor, String email, List<String> roles) {
        return post(ACCOUNTS, actor, new CreateAccountRequestBody(email, "Initial-Strong-Secret-42", roles, "入职"));
    }

    private Operator viewerOperator() {
        return operationsMocker.operator(OperatorBuilder.viewer());
    }

    private String bootstrapId() {
        return operationsMocker.operator(OperatorBuilder.bootstrap()).actor();
    }

    private static String loginEmailVaultTokenOf(String email) {
        return com.paytech.platform.support.test.mocker.external.LoginEmailVaultMocker.tokenOf(email);
    }
}
