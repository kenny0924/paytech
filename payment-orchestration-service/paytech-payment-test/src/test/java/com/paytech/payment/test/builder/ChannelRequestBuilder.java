package com.paytech.payment.test.builder;

import java.math.BigDecimal;

import com.paytech.payment.channel.core.enums.ChannelOperation;
import com.paytech.payment.channel.core.enums.ExecutionPurpose;
import com.paytech.payment.channel.service.processor.request.*;
import com.paytech.payment.test.mocker.ChannelDataMocker.Fixture;

/** 只组装可直接修改的请求；不会把测试刻意设置的非法值补回默认值。 */
public final class ChannelRequestBuilder {
    /** 默认交易的消费者国家和 MCC 明确，不从 MID 反推。 */
    public static MidCriteria criteria() {
        var criteria = new MidCriteria();
        criteria.setPaymentMethodCode("CARD");
        criteria.setSchemeCode("VISA");
        criteria.setCountryCode("AE");
        criteria.setCurrency("AED");
        criteria.setMccCode("5734");
        return criteria;
    }

    /** 创建候选检索请求。 */
    public static QueryMidCandidatesRequest candidates() {
        var request = new QueryMidCandidatesRequest();
        request.setEntityNo("00001");
        request.setCriteria(criteria());
        return request;
    }

    /** 创建使用原交易冻结身份的新支付执行配置请求。 */
    public static GetChannelExecutionConfigRequest execution(Fixture fixture) {
        var request = new GetChannelExecutionConfigRequest();
        request.setEntityNo("00001");
        request.setChannelId(fixture.getChannel().getChannelId());
        request.setChannelAccountId(fixture.getAccount().getChannelAccountId());
        request.setMidId(fixture.getMid().getMidId());
        request.setOperation(ChannelOperation.PAY);
        request.setPurpose(ExecutionPurpose.NEW_PAYMENT);
        request.setCriteria(criteria());
        request.setAmount(new BigDecimal("10.00"));
        return request;
    }
}
