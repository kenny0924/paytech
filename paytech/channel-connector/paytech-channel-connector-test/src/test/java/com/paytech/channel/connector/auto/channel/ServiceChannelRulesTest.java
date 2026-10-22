package com.paytech.channel.connector.auto.channel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.channel.connector.api.channel.ChannelAccountApi;
import com.paytech.channel.connector.api.channel.ChannelMidApi;
import com.paytech.channel.connector.api.channel.MidCapabilityApi;
import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.ConfigureChannelAccountRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelAccountRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelMidRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateMidCapabilityRequestBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelRequestBody;
import com.paytech.channel.connector.api.cost.ChannelCostPlanApi;
import com.paytech.channel.connector.api.cost.request.CreateChannelCostPlanRequestBody;
import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.domain.ChannelAccountConfigPayload;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.test.base.AbstractChannelConfigApiTest;
import com.paytech.channel.connector.test.builder.ChannelConfigRequestBuilder;
import com.paytech.channel.connector.test.mocker.VaultMocker;

/**
 * 服务渠道的渠道规则（technique 第 5.3 节）：凭据复用技术账号但不绑定 OE、支付渠道账号必须绑定 OE、每个服务渠道最多一个 ACTIVE 凭据（409）、
 * 凭据的 Vault 负载 entityNo 为 null；服务渠道的 MID、能力组与渠道成本方案创建一律 400，且不写库。
 *
 * @see ChannelAccountApi#createChannelAccount(String, CreateChannelAccountRequestBody)
 * @see ChannelAccountApi#configureChannelAccount(String, String, ConfigureChannelAccountRequestBody)
 * @see ChannelAccountApi#changeChannelAccountStatus(String, String, ChangeChannelStatusRequestBody)
 * @see ChannelMidApi#createChannelMid(String, CreateChannelMidRequestBody)
 * @see MidCapabilityApi#createMidCapability(String, CreateMidCapabilityRequestBody)
 * @see ChannelCostPlanApi#createChannelCostPlan(String, CreateChannelCostPlanRequestBody)
 */
class ServiceChannelRulesTest extends AbstractChannelConfigApiTest {

    private Channel service;

    /** 本类公共：ACTIVE 服务渠道 GMAP。 */
    @BeforeEach
    void setUpClassMocks() {
        service = data.serviceChannel("GMAP", ChannelStatus.ACTIVE);
    }

    /** 服务渠道创建不绑定 OE 的凭据；带 OE 400；支付渠道的账号缺 OE 400。 */
    @Test
    void test_credentialWithoutEntity() {
        Channel payment = data.channel("NI", ChannelStatus.ACTIVE);

        var created = command(HttpMethod.POST, ACCOUNT_PATH, ChannelConfigRequestBuilder.credential(service.getChannelId()))
            .assertSuccess(201);
        command(HttpMethod.POST, ACCOUNT_PATH, ChannelConfigRequestBuilder.account(service.getChannelId(), ENTITY_NO))
            .assertBusinessRejected("服务渠道的凭据不绑定 OE");
        command(HttpMethod.POST, ACCOUNT_PATH, ChannelConfigRequestBuilder.credential(payment.getChannelId()))
            .assertBusinessRejected("支付渠道的技术账号必须指定 OE");

        assertAll(() -> assertTrue(created.json().path("entityNo").isNull() || created.json().path("entityNo").isMissingNode()),
            () -> assertEquals(1, testMapper.countAccountsOf(service.getChannelId())),
            () -> assertEquals(0, testMapper.countAccountsOf(payment.getChannelId())));
    }

    /** 服务渠道凭据写入 Vault 时负载的 entityNo 为 null（schemaVersion 仍为 1）。 */
    @Test
    void test_credentialPayloadEntityNull() {
        var account = data.account(service.getChannelId(), null, null, ChannelStatus.DRAFT);
        vault.expectProtect(ChannelAccountConfigPayload.DATA_TYPE, ChannelAccountConfigPayload.PURPOSE,
            VaultMocker.token("gmap-credential"));

        command(HttpMethod.PUT, ACCOUNT_PATH + "/" + account.getChannelAccountId() + "/configuration",
            ChannelConfigRequestBuilder.configuration(ChannelConfigRequestBuilder.PARAMETERS,
                ChannelConfigRequestBuilder.CREDENTIALS)).assertSuccess(200);

        var structure = vault.protectCalls().getFirst().structure();
        assertAll(() -> assertEquals(1, structure.get("schemaVersion")),
            () -> assertEquals("GMAP", structure.get("channelCode")),
            () -> assertNull(structure.get("entityNo")));
    }

    /** 同一服务渠道最多一个 ACTIVE 凭据：激活第二个 409，账号保持原状态；支付渠道不受此限制。 */
    @Test
    void test_singleActiveCredential() {
        data.account(service.getChannelId(), null, VaultMocker.token("first"), ChannelStatus.ACTIVE);
        var second = data.account(service.getChannelId(), null, VaultMocker.token("second"), ChannelStatus.DRAFT);
        Channel payment = data.channel("NI", ChannelStatus.ACTIVE);
        data.account(payment.getChannelId(), ENTITY_NO, TOKEN_A, ChannelStatus.ACTIVE);
        var paymentSecond = data.account(payment.getChannelId(), ENTITY_NO, TOKEN_B, ChannelStatus.DRAFT);

        assertConflict(command(HttpMethod.POST, ACCOUNT_PATH + "/" + second.getChannelAccountId() + "/status",
            status(ChannelStatus.ACTIVE)));
        command(HttpMethod.POST, ACCOUNT_PATH + "/" + paymentSecond.getChannelAccountId() + "/status",
            status(ChannelStatus.ACTIVE)).assertSuccess(200);

        assertEquals(ChannelStatus.DRAFT.getCode(),
            testMapper.selectAccount(second.getChannelAccountId()).get("account_status"));
    }

    /** 服务渠道没有 MID、能力与成本：MID 创建、能力组创建（MID 绕过接口写入）与成本方案创建均 400，不写库。 */
    @Test
    void test_paymentExtensionsRejected() {
        var credential = data.account(service.getChannelId(), null, VaultMocker.token("gmap"), ChannelStatus.ACTIVE);
        var mid = data.mid(service.getChannelId(), credential.getChannelAccountId(), ENTITY_NO, ChannelStatus.ACTIVE);
        int rowsBefore = testMapper.countRows();

        command(HttpMethod.POST, MID_PATH, ChannelConfigRequestBuilder.mid(service.getChannelId(),
            credential.getChannelAccountId(), "MID-1", ENTITY_NO)).assertBusinessRejected("服务渠道没有 MID");
        command(HttpMethod.POST, CAPABILITY_PATH, ChannelConfigRequestBuilder.capability(mid.getMidId(),
            ChannelConfigRequestBuilder.bool("ACTION.PAY", true)))
            .assertBusinessRejected("服务渠道没有 MID 能力");
        command(HttpMethod.POST, "/channel-connector/v1/channel-cost-plans",
            costPlan(service.getChannelId()))
            .assertBusinessRejected("服务渠道没有渠道成本");

        assertAll(() -> assertEquals(rowsBefore, testMapper.countRows()),
            () -> assertEquals(0, testMapper.countChannelCostRows()),
            () -> assertEquals(0, testMapper.countConfigChangeRows()));
    }

    /** 服务渠道的成本方案创建请求。 */
    private static CreateChannelCostPlanRequestBody costPlan(long channelId) {
        CreateChannelCostPlanRequestBody body = new CreateChannelCostPlanRequestBody();
        body.setChannelId(String.valueOf(channelId));
        body.setPlanCode("GMAP_COST");
        return body;
    }

    /** 服务渠道类型创建后不可改：修改接口不接受类型，修改后仍为 SERVICE。 */
    @Test
    void test_typeImmutable() {
        var body = new UpdateChannelRequestBody();
        body.setChannelName("改名");

        command(HttpMethod.PUT, CHANNEL_PATH + "/" + service.getChannelId(), body).assertSuccess(200)
            .assertText("channelType", ChannelType.SERVICE.getCode());

        assertEquals(ChannelType.SERVICE.getCode(), testMapper.selectChannel(service.getChannelId()).get("channel_type"));
    }
}
