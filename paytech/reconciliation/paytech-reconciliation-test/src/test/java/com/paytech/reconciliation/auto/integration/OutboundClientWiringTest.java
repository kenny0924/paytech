package com.paytech.reconciliation.auto.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.channel.connector.api.channel.ChannelMidApi;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.reconciliation.test.base.MockTestBase;
import com.paytech.reconciliation.test.builder.ChannelMidBuilder;
import com.paytech.reconciliation.test.mocker.external.VaultMocker;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;
import com.paytech.vault.client.ProtectCommand;
import com.paytech.vault.client.TokenMode;
import com.paytech.vault.client.VaultClient;

/**
 * 出站客户端装配（A 线测试底座自检）：生产注册的 ChannelMidApi 代理与 VaultClient 经 simple discovery 打到独立 Mock HTTP 服务，
 * 按用例配置的 DTO 返回；对方 503 由 Foundation 公共出站边界转为系统错误
 * <li>业务入口（挂 MID、银行账户新建）的场景测试由 B / C 线在各自 Processor 入口覆盖；本类只证明客户端配置、服务发现与 Mock 底座可用。</li>
 *
 * @see ChannelMidApi#listChannelMids(String, String, String, String, String, Integer, Integer)
 * @see ChannelMidApi#getChannelMid(String)
 * @see VaultClient#protect(ProtectCommand)
 */
class OutboundClientWiringTest extends MockTestBase {
    private static final String TOKEN = "t" + "0".repeat(41) + "1";

    @Autowired
    private ChannelMidApi channelMidApi;

    @Autowired
    private VaultClient vaultClient;

    /** 列表按经营主体与状态过滤，详情带能力组币种。 */
    @Test
    void test_channelMidListAndDetail() {
        long midId = ReconciliationTestConstants.nextId();
        ChannelMidBuilder mid = ChannelMidBuilder.active(midId).currency("AED");
        channelConnector.mockMid(mid.summary(), mid.detail());

        var page = channelMidApi.listChannelMids(null, null, ReconciliationTestConstants.ENTITY_NO, "ACTIVE", null, 1,
            10);
        var detail = channelMidApi.getChannelMid(String.valueOf(midId));

        assertEquals(1, page.total());
        assertEquals(ReconciliationTestConstants.CHANNEL_CODE, page.items().getFirst().getChannelCode());
        assertEquals(List.of(ReconciliationTestConstants.CURRENCY, "AED"),
            detail.getCapabilities().stream().map(capability -> capability.getCurrency()).toList());
        assertEquals(List.of("list:" + ReconciliationTestConstants.ENTITY_NO + ":ACTIVE", "detail:" + midId),
            channelConnector.calls());
    }

    /** channel-connector 不可用（503 Problem）转为系统错误，不当作 MID 不存在。 */
    @Test
    void test_channelMidUnavailable() {
        long midId = ReconciliationTestConstants.nextId();
        channelConnector.failMid(String.valueOf(midId), MockHttpFault.json(503, new ApiProblemResponse("about:blank",
            "Service Unavailable", "服务暂时不可用", CommonErrorCode.SERVICE_UNAVAILABLE.getCode(), REQUEST_ID,
            "2026-10-20T00:00:00.000000Z", null)));

        assertThrows(ErrorException.class, () -> channelMidApi.getChannelMid(String.valueOf(midId)));
    }

    /** Vault Protect 以 reconciliation-service 身份发送，Mock 只记录原值摘要。 */
    @Test
    void test_vaultProtect() {
        vault.expectProtect("CASH_ACCOUNT_NUMBER_V1", "RECON_CASH_ACCOUNT", TOKEN);

        var result = vaultClient.protect(new ProtectCommand("CASH_ACCOUNT_NUMBER_V1", TokenMode.REFERENCE,
            "AE070331234567890123456", "RECON_CASH_ACCOUNT", "cash-account-1"));

        assertEquals(TOKEN, result.token());
        List<VaultMocker.ProtectCall> calls = vault.protectCalls();
        assertEquals(1, calls.size());
        assertEquals(TokenMode.REFERENCE.name(), calls.getFirst().tokenMode());
        assertEquals("cash-account-1", calls.getFirst().idempotencyKey());
    }
}
