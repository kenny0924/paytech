package com.paytech.channel.connector.auto.channel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.channel.connector.api.ChannelConnectorHeaders;
import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;
import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.channel.connector.api.grant.ChannelCallGrantApi;
import com.paytech.channel.connector.api.grant.request.ChangeChannelCallGrantStatusRequestBody;
import com.paytech.channel.connector.api.grant.request.CreateChannelCallGrantRequestBody;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.test.base.AbstractChannelConfigApiTest;
import com.paytech.channel.connector.test.builder.ChannelConfigRequestBuilder;
import com.paytech.channel.connector.test.builder.ExternalApiRequests;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;
import com.paytech.channel.connector.test.checker.ChannelCostResponseChecker;
import com.paytech.channel.connector.test.mocker.ExternalApiMocker;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * 服务渠道调用授权：新建（服务渠道且已实现该标准 API、同一组合只有一行）、分页筛选、启停（版本 CAS、再启用沿用原行）、
 * 变更记录（可经变更记录查询接口按 CHANNEL_CALL_GRANT 查询），以及仍有 ACTIVE 授权时不能删除标准 API 实现。
 *
 * @see ChannelCallGrantApi#createChannelCallGrant(String, CreateChannelCallGrantRequestBody)
 * @see ChannelCallGrantApi#listChannelCallGrants(String, String, String, String, Integer, Integer)
 * @see ChannelCallGrantApi#changeChannelCallGrantStatus(String, String, ChangeChannelCallGrantStatusRequestBody)
 */
class ChannelCallGrantApiTest extends AbstractChannelConfigApiTest {
    private static final String GRANT_PATH = "/channel-connector/v1/channel-call-grants";
    private static final String CALLER = "commerce-service";

    @Autowired
    private ExternalApiMocker externalApi;

    private Channel gmap;

    /** 本类公共：服务渠道 GMAP 以样例流程实现标准 API PAY。 */
    @BeforeEach
    void setUpClassMocks() {
        StandardApi pay = externalApi.standardApi("PAY");
        gmap = externalApi.channel("GMAP", ChannelType.SERVICE, ChannelStatus.ACTIVE, null);
        externalApi.createOrderApi(gmap);
        externalApi.implementation(gmap, pay, ExternalApiSamples.payFlow());
    }

    /** 新建 ACTIVE 授权：201、版本 1、标准 API 编码与名称、操作人；同事务写入 CREATE 变更记录，可按 CHANNEL_CALL_GRANT 查询。 */
    @Test
    void test_createGrant() {
        var created = create(CALLER, "PAY").assertSuccess(201);

        long grantId = Long.parseLong(created.text("callGrantId"));
        var change = changes(ChangeTargetType.CHANNEL_CALL_GRANT, grantId);
        assertAll(() -> created.assertText("channelId", gmap.getChannelId().toString())
                .assertText("callerService", CALLER).assertText("standardApiCode", "PAY")
                .assertText("standardApiName", "PAY 名称").assertText("grantStatus", "ACTIVE")
                .assertText("createdBy", ACTOR_ID).assertText("updatedBy", ACTOR_ID),
            () -> assertEquals(1, created.json().path("version").asInt()),
            () -> assertEquals(1, change.size()),
            () -> assertChange(change.getFirst(), ChangeType.CREATE.getCode(), null, null));
        get("/channel-connector/v1/config-changes?targetType=CHANNEL_CALL_GRANT&targetId=" + grantId)
            .assertPage(1, 10, 1, 1);
    }

    /** 同一组合已存在（无论状态）409，原因含现有授权 ID 与状态，停用的提示再启用；不写第二行。 */
    @Test
    void test_duplicateCombination() {
        StandardApi query = externalApi.standardApi("QUERY");
        externalApi.implementation(gmap, query, ExternalApiSamples.payFlow());
        var disabled = externalApi.grant(CALLER, gmap, query, ChannelCallGrantStatus.DISABLED);
        long existing = Long.parseLong(create(CALLER, "PAY").assertSuccess(201).text("callGrantId"));

        create(CALLER, "PAY").assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode())
            .assertDetailContains("授权 ID " + existing).assertDetailContains("状态 ACTIVE");
        create(CALLER, "QUERY").assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode())
            .assertDetailContains("授权 ID " + disabled.getCallGrantId()).assertDetailContains("状态 DISABLED")
            .assertDetailContains("再启用");

        assertEquals(2, testMapper.countCallGrants());
    }

    /** 支付渠道、渠道不存在、标准 API 未被实现均 400；调用方服务名格式不符 422；不写库。 */
    @Test
    void test_rejected() {
        Channel payment = externalApi.channel("NI", ChannelType.PAYMENT, ChannelStatus.ACTIVE, null);
        externalApi.standardApi("QUERY");

        command(HttpMethod.POST, GRANT_PATH, ChannelConfigRequestBuilder.grant(payment.getChannelId(), CALLER, "PAY"))
            .assertBusinessRejected("只有服务渠道可以设置调用授权");
        command(HttpMethod.POST, GRANT_PATH, ChannelConfigRequestBuilder.grant(1L, CALLER, "PAY"))
            .assertBusinessRejected("渠道不存在");
        create(CALLER, "QUERY").assertBusinessRejected("渠道没有实现该标准 API");
        create(CALLER, "UNKNOWN").assertBusinessRejected("渠道没有实现该标准 API");
        assertValidationError(create("Commerce_Service", "PAY"));
        command(HttpMethod.POST, GRANT_PATH, null, ChannelConfigRequestBuilder.grant(gmap.getChannelId(), CALLER,
            "PAY")).assertStatus(400);

        assertEquals(0, testMapper.countCallGrants());
        assertEquals(0, testMapper.countConfigChangeRows());
    }

    /** 列表按渠道、调用方、标准 API 与状态筛选并分页，按更新时间降序。 */
    @Test
    void test_listGrants() {
        StandardApi query = externalApi.standardApi("QUERY");
        externalApi.implementation(gmap, query, ExternalApiSamples.payFlow());
        externalApi.grant(CALLER, gmap, query, ChannelCallGrantStatus.ACTIVE);
        externalApi.grant("risk-service", gmap, query, ChannelCallGrantStatus.DISABLED);
        create(CALLER, "PAY").assertSuccess(201);

        get(GRANT_PATH).assertPage(1, 10, 3, 3);
        get(GRANT_PATH + "?channelId=" + gmap.getChannelId()).assertPage(1, 10, 3, 3);
        get(GRANT_PATH + "?callerService=" + CALLER).assertPage(1, 10, 2, 2);
        get(GRANT_PATH + "?standardApiCode=QUERY").assertPage(1, 10, 2, 2);
        var disabled = get(GRANT_PATH + "?grantStatus=DISABLED").assertPage(1, 10, 1, 1).item(0);
        assertAll(() -> assertEquals("risk-service", disabled.path("callerService").asString()),
            () -> assertEquals("QUERY", disabled.path("standardApiCode").asString()));
        get(GRANT_PATH + "?channelId=1").assertPage(1, 10, 0, 0);
        get(GRANT_PATH + "?grantStatus=REVOKED").assertInvalidRequest();
        get(GRANT_PATH + "?callerService=Bad_Name").assertInvalidRequest();
    }

    /** 停用后再启用沿用同一行：版本 1 → 2 → 3，两条 STATUS 记录带原因；版本不一致 409，目标状态相同 400。 */
    @Test
    void test_disableAndEnable() {
        long grantId = Long.parseLong(create(CALLER, "PAY").assertSuccess(201).text("callGrantId"));

        status(grantId, ChannelCallGrantStatus.DISABLED, 1, "临时下线").assertSuccess(200)
            .assertText("grantStatus", "DISABLED");
        assertConflict(status(grantId, ChannelCallGrantStatus.ACTIVE, 1, null));
        status(grantId, ChannelCallGrantStatus.DISABLED, 2, null).assertBusinessRejected("调用授权已是目标状态");
        var enabled = status(grantId, ChannelCallGrantStatus.ACTIVE, 2, "恢复").assertSuccess(200);

        var changes = changes(ChangeTargetType.CHANNEL_CALL_GRANT, grantId);
        assertAll(() -> enabled.assertText("grantStatus", "ACTIVE").assertText("callGrantId", String.valueOf(grantId)),
            () -> assertEquals(3, enabled.json().path("version").asInt()),
            () -> assertEquals(3, changes.size()),
            () -> assertChange(changes.get(1), ChangeType.STATUS.getCode(), "ACTIVE", "DISABLED"),
            () -> assertEquals("临时下线", changes.get(1).get("reason")),
            () -> assertChange(changes.get(2), ChangeType.STATUS.getCode(), "DISABLED", "ACTIVE"),
            () -> assertEquals(1, testMapper.countCallGrants()));
        status(1L, ChannelCallGrantStatus.ACTIVE, 1, null).assertBusinessRejected("调用授权不存在");
    }

    /** 渠道该标准 API 仍有 ACTIVE 授权时不能删除实现（409）；停用授权后可以删除，此时不能再启用授权。 */
    @Test
    void test_implementationDeletionGuardedByActiveGrant() {
        long grantId = Long.parseLong(create(CALLER, "PAY").assertSuccess(201).text("callGrantId"));
        String implementation = "/external-api-admin/v1/integrations/GMAP/implementations/PAY";

        deleteImplementation(implementation, ExternalApiRequests.delete(1))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode()).assertDetailContains("ACTIVE 调用授权");
        status(grantId, ChannelCallGrantStatus.DISABLED, 1, null).assertSuccess(200);
        deleteImplementation(implementation, ExternalApiRequests.delete(1)).assertStatus(200);

        status(grantId, ChannelCallGrantStatus.ACTIVE, 2, null).assertBusinessRejected("渠道没有实现该标准 API");
    }

    /** 以标准操作者新建本类渠道的授权。 */
    private ChannelCostResponseChecker create(String callerService, String standardApiCode) {
        return command(HttpMethod.POST, GRANT_PATH,
            ChannelConfigRequestBuilder.grant(gmap.getChannelId(), callerService, standardApiCode));
    }

    /** 以标准操作者启停授权。 */
    private ChannelCostResponseChecker status(long grantId, ChannelCallGrantStatus target, int expectedVersion,
        String reason) {
        return command(HttpMethod.POST, GRANT_PATH + "/" + grantId + "/status",
            ChannelConfigRequestBuilder.grantStatus(target, expectedVersion, reason));
    }

    /** 经外部 API 管理接口删除标准 API 实现（DELETE 带请求体）。 */
    private ChannelCostResponseChecker deleteImplementation(String path, DeleteExternalApiConfigRequestBody body) {
        HttpHeaders headers = TestRequestHeaders.internal(REQUEST_ID);
        headers.set(ChannelConnectorHeaders.ACTOR_HEADER, ACTOR_ID);
        return new ChannelCostResponseChecker(http.exchange(HttpMethod.DELETE, path, headers, body), objectMapper,
            REQUEST_ID);
    }
}
