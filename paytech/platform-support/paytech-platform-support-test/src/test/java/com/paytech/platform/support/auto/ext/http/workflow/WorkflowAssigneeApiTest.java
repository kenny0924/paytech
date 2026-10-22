package com.paytech.platform.support.auto.ext.http.workflow;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.workflow.WorkflowInstanceApi;
import com.paytech.platform.support.api.workflow.response.WorkflowAssigneeCandidateBody;
import com.paytech.platform.support.api.workflow.response.WorkflowAssigneeCandidateListResponseBody;
import com.paytech.platform.support.test.base.WorkflowApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;

/**
 * 选人：只返回 ACTIVE 的 Operations 人员（不含 Bootstrap 与停用账号），完整邮箱经 Vault 稳定 Token 精确匹配，
 * 其他关键字按登录名前缀或脱敏邮箱片段匹配，LIKE 通配符按字面；响应不含邮箱原文；办理或流程管理权限之一即可。
 *
 * @see WorkflowInstanceApi#listAssigneeCandidates(String, String, String)
 */
class WorkflowAssigneeApiTest extends WorkflowApiTestBase {

    private static final String PATH = RUNTIME + "/assignee-candidates";

    private Operator alice;
    private Operator disabled;

    /** 本类公共：再插入一名 ACTIVE 人员、一名停用人员与初始化管理员账号。 */
    @BeforeEach
    void setUpAssigneeMocks() {
        alice = person();
        disabled = operationsMocker.operator(OperatorBuilder.administrator().accountStatus("DISABLED"));
        operationsMocker.operator(OperatorBuilder.bootstrap());
    }

    /** 空关键字返回全部 ACTIVE 人员；完整邮箱精确匹配一人，显示名为脱敏邮箱，响应不含邮箱原文。 */
    @Test
    void test_normalFlow() {
        var all = search(admin, "");
        var byEmail = get(PATH + "?keyword=" + alice.email().toUpperCase(), admin).assertOk();
        WorkflowAssigneeCandidateBody hit = byEmail.as(WorkflowAssigneeCandidateListResponseBody.class).getItems()
            .getFirst();

        assertAll(() -> assertEquals(Set.of(admin.actor(), alice.actor()), ids(all)),
            () -> assertEquals(1, byEmail.as(WorkflowAssigneeCandidateListResponseBody.class).getItems().size()),
            () -> assertEquals(alice.actor(), hit.getAccountId()), () -> assertNull(hit.getLoginName()),
            () -> assertEquals(hit.getEmailMasked(), hit.getDisplayName()),
            () -> assertFalse(byEmail.text().contains(alice.email())),
            () -> assertEquals(1, loginEmailVault.protectCount()));
    }

    /** 脱敏邮箱片段匹配；初始化管理员的登录名、停用人员的完整邮箱、格式无效的邮箱、LIKE 通配符与不存在的片段都不返回。 */
    @Test
    void test_noDataFound() {
        String domainFragment = alice.email().substring(alice.email().indexOf('@') + 1);

        var fragment = search(admin, domainFragment);
        var stopped = search(admin, disabled.email());
        var invalid = search(admin, "a@@b");
        var wildcard = search(admin, "_");
        var none = search(admin, "no-such-person");
        var bootstrap = search(admin, "adm");

        assertAll(() -> assertEquals(Set.of(admin.actor(), alice.actor()), ids(fragment)),
            () -> assertEquals(Set.of(), ids(stopped)), () -> assertEquals(Set.of(), ids(invalid)),
            () -> assertEquals(Set.of(), ids(wildcard)), () -> assertEquals(Set.of(), ids(none)),
            () -> assertEquals(Set.of(), ids(bootstrap), "初始化管理员不是人员账号"),
            () -> assertEquals(1, loginEmailVault.protectCount(), "无效邮箱不调用 Vault"));
    }

    /** 关键字超长 400；只有定义读取权限 403；只有流程管理权限可以选人。 */
    @Test
    void test_invalidParameterAndPermission() {
        long readRole = operationsMocker.customRole("WORKFLOW_READER", WorkflowPermissions.DEFINITION_READ);
        long manageRole = operationsMocker.customRole("WORKFLOW_DESIGNER", WorkflowPermissions.DEFINITION_MANAGE);
        Operator reader = operationsMocker.operator(OperatorBuilder.administrator().roles(readRole));
        Operator designer = operationsMocker.operator(OperatorBuilder.administrator().roles(manageRole));

        get(PATH + "?keyword=" + "x".repeat(129), admin).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH, reader).assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        var allowed = search(designer, null);

        assertEquals(Set.of(admin.actor(), alice.actor(), reader.actor(), designer.actor()), ids(allowed));
    }

    private List<WorkflowAssigneeCandidateBody> search(Operator operator, String keyword) {
        String path = keyword == null ? PATH : PATH + "?keyword=" + keyword;
        return get(path, operator).assertOk().as(WorkflowAssigneeCandidateListResponseBody.class).getItems();
    }

    private static Set<String> ids(List<WorkflowAssigneeCandidateBody> items) {
        return items.stream().map(WorkflowAssigneeCandidateBody::getAccountId).collect(Collectors.toSet());
    }
}
