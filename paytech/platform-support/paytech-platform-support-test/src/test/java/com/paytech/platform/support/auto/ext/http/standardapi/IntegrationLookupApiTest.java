package com.paytech.platform.support.auto.ext.http.standardapi;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.standardapi.IntegrationLookupApi;
import com.paytech.platform.support.api.standardapi.response.IntegrationExistenceResponseBody;
import com.paytech.platform.support.test.base.ExternalApiTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;

/**
 * 接入方存在性内部查询（internal-auth=OFF）：无运营会话的内部调用，存在与不存在均 200，编码格式非法被入口拒绝。
 * internal-auth=REQUIRED 下的 Token 校验见 IntegrationLookupInternalAuthTest。
 *
 * @see IntegrationLookupApi#integrationExistence(String)
 */
class IntegrationLookupApiTest extends ExternalApiTestBase {

    private static final String DEMO = "DEMO";

    /** 本类公共：登记接入方 DEMO。 */
    @BeforeEach
    void setUpClassMocks() {
        externalApi.integration(DEMO);
    }

    /** 已登记的接入方：exists=true 并原样回显编码。 */
    @Test
    void test_normalFlow() {
        assertExistence(DEMO, true);
    }

    /** 未登记的编码：仍返回 200，exists=false。 */
    @Test
    void test_noDataFound() {
        assertExistence("UNKNOWN_CHANNEL", false);
    }

    /** 小写、首字符为数字或超长的编码：方法校验拒绝，不查询数据库。 */
    @Test
    void test_invalidParameter() {
        for (String code : new String[] {"demo", "1DEMO", "D", "D" + "X".repeat(64)}) {
            lookup(code).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        }
    }

    /** 查询并断言响应内容。 */
    private void assertExistence(String code, boolean expected) {
        PlatformResponseChecker checker = lookup(code).assertOk();
        var body = checker.as(IntegrationExistenceResponseBody.class);
        assertAll(() -> assertEquals(code, body.getIntegrationCode()),
            () -> assertEquals(expected, body.isExists()),
            () -> assertEquals(Set.of("integrationCode", "exists"), Set.copyOf(checker.json().propertyNames())));
    }

    /** 无可信身份的 GET 调用。 */
    private PlatformResponseChecker lookup(String code) {
        return anonymous(HttpMethod.GET, "/standard-apis/v1/integrations/" + code + "/existence", null);
    }
}
