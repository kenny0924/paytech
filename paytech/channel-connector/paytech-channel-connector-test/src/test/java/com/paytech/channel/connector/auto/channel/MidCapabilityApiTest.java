package com.paytech.channel.connector.auto.channel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.channel.connector.api.ChannelCapabilityCodes;
import com.paytech.channel.connector.api.channel.MidCapabilityApi;
import com.paytech.channel.connector.api.channel.request.ChangeMidCapabilityStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateMidCapabilityRequestBody;
import com.paytech.channel.connector.api.channel.request.MidCapabilityDetailRequestBody;
import com.paytech.channel.connector.api.channel.request.ReplaceMidCapabilityDetailsRequestBody;
import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.core.enums.CapabilityStatus;
import com.paytech.channel.connector.core.enums.CapabilityValueType;
import com.paytech.channel.connector.core.enums.ChangeTargetType;
import com.paytech.channel.connector.core.enums.ChangeType;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.test.base.AbstractChannelConfigApiTest;
import com.paytech.channel.connector.test.builder.ChannelConfigRequestBuilder;
import com.paytech.channel.connector.test.builder.ChannelRequestBuilder;
import com.paytech.channel.connector.test.checker.ChannelCostResponseChecker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * MID 能力组管理：能力目录、创建与明细类型校验、六维组合唯一、未注册代码与值域、整组替换、停用后候选查询不再命中。
 *
 * @see MidCapabilityApi#listCapabilityDefinitions()
 * @see MidCapabilityApi#createMidCapability(String, CreateMidCapabilityRequestBody)
 * @see MidCapabilityApi#replaceMidCapabilityDetails(String, String, ReplaceMidCapabilityDetailsRequestBody)
 * @see MidCapabilityApi#changeMidCapabilityStatus(String, String, ChangeMidCapabilityStatusRequestBody)
 */
class MidCapabilityApiTest extends AbstractChannelConfigApiTest {
    /** 安全模式能力代码。 */
    private static final String THREE_DS_MODE = "SECURITY.3DS_MODE";

    /** 本类公共 ACTIVE MID。 */
    private ChannelMid mid;

    /** 本类公共：ACTIVE 渠道、已配置的 ACTIVE 账号与 ACTIVE MID，均无能力组。 */
    @BeforeEach
    void setUpClassMocks() {
        PaymentChannel channel = data.channel("NI", ChannelStatus.ACTIVE);
        ChannelAccount account = data.account(channel.getChannelId(), ENTITY_NO, TOKEN_A, ChannelStatus.ACTIVE);
        mid = data.mid(channel.getChannelId(), account.getChannelAccountId(), ENTITY_NO, ChannelStatus.ACTIVE);
    }

    /** 能力目录按代码升序返回类型与 CODE 允许值。 */
    @Test
    void test_listDefinitions() {
        var definitions = get(CAPABILITY_PATH + "/definitions").assertSuccess(200).json().path("definitions");

        var threeDs = definitions.get(definitions.size() - 1);
        assertAll(() -> assertEquals(9, definitions.size()),
            () -> assertEquals(ChannelCapabilityCodes.ACTION_PREFIX + "AUTHENTICATE",
                definitions.get(0).path("capabilityCode").asString()),
            () -> assertEquals(THREE_DS_MODE, threeDs.path("capabilityCode").asString()),
            () -> assertEquals(CapabilityValueType.CODE.getCode(), threeDs.path("valueType").asString()),
            () -> assertEquals(3, threeDs.path("allowedCodes").size()));
    }

    /** 创建 ENABLED 能力组，三种类型明细逐值落库，并写入 CREATE 记录。 */
    @Test
    void test_createCapability() {
        ChannelCostResponseChecker created = create(standardDetails()).assertSuccess(201)
            .assertText("status", CapabilityStatus.ENABLED.getCode());

        long capabilityId = Long.parseLong(created.text("midCapabilityId"));
        List<Map<String, Object>> details = testMapper.selectDetails(capabilityId);
        assertAll(() -> assertEquals(4, details.size()),
            () -> assertEquals(4, created.json().path("details").size()),
            () -> assertEquals(ChannelCapabilityCodes.AMOUNT_MAX, details.get(1).get("capability_code")),
            () -> assertEquals(0, new BigDecimal("5000").compareTo((BigDecimal)details.get(1).get("decimal_value"))),
            () -> assertEquals("REQUIRED", details.get(3).get("code_value")),
            () -> assertChange(changes(ChangeTargetType.MID_CAPABILITY, capabilityId).getFirst(),
                ChangeType.CREATE.getCode(), null, null));
    }

    /** MID 内六维组合重复 409，不写第二组。 */
    @Test
    void test_duplicateScope() {
        create(standardDetails()).assertSuccess(201);

        assertConflict(create(standardDetails()));

        assertEquals(1, testMapper.countCapabilities());
    }

    /** 类型不一致、多值、未注册代码、CODE 越界、上下限颠倒、代码重复与通配 MCC 均拒绝且不写库。 */
    @Test
    void test_invalidDetails() {
        create(ChannelConfigRequestBuilder.decimal(ChannelCapabilityCodes.ACTION_PREFIX + "PAY", "1"))
            .assertBusinessRejected("能力值与目录类型不一致");
        create(ChannelConfigRequestBuilder.bool("ACTION.UNKNOWN", true)).assertBusinessRejected("能力代码未注册");
        create(ChannelConfigRequestBuilder.code(THREE_DS_MODE, "MAYBE")).assertBusinessRejected("不在允许范围");
        create(ChannelConfigRequestBuilder.decimal(ChannelCapabilityCodes.AMOUNT_MIN, "10"),
            ChannelConfigRequestBuilder.decimal(ChannelCapabilityCodes.AMOUNT_MAX, "1"))
            .assertBusinessRejected("最小金额不能大于最大金额");
        create(ChannelConfigRequestBuilder.bool(ChannelCapabilityCodes.ACTION_PREFIX + "PAY", true),
            ChannelConfigRequestBuilder.bool(ChannelCapabilityCodes.ACTION_PREFIX + "PAY", false))
            .assertBusinessRejected("能力代码重复");
        CreateMidCapabilityRequestBody wildcardMcc = ChannelConfigRequestBuilder.capability(mid.getMidId(),
            ChannelConfigRequestBuilder.bool(ChannelCapabilityCodes.ACTION_PREFIX + "PAY", true));
        wildcardMcc.setMccCode("*");
        assertValidationError(command(HttpMethod.POST, CAPABILITY_PATH, wildcardMcc));

        assertEquals(0, testMapper.countCapabilities());
        assertEquals(0, testMapper.countConfigChangeRows());
    }

    /** 已关闭 MID 拒绝新增能力组。 */
    @Test
    void test_closedMidRejected() {
        testMapper.rewriteMid(mid.getMidId(), ChannelStatus.CLOSED.getCode(), mid.getUpdateTime());

        create(standardDetails()).assertBusinessRejected("MID 已关闭");
    }

    /** 整组替换：原明细全部删除，写入新明细，记 UPDATE。 */
    @Test
    void test_replaceDetails() {
        long capabilityId = Long.parseLong(create(standardDetails()).assertSuccess(201).text("midCapabilityId"));

        command(HttpMethod.PUT, CAPABILITY_PATH + "/" + capabilityId + "/details", ChannelConfigRequestBuilder.replace(
            ChannelConfigRequestBuilder.bool(ChannelCapabilityCodes.ACTION_PREFIX + "REFUND", true)))
            .assertSuccess(200);

        List<Map<String, Object>> details = testMapper.selectDetails(capabilityId);
        assertAll(() -> assertEquals(1, details.size()),
            () -> assertEquals(ChannelCapabilityCodes.ACTION_PREFIX + "REFUND", details.getFirst().get("capability_code")),
            () -> assertChange(changes(ChangeTargetType.MID_CAPABILITY, capabilityId).get(1),
                ChangeType.UPDATE.getCode(), null, null));
    }

    /** 停用后候选查询不再返回该 MID；重复停用业务拒绝。 */
    @Test
    void test_disableRemovesCandidate() {
        long capabilityId = Long.parseLong(create(standardDetails()).assertSuccess(201).text("midCapabilityId"));
        assertEquals(1, candidates().json().path("candidates").size());

        command(HttpMethod.POST, CAPABILITY_PATH + "/" + capabilityId + "/status", disable()).assertSuccess(200)
            .assertText("status", CapabilityStatus.DISABLED.getCode());

        assertAll(() -> assertEquals(0, candidates().json().path("candidates").size()),
            () -> assertEquals(CapabilityStatus.DISABLED.getCode(), testMapper.selectCapabilityStatus(capabilityId)),
            () -> assertChange(changes(ChangeTargetType.MID_CAPABILITY, capabilityId).get(1),
                ChangeType.STATUS.getCode(), CapabilityStatus.ENABLED.getCode(), CapabilityStatus.DISABLED.getCode()));
        command(HttpMethod.POST, CAPABILITY_PATH + "/" + capabilityId + "/status", disable())
            .assertBusinessRejected("已是目标状态");
    }

    /** 标准明细：PAY、上下限与 3DS 模式。 */
    static List<MidCapabilityDetailRequestBody> standardDetails() {
        return List.of(ChannelConfigRequestBuilder.bool(ChannelCapabilityCodes.ACTION_PREFIX + "PAY", true),
            ChannelConfigRequestBuilder.decimal(ChannelCapabilityCodes.AMOUNT_MIN, "1.50"),
            ChannelConfigRequestBuilder.decimal(ChannelCapabilityCodes.AMOUNT_MAX, "5000"),
            ChannelConfigRequestBuilder.code(THREE_DS_MODE, "REQUIRED"));
    }

    /** 以本类 MID 与标准六维创建能力组。 */
    ChannelCostResponseChecker create(List<MidCapabilityDetailRequestBody> details) {
        return create(details.toArray(MidCapabilityDetailRequestBody[]::new));
    }

    /** 以本类 MID 与标准六维创建能力组。 */
    ChannelCostResponseChecker create(MidCapabilityDetailRequestBody... details) {
        return command(HttpMethod.POST, CAPABILITY_PATH, ChannelConfigRequestBuilder.capability(mid.getMidId(), details));
    }

    /** 停用请求体。 */
    static ChangeMidCapabilityStatusRequestBody disable() {
        ChangeMidCapabilityStatusRequestBody body = new ChangeMidCapabilityStatusRequestBody();
        body.setTargetStatus(CapabilityStatus.DISABLED.getCode());
        return body;
    }

    /** 以本类 OE 与标准交易维度经真实 HTTP 查询候选。 */
    ChannelCostResponseChecker candidates() {
        var body = ChannelRequestBuilder.candidatesBody();
        body.setEntityNo(ENTITY_NO);
        return new ChannelCostResponseChecker(http.post("/channel-connector/v1/mid-candidates",
            TestRequestHeaders.internal(REQUEST_ID), body), objectMapper,
            REQUEST_ID).assertSuccess(200);
    }
}
