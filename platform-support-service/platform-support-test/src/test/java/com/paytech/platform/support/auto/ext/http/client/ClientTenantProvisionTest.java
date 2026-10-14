package com.paytech.platform.support.auto.ext.http.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.enums.AuthenticationRealm;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.api.enums.TenantProjectionErrorCode;
import com.paytech.platform.support.api.tenantprojection.TenantProjectionApi;
import com.paytech.platform.support.api.tenantprojection.request.ProvisionRequestBody;
import com.paytech.platform.support.api.tenantprojection.response.ProjectionResponseBody;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;

/**
 * CCT 来源首次开通 CLIENT 工作区：真实读取申请人账号行并创建 PENDING Tenant 与 OWNER 成员。
 *
 * @see TenantProjectionApi#provision(ProvisionRequestBody)
 * @see TenantProjectionApi#projection(String, String)
 */
class ClientTenantProvisionTest extends OperationsApiTestBase {

    private static final String PATH = "/tenant-projections/v1/tenants";

    private static final String PAYLOAD_HASH = "a".repeat(64);

    /** 有效 CUSTOMER 申请人：创建 PENDING Tenant，来源查询读回同一投影，同内容重放返回原结果。 */
    @Test
    void test_normalFlow() {
        long accountId = customer(AuthenticationRealm.CUSTOMER);
        long clientId = OperatorBuilder.nextId();

        var created = provision(clientId, accountId).assertOk().as(ProjectionResponseBody.class);
        try {
            var read = anonymous(HttpMethod.GET, PATH + "/" + AccessTenantType.CLIENT.getCode() + "/" + clientId, null)
                .assertOk().as(ProjectionResponseBody.class);
            var replay = provision(clientId, accountId).assertOk().as(ProjectionResponseBody.class);
            assertAll(() -> assertEquals(AccessTenantType.CLIENT.getCode(), created.getTenantType()),
                () -> assertEquals(Long.toString(clientId), created.getOwnerRefId()),
                () -> assertEquals(Long.toString(accountId), created.getRequestedByAccountId()),
                () -> assertEquals(OperationsTenantOwnerStatus.PENDING.getCode(), created.getOwnerStatus()),
                () -> assertEquals(1, created.getSourceVersion()),
                () -> assertEquals(created.getTenantId(), read.getTenantId()),
                () -> assertEquals(created.getMemberId(), read.getMemberId()),
                () -> assertEquals(created.getTenantId(), replay.getTenantId()));
        } finally {
            operationsData.deleteTenant(Long.parseLong(created.getTenantId()));
        }
    }

    /** 非 CUSTOMER 账号不能作为 CLIENT 申请人，不创建 Tenant。 */
    @Test
    void test_businessRuleViolation() {
        long accountId = customer(AuthenticationRealm.OPERATIONS);

        provision(OperatorBuilder.nextId(), accountId).assertProblem(403, TenantProjectionErrorCode.TENANT_APPLICANT_INVALID);
    }

    private long customer(AuthenticationRealm realm) {
        long id = OperatorBuilder.nextId();
        Instant now = operationsMocker.now();
        operationsData.insertAccount(id, realm.getCode(), OperationsAccountType.HUMAN.getCode(), null, null, null, now,
            OperationsAccountStatus.ACTIVE.getCode(), null, now);
        return id;
    }

    private PlatformResponseChecker provision(long clientId, long accountId) {
        return anonymous(HttpMethod.POST, PATH + "/provision", new ProvisionRequestBody(AccessTenantType.CLIENT.getCode(),
            Long.toString(clientId), Long.toString(accountId), 1, PAYLOAD_HASH));
    }
}
