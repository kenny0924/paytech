package com.paytech.platform.support.auto.ext.http.internalauth;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.enums.AuthenticationRealm;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.api.referencedata.ReferenceCurrencyQueryApi;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyListResponseBody;
import com.paytech.platform.support.api.tenantprojection.TenantProjectionApi;
import com.paytech.platform.support.api.tenantprojection.request.ProvisionRequestBody;
import com.paytech.platform.support.api.tenantprojection.response.ProjectionResponseBody;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;

/**
 * internal-auth.mode=DUAL 不改变现有内部入口：调用方既不带 Token 也不带 X-Caller-Service 时照常放行，结果与 OFF 相同。
 * <li>挑选两类有代表性的入口：CCT 调用的租户投影写入与读取，以及各服务参考数据客户端读取的币种查询。</li>
 *
 * @see TenantProjectionApi#provision(ProvisionRequestBody)
 * @see TenantProjectionApi#projection(String, String)
 * @see ReferenceCurrencyQueryApi#listCurrencies(String, String)
 */
@TestPropertySource(properties = "internal-auth.mode=DUAL")
class InternalAuthDualPassThroughTest extends OperationsApiTestBase {

    private static final String TENANT_PATH = "/tenant-projections/v1/tenants";

    /** 租户投影：无身份开通 PENDING 工作区并读回同一投影。 */
    @Test
    void test_tenantProjectionWithoutIdentity() {
        long accountId = customer();
        long clientId = OperatorBuilder.nextId();

        var created = anonymous(HttpMethod.POST, TENANT_PATH + "/provision",
            new ProvisionRequestBody(AccessTenantType.CLIENT.getCode(), Long.toString(clientId),
                Long.toString(accountId), 1, "a".repeat(64))).assertOk().as(ProjectionResponseBody.class);
        try {
            var read = anonymous(HttpMethod.GET,
                TENANT_PATH + "/" + AccessTenantType.CLIENT.getCode() + "/" + clientId, null)
                .assertOk().as(ProjectionResponseBody.class);
            assertAll(() -> assertEquals(OperationsTenantOwnerStatus.PENDING.getCode(), created.getOwnerStatus()),
                () -> assertEquals(created.getTenantId(), read.getTenantId()));
        } finally {
            operationsData.deleteTenant(Long.parseLong(created.getTenantId()));
        }
    }

    /** 参考数据币种查询：无身份读取常驻 USD 种子。 */
    @Test
    void test_referenceCurrencyQueryWithoutIdentity() {
        var body = anonymous(HttpMethod.GET, "/platform/v1/reference-data/currencies?codes=USD", null).assertOk()
            .as(ReferenceCurrencyListResponseBody.class);

        assertEquals(1, body.getItems().size());
        var usd = body.getItems().getFirst();
        assertAll(() -> assertEquals("USD", usd.getCurrencyCode()), () -> assertEquals(2, usd.getMinorUnit()),
            () -> assertEquals(ReferenceDataStatus.ACTIVE.getCode(), usd.getStatus()));
    }

    /** 有效 CUSTOMER 申请人账号。 */
    private long customer() {
        long id = OperatorBuilder.nextId();
        Instant now = operationsMocker.now();
        operationsData.insertAccount(id, AuthenticationRealm.CUSTOMER.getCode(), OperationsAccountType.HUMAN.getCode(),
            null, null, null, now, OperationsAccountStatus.ACTIVE.getCode(), null, now);
        return id;
    }
}
