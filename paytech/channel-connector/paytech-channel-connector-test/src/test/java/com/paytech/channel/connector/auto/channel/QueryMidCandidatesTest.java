package com.paytech.channel.connector.auto.channel;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.channel.connector.service.processor.QueryMidCandidatesProcessor;
import com.paytech.channel.connector.test.base.ChannelConnectorMockTestBase;
import com.paytech.channel.connector.test.builder.ChannelRequestBuilder;
import com.paytech.channel.connector.test.dal.ChannelTestMapper;
import com.paytech.channel.connector.test.mocker.ChannelDataMocker.Fixture;

/**
 * 候选检索的真实五表读取、完整维度、状态与零秘密访问场景。
 * 
 * @see QueryMidCandidatesProcessor#process(Object)
 */
class QueryMidCandidatesTest extends ChannelConnectorMockTestBase {
    @Autowired
    private QueryMidCandidatesProcessor query;

    @Autowired
    private ChannelTestMapper mapper;

    private Fixture fixture;

    /** 本类每例独立插入完整基线。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = data.activeFixture();
    }

    /** 返回完整候选与能力，且不调用 Vault 或写交易记录。 */
    @Test
    void returnsCandidateAndEffectiveCapabilities() {
        var result = query.process(ChannelRequestBuilder.candidates());
        assertEquals(1, result.size());
        var candidate = result.getFirst();
        assertEquals(fixture.getMid().getMidId(), candidate.getMidId());
        assertEquals(fixture.getChannel().getChannelId(), candidate.getChannelId());
        assertEquals(fixture.getAccount().getChannelAccountId(), candidate.getChannelAccountId());
        assertEquals(1, candidate.getCapabilities().size());
        assertEquals("ACTION.PAY", candidate.getCapabilities().getFirst().getCapabilityCode());
        assertEquals(Boolean.TRUE, candidate.getCapabilities().getFirst().getBooleanValue());
        assertEquals(5, mapper.countRows());
        vault.assertNoCalls();
    }

    /** 每次读取最新已提交状态，不缓存旧许可。 */
    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "SUSPENDED", "CLOSED"})
    void excludesInactiveChannel(String state) {
        assertEquals(1, query.process(ChannelRequestBuilder.candidates()).size());
        mapper.channelStatus(state);
        assertTrue(query.process(ChannelRequestBuilder.candidates()).isEmpty());
        vault.assertNoCalls();
    }

    /** 账号和 MID 都必须启用。 */
    @Test
    void excludesInactiveAccountAndMid() {
        mapper.accountStatus("SUSPENDED");
        assertTrue(query.process(ChannelRequestBuilder.candidates()).isEmpty());
        mapper.accountStatus("ACTIVE");
        mapper.midStatus("CLOSED");
        assertTrue(query.process(ChannelRequestBuilder.candidates()).isEmpty());
    }

    /** 指定 OE 没有配置时是空候选，不泄露其他 OE 数据。 */
    @Test
    void noCrossEntityCandidates() {
        var request = ChannelRequestBuilder.candidates();
        request.setEntityNo("00002");
        assertTrue(query.process(request).isEmpty());
        vault.assertNoCalls();
    }

    /** 关系损坏必须报错，不能伪装成正常空集合。 */
    @Test
    void rejectsBrokenOwnerRelation() {
        mapper.midEntity("00002");
        assertThrows(ErrorException.class, () -> query.process(ChannelRequestBuilder.candidates()));
        vault.assertNoCalls();
    }

    /** 具体能力明确关闭时覆盖通配支持，仍交给 Routing 决定淘汰原因。 */
    @Test
    void specificFalseOverridesWildcard() {
        data.capability(fixture, "VISA", "AE", "AED", "ACTION.PAY", false);
        var result = query.process(ChannelRequestBuilder.candidates());
        assertEquals(1, result.size());
        assertEquals(Boolean.FALSE, result.getFirst().getCapabilities().getFirst().getBooleanValue());
    }

    /** 同等精确度的两个匹配是配置冲突。 */
    @Test
    void rejectsAmbiguousConfiguration() {
        data.capability(fixture, "VISA", "*", "AED", "ACTION.PAY", true);
        data.capability(fixture, "*", "AE", "AED", "ACTION.PAY", false);
        assertThrows(ErrorException.class, () -> query.process(ChannelRequestBuilder.candidates()));
    }

    /** 任何值类型错误都在候选返回前失败。 */
    @Test
    void rejectsMultipleValueColumns() {
        mapper.corruptValue();
        assertThrows(ErrorException.class, () -> query.process(ChannelRequestBuilder.candidates()));
    }

    /** 未注册代码不能当作可用能力。 */
    @Test
    void rejectsUnknownCapability() {
        data.capability(fixture, "VISA", "AE", "AED", "UNREGISTERED.FLAG", true);
        assertThrows(ErrorException.class, () -> query.process(ChannelRequestBuilder.candidates()));
    }

    /** MCC 和币种都需匹配同一组，不能只按支付方式放行。 */
    @Test
    void noCandidateForUnmatchedDimensions() {
        var request = ChannelRequestBuilder.candidates();
        request.getCriteria().setCurrency("USD");
        assertTrue(query.process(request).isEmpty());
        request.getCriteria().setCurrency("AED");
        request.getCriteria().setMccCode("5999");
        assertTrue(query.process(request).isEmpty());
    }

    /** Processor 真正执行嵌套 Bean Validation，注解不是摆设。 */
    @Test
    void rejectsInvalidNestedInput() {
        var request = ChannelRequestBuilder.candidates();
        request.getCriteria().setCountryCode("*");
        assertThrows(InvalidParameterException.class, () -> query.process(request));
        assertThrows(InvalidParameterException.class, () -> query.process(null));
        vault.assertNoCalls();
    }

    /** 未知持久化枚举不能静默解析为空或默认启用。 */
    @Test
    void unknownStoredStatusFailsClosed() {
        mapper.channelStatus("INVALID_STATUS");
        assertThrows(RuntimeException.class, () -> query.process(ChannelRequestBuilder.candidates()));
        vault.assertNoCalls();
    }
}
