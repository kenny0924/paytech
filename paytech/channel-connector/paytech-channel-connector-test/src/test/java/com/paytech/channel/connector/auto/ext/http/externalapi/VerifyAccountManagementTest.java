package com.paytech.channel.connector.auto.ext.http.externalapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.channel.connector.api.externalapi.IntegrationVerifyManagementApi;
import com.paytech.channel.connector.api.externalapi.request.CreateVerifyAccountRequestBody;
import com.paytech.channel.connector.api.externalapi.request.CreateVerifyCaseRequestBody;
import com.paytech.channel.connector.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.channel.connector.api.externalapi.request.UpdateVerifyAccountRequestBody;
import com.paytech.channel.connector.api.externalapi.response.VerifyAccountResponseBody;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.test.base.ExternalApiTestBase;
import com.paytech.channel.connector.test.builder.ExternalApiFixtures;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;

/**
 * 验证账号管理：创建与详情（参数与沙箱凭据原样返回、保持顺序）、列表不带材料、修改递增版本、编码重复、
 * 参数与凭据约束的 422 位置、旧版本 409、被用例引用时删除保护、操作者请求头，以及变更记录不含沙箱凭据。
 *
 * @see IntegrationVerifyManagementApi#listVerifyAccounts(String)
 * @see IntegrationVerifyManagementApi#getVerifyAccount(String, String)
 * @see IntegrationVerifyManagementApi#createVerifyAccount(String, String, CreateVerifyAccountRequestBody)
 * @see IntegrationVerifyManagementApi#updateVerifyAccount(String, String, String, UpdateVerifyAccountRequestBody)
 * @see IntegrationVerifyManagementApi#deleteVerifyAccount(String, String, String, DeleteExternalApiConfigRequestBody)
 */
class VerifyAccountManagementTest extends ExternalApiTestBase {

    private static final String PATH = BASE + "/integrations/DEMO/verify-accounts";

    private Integration demo;

    /** 本类公共：一个接入方 DEMO。 */
    @BeforeEach
    void setUpClassMocks() {
        demo = externalApi.integration("DEMO");
    }

    /** 创建后详情返回全部材料并保持顺序，列表只返回身份；修改递增版本；变更记录只有摘要、不含沙箱凭据。 */
    @Test
    void test_createGetListUpdate() {
        assertMutation(post(PATH, create("MAIN")), "INTEGRATION_VERIFY_ACCOUNT", "DEMO/MAIN", 1);

        var detail = get(PATH + "/MAIN").assertOk().as(VerifyAccountResponseBody.class);
        assertAll(() -> assertEquals(List.of("appId", "merchantNo"), List.copyOf(detail.getParameters().keySet())),
            () -> assertEquals(Map.of("merchantPrivateKey", "sandbox-private"), detail.getCredentials()),
            () -> assertEquals(List.of(), detail.getReferencedCaseCodes()), () -> assertEquals(1, detail.getVersion()));
        var list = get(PATH).assertOk().json();
        assertAll(() -> assertEquals("MAIN", list.get(0).path("accountCode").asString()),
            () -> assertFalse(list.get(0).has("credentials")));

        assertMutation(put(PATH + "/MAIN", update(1, Map.of("merchantPrivateKey", "rotated"))),
            "INTEGRATION_VERIFY_ACCOUNT", "DEMO/MAIN", 2);
        assertEquals(Map.of("merchantPrivateKey", "rotated"),
            get(PATH + "/MAIN").assertOk().as(VerifyAccountResponseBody.class).getCredentials());
        long accountId = externalApiData.verifyAccountId("DEMO", "MAIN");
        var created = assertChange(ChangeTargetType.VERIFY_ACCOUNT, accountId, ChangeType.CREATE, "DEMO/MAIN", 0, 1,
            "沙箱验证");
        var updated = assertChange(ChangeTargetType.VERIFY_ACCOUNT, accountId, ChangeType.UPDATE, "DEMO/MAIN", 1, 2,
            "轮换");
        assertAll(() -> assertFalse(created.toString().contains("sandbox-private"), "变更摘要不应包含沙箱凭据"),
            () -> assertFalse(updated.toString().contains("rotated"), "变更摘要不应包含沙箱凭据"));
    }

    /** 编码重复 400；名称或值不合法 422 并指出位置；旧版本 409。 */
    @Test
    void test_rejections() {
        post(PATH, create("MAIN")).assertOk();
        post(PATH, create("MAIN")).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        var parameters = new LinkedHashMap<String, String>();
        parameters.put("1bad", "x");
        parameters.put("blank", " ");
        post(PATH, new CreateVerifyAccountRequestBody("OTHER", "账号", "PAYERMAX", "A1", "E1", parameters,
            Map.of("key", "x".repeat(8193)), "原因")).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("parameters.1bad").assertFieldError("parameters.blank")
            .assertFieldError("credentials.key");

        put(PATH + "/MAIN", update(7, Map.of("k", "v"))).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
    }

    /** 被用例引用时不能删除；用例删除后可以删除；写命令带非法操作者请求头时 400。 */
    @Test
    void test_deleteProtectionAndActorHeader() {
        post(PATH, create("MAIN")).assertOk();
        long accountId = externalApiData.verifyAccountId("DEMO", "MAIN");
        externalApi.implementation(demo, externalApi.standardApi("PAY"), ExternalApiFixtures.flow("call-create-order"));
        externalApi.integrationApi(demo, "CALL", ExternalApiSamples.createOrderDefinition());
        post(BASE + "/integrations/DEMO/verify-cases", new CreateVerifyCaseRequestBody("CASE_A", "用例", null,
            "MAIN", ExternalApiSamples.object("""
                {"s":[{"id":"pay","standardApiCode":"PAY","input":{"paymentContext":{"attemptId":"A1","amount":"1.00",
                  "vaultToken":"T"}},"expect":{"executeStatus":"SUCCESS"}}]}""").get("s"), "原因")).assertOk();
        assertEquals(List.of("CASE_A"),
            get(PATH + "/MAIN").assertOk().as(VerifyAccountResponseBody.class).getReferencedCaseCodes());

        delete(PATH + "/MAIN", new DeleteExternalApiConfigRequestBody(1, "清理"))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        delete(BASE + "/integrations/DEMO/verify-cases/CASE_A", new DeleteExternalApiConfigRequestBody(1, "清理"))
            .assertOk();
        assertMutation(delete(PATH + "/MAIN", new DeleteExternalApiConfigRequestBody(1, "清理")),
            "INTEGRATION_VERIFY_ACCOUNT", "DEMO/MAIN", 1);
        get(PATH + "/MAIN").assertProblem(404, CommonErrorCode.NOT_FOUND);

        assertChange(ChangeTargetType.VERIFY_ACCOUNT, accountId, ChangeType.DELETE, "DEMO/MAIN", 1, 1, "清理");

        call(HttpMethod.POST, PATH, "1.5", create("RO")).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "/RO").assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 带参数与沙箱凭据的验证账号创建请求。 */
    private static CreateVerifyAccountRequestBody create(String code) {
        var parameters = new LinkedHashMap<String, String>();
        parameters.put("appId", "app");
        parameters.put("merchantNo", "SP01");
        return new CreateVerifyAccountRequestBody(code, "主账号", "PAYERMAX", "A1", "E1", parameters,
            Map.of("merchantPrivateKey", "sandbox-private"), "沙箱验证");
    }

    /** 按期望版本替换凭据的验证账号修改请求。 */
    private static UpdateVerifyAccountRequestBody update(int version, Map<String, String> credentials) {
        return new UpdateVerifyAccountRequestBody("主账号", "PAYERMAX", "A1", "E1", Map.of("appId", "app"), credentials,
            version, "轮换");
    }
}
