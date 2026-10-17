package com.paytech.channel.connector.test.builder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.paytech.channel.connector.api.candidate.request.MidCriteriaBody;
import com.paytech.channel.connector.api.candidate.request.QueryMidCandidatesRequestBody;
import com.paytech.channel.connector.api.operation.request.ExecuteChannelOperationRequestBody;

import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.api.enums.ExecutionPurpose;
import com.paytech.channel.connector.service.processor.request.*;
import com.paytech.channel.connector.test.mocker.ChannelDataMocker.Fixture;

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

    /** HTTP 合同的交易维度，与 {@link #criteria()} 相同。 */
    public static MidCriteriaBody criteriaBody() {
        var criteria = new MidCriteriaBody();
        criteria.setPaymentMethodCode("CARD");
        criteria.setSchemeCode("VISA");
        criteria.setCountryCode("AE");
        criteria.setCurrency("AED");
        criteria.setMccCode("5734");
        return criteria;
    }

    /** HTTP 合同的候选查询请求。 */
    public static QueryMidCandidatesRequestBody candidatesBody() {
        var request = new QueryMidCandidatesRequestBody();
        request.setEntityNo("00001");
        request.setCriteria(criteriaBody());
        return request;
    }

    /** HTTP 合同的新支付 PAY 请求：冻结三元组、交易维度与只含支付工具 Token 的合成标准 API 输入。 */
    public static ExecuteChannelOperationRequestBody operationBody(Fixture fixture) {
        var request = new ExecuteChannelOperationRequestBody();
        request.setEntityNo("00001");
        request.setChannelId(fixture.getChannel().getChannelId().toString());
        request.setChannelAccountId(fixture.getAccount().getChannelAccountId().toString());
        request.setMidId(fixture.getMid().getMidId().toString());
        request.setOperation(ChannelOperation.PAY.getCode());
        request.setPurpose(ExecutionPurpose.NEW_PAYMENT.getCode());
        request.setCriteria(criteriaBody());
        request.setAmount("10.00");
        var payment = new LinkedHashMap<String, Object>();
        payment.put("attemptId", "9001");
        payment.put("amount", Map.of("value", "10.00", "currency", "AED"));
        payment.put("returnUrl", "https://merchant.local.invalid/return");
        var input = new LinkedHashMap<String, Object>();
        input.put("card", Map.of("token", "synthetic-instrument-token"));
        input.put("payment", payment);
        request.setInput(input);
        return request;
    }
}
