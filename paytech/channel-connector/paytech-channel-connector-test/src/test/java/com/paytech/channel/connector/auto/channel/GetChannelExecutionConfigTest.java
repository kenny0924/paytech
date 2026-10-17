package com.paytech.payment.auto.channel;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.payment.channel.core.enums.ChannelOperation;
import com.paytech.payment.channel.core.enums.ExecutionPurpose;
import com.paytech.payment.channel.service.processor.GetChannelExecutionConfigProcessor;
import com.paytech.payment.test.base.MockTestBase;
import com.paytech.payment.test.builder.ChannelRequestBuilder;
import com.paytech.payment.test.dal.ChannelTestMapper;
import com.paytech.payment.test.mocker.ChannelDataMocker.Fixture;

/**
 * 执行配置从正式应用、真实数据库和真实 Vault HTTP Client 经过的查询场景。
 * @see GetChannelExecutionConfigProcessor#process(Object)
 */
class GetChannelExecutionConfigTest extends MockTestBase {
    @Autowired
    private GetChannelExecutionConfigProcessor query;

    @Autowired
    private ChannelTestMapper mapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Fixture fixture;

    /** 本类每例独立准备配置与合成 Vault DTO。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = data.activeFixture();
        vault.configure(fixture);
    }

    /** 真实客户端还原冻结账号配置，结果不把密钥写入默认 toString。 */
    @Test
    void readsFrozenAccountThroughVaultHttp() {
        var result = query.process(ChannelRequestBuilder.execution(fixture));
        assertEquals("NI", result.getChannelCode());
        assertEquals(fixture.getMid().getExternalMidCode(), result.getExternalMidCode());
        assertEquals("synthetic-outlet", result.getAccountConfiguration().getParameters().get("outletId"));
        assertEquals("synthetic-api-key", result.getAccountConfiguration().getCredentials().get("apiKey"));
        assertFalse(result.toString().contains("synthetic-api-key"));
        assertFalse(result.getAccountConfiguration().toString().contains("synthetic-api-key"));
        assertEquals(5, mapper.countRows());
        transactions.assertObservedOnce();
        vault.assertOnly(fixture.getAccount().getAccountVaultToken());
    }

    /** 停用后的原退款仍读取原账号。 */
    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "CLOSED"})
    void originalRefundAllowsStoppedConfiguration(String state) {
        mapper.channelStatus(state);
        mapper.accountStatus(state);
        mapper.midStatus(state);
        var request = ChannelRequestBuilder.execution(fixture);
        request.setPurpose(ExecutionPurpose.ORIGINAL_TRANSACTION);
        request.setOperation(ChannelOperation.REFUND);
        request.setCriteria(null);
        request.setAmount(null);
        assertEquals("NI", query.process(request).getChannelCode());
        vault.assertOnly(fixture.getAccount().getAccountVaultToken());
    }

    /** 原操作恢复允许关闭配置，不重新套用当前新交易能力。 */
    @Test
    void recoveryUsesOriginalConfiguration() {
        mapper.channelStatus("CLOSED");
        mapper.clearDetails();
        mapper.clearCapabilities();
        var request = ChannelRequestBuilder.execution(fixture);
        request.setPurpose(ExecutionPurpose.RECOVERY);
        request.setCriteria(null);
        request.setAmount(null);
        assertEquals("NI", query.process(request).getChannelCode());
        vault.assertOnly(fixture.getAccount().getAccountVaultToken());
    }

    /** 新支付拒绝全部未激活状态，且不执行 Reveal。 */
    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "SUSPENDED", "CLOSED"})
    void newPaymentRejectsInactiveChannel(String state) {
        mapper.channelStatus(state);
        assertThrows(BizCheckFailException.class, () -> query.process(ChannelRequestBuilder.execution(fixture)));
        vault.assertNoCalls();
    }

    /** DRAFT 原配置从未生效，不能用于任何原交易处理。 */
    @Test
    void originalRejectsDraftAccount() {
        mapper.accountStatus("DRAFT");
        var request = ChannelRequestBuilder.execution(fixture);
        request.setPurpose(ExecutionPurpose.ORIGINAL_TRANSACTION);
        request.setOperation(ChannelOperation.QUERY);
        assertThrows(BizCheckFailException.class, () -> query.process(request));
        vault.assertNoCalls();
    }

    /** 当前换绑只阻止新支付，原交易仍必须用冻结账号。 */
    @Test
    void rebindingDoesNotReplaceFrozenAccount() {
        var newAccount = data.account(fixture.getChannel().getChannelId(), "00001", "B".repeat(43));
        mapper.rebind(fixture.getMid().getMidId(), newAccount.getChannelAccountId());
        var request = ChannelRequestBuilder.execution(fixture);
        assertThrows(BizCheckFailException.class, () -> query.process(request));
        vault.assertNoCalls();
        request.setPurpose(ExecutionPurpose.ORIGINAL_TRANSACTION);
        request.setOperation(ChannelOperation.CAPTURE);
        var result = query.process(request);
        assertEquals(fixture.getAccount().getChannelAccountId().toString(), result.getAccountConfiguration().getChannelAccountId());
        vault.assertOnly(fixture.getAccount().getAccountVaultToken());
    }

    /** 错误 OE 在读取任何秘密前拒绝。 */
    @Test
    void rejectsCrossEntityBeforeReveal() {
        var request = ChannelRequestBuilder.execution(fixture);
        request.setEntityNo("00002");
        assertThrows(BizCheckFailException.class, () -> query.process(request));
        vault.assertNoCalls();
    }

    /** 外部 Payload 归属错误不能以 Token 有效为由放行。 */
    @Test
    void rejectsWrongVaultPayloadOwner() {
        vault.respond(fixture.getAccount().getAccountVaultToken(), """
            {"schemaVersion":1,"channelCode":"NI","channelAccountId":"999","entityNo":"00001",
             "parameters":{},"credentials":{}}
            """);
        assertThrows(ErrorException.class, () -> query.process(ChannelRequestBuilder.execution(fixture)));
        vault.assertOnly(fixture.getAccount().getAccountVaultToken());
    }

    /** 配置解析失败不把含秘密的错误原文带入异常。 */
    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{\"synthetic-api-key\":", "{\"schemaVersion\":\"1\"}"})
    void rejectsMalformedPayloadWithoutLeaking(String payload) {
        vault.respond(fixture.getAccount().getAccountVaultToken(), payload);
        var failure = assertThrows(ErrorException.class, () -> query.process(ChannelRequestBuilder.execution(fixture)));
        assertFalse(failure.toString().contains("synthetic-api-key"));
        assertNull(failure.getCause());
    }

    /** 明确 false 在执行前拒绝，不能因候选曾经成功就继续用旧许可。 */
    @Test
    void rechecksCurrentCapability() {
        data.capability(fixture, "VISA", "AE", "AED", "ACTION.PAY", false);
        assertThrows(BizCheckFailException.class, () -> query.process(ChannelRequestBuilder.execution(fixture)));
        vault.assertNoCalls();
    }

    /** Processor 拒绝已有事务，不挂起数据库事务后继续访问 Vault。 */
    @Test
    void refusesCallerTransaction() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            assertThrows(ErrorException.class, () -> query.process(ChannelRequestBuilder.execution(fixture))));
        vault.assertNoCalls();
    }

    /** Vault 拒绝时不返回任何配置，不换账号或重试。 */
    @Test
    void vaultDenialFailsWithoutRetry() {
        var problem = new com.paytech.foundation.httpproblem.api.ApiProblemResponse("about:blank", "Forbidden",
            "访问被拒绝", "ACCESS_DENIED", "channel-test-request", "2026-09-21T00:00:00.000000Z", null);
        vault.problem(fixture.getAccount().getAccountVaultToken(), problem);
        assertThrows(com.paytech.foundation.common.error.FailException.class,
            () -> query.process(ChannelRequestBuilder.execution(fixture)));
        vault.assertOnly(fixture.getAccount().getAccountVaultToken());
        transactions.assertObservedOnce();
        assertEquals(5, mapper.countRows());
    }

    /** Vault 协议损坏是系统错误，不当作空配置。 */
    @Test
    void malformedVaultProtocolFailsWithoutRetry() {
        vault.fault(fixture.getAccount().getAccountVaultToken(),
            com.paytech.foundation.testsupport.api.MockHttpFault.raw(200, "{broken"));
        assertThrows(ErrorException.class, () -> query.process(ChannelRequestBuilder.execution(fixture)));
        vault.assertOnly(fixture.getAccount().getAccountVaultToken());
        assertEquals(5, mapper.countRows());
    }

    /** 合法归属下仍严格拒绝非字符串、空白和越界参数。 */
    @ParameterizedTest
    @ValueSource(strings = {"{\"outletId\":1}", "{\"apiKey\":{}}", "{\"bad key\":\"value\"}",
        "{\"apiKey\":\" \"}", "{\"apiKey\":\"a\",\"apiKey\":\"b\"}"})
    void rejectsInvalidParameterShape(String parameters) {
        String payload = "{\"schemaVersion\":1,\"channelCode\":\"NI\",\"channelAccountId\":\""
            + fixture.getAccount().getChannelAccountId() + "\",\"entityNo\":\"00001\",\"parameters\":"
            + parameters + ",\"credentials\":{}}";
        vault.respond(fixture.getAccount().getAccountVaultToken(), payload);
        assertThrows(ErrorException.class, () -> query.process(ChannelRequestBuilder.execution(fixture)));
    }

    /** 金额区间闭合，最小精度外的一步也必须拒绝，不能四舍五入后通过。 */
    @ParameterizedTest
    @CsvSource({"9.99999999,false", "10.00000000,true", "20.00000000,true", "20.00000001,false"})
    void appliesInclusiveExactAmountLimits(String amount, boolean allowed) {
        data.amountBounds(fixture, new BigDecimal("10.00000000"), new BigDecimal("20.00000000"));
        var request = ChannelRequestBuilder.execution(fixture);
        request.setAmount(new BigDecimal(amount));
        if (allowed) {
            assertEquals("NI", query.process(request).getChannelCode());
            vault.assertOnly(fixture.getAccount().getAccountVaultToken());
        } else {
            assertThrows(BizCheckFailException.class, () -> query.process(request));
            vault.assertNoCalls();
        }
        assertEquals(7, mapper.countRows());
    }

    /** 账号引用缺失属于配置错误，不把 null 发往 Vault 或尝试其他账号。 */
    @Test
    void missingAccountTokenFailsBeforeReveal() {
        mapper.clearAccountToken();
        assertThrows(ErrorException.class, () -> query.process(ChannelRequestBuilder.execution(fixture)));
        vault.assertNoCalls();
    }

    /** 操作与用途必须一致，新支付不能缺少完整事实。 */
    @Test
    void rejectsInvalidPurposeAndMissingCriteria() {
        var request = ChannelRequestBuilder.execution(fixture);
        request.setOperation(ChannelOperation.REFUND);
        assertThrows(InvalidParameterException.class, () -> query.process(request));
        request.setOperation(ChannelOperation.PAY);
        request.setCriteria(null);
        assertThrows(InvalidParameterException.class, () -> query.process(request));
        vault.assertNoCalls();
    }
}
