package com.paytech.platform.support.auto.ext.http.operations.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;
import com.paytech.platform.support.accesscontrol.domain.webhookmanagement.WebhookManagementPermissions;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.enums.WebhookEventType;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookClientResponseBody;
import com.paytech.platform.support.test.base.WebhookTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.webhook.WebhookFixture;

import tools.jackson.core.type.TypeReference;

/**
 * 运营按 Client ID 或名称搜索 Client 并查看端点数量。
 *
 * @see com.paytech.platform.support.api.webhookmanagement.WebhookManagementApi#clients(String, String, String, Integer, Integer)
 */
class WebhookClientSearchTest extends WebhookTestBase {

    private static final String NAME = "Acme Webhook_Search";

    private Operator reader;
    private Operator viewer;

    /** 只读人员只有 WEBHOOK_READ；viewer 没有 Webhook 权限。两个测试租户中一个改名并有两个端点。 */
    @BeforeEach
    void setUpClients() {
        reader = operationsMocker.operator(OperatorBuilder.administrator()
            .roles(operationsMocker.customRole("WEBHOOK_READER", WebhookManagementPermissions.READ)));
        viewer = operationsMocker.operator(OperatorBuilder.viewer());
        webhookData.renameTenant(WebhookFixture.TENANT_ID, NAME);
        var types = WebhookFixture.eventTypes(WebhookEventType.ORDER_PAID);
        fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("a"), types);
        fixture.endpoint(WebhookFixture.TENANT_ID, fixture.receiverUrl("b"), types);
    }

    /** 名称包含匹配（通配符按字面）、Client ID 精确匹配；端点数量与状态随结果返回。 */
    @Test
    void test_normalFlow() {
        var byName = search("?keyword=Webhook_Search", reader);
        assertEquals(1, byName.total());
        var item = byName.items().getFirst();
        assertEquals(Long.toString(WebhookFixture.CLIENT_ID), item.getClientId());
        assertEquals(NAME, item.getClientName());
        assertEquals(OperationsTenantOwnerStatus.ACTIVE.getCode(), item.getOwnerStatus());
        assertEquals(OperationsTenantSecurityStatus.ACTIVE.getCode(), item.getSecurityStatus());
        assertEquals(2, item.getEndpointCount());

        var byId = search("?keyword=" + WebhookFixture.OTHER_CLIENT_ID, reader);
        assertEquals(1, byId.total());
        assertEquals(Long.toString(WebhookFixture.OTHER_CLIENT_ID), byId.items().getFirst().getClientId());
        assertEquals(0, byId.items().getFirst().getEndpointCount());

        assertEquals(0, search("?keyword=Acme_Webhook", reader).total());
        assertEquals(0, search("?keyword=" + WebhookFixture.UNKNOWN_CLIENT_ID, reader).total());
        var all = search("?page=1&size=10", reader);
        assertTrue(all.total() >= 2);
        assertEquals(10, all.size());
    }

    /** 分页参数非法或关键字超长返回 400；没有 WEBHOOK_READ 返回 403。 */
    @Test
    void test_invalidParameter() {
        get(ADMIN + "/clients?size=7", reader).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(ADMIN + "/clients?keyword=" + "a".repeat(256), reader).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(ADMIN + "/clients", viewer).assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
    }

    /** 搜索 Client 并解析分页结果。 */
    PagingResponseBody<WebhookClientResponseBody> search(String query, Operator operator) {
        return get(ADMIN + "/clients" + query, operator).assertOk()
            .as(new TypeReference<PagingResponseBody<WebhookClientResponseBody>>() {});
    }
}
