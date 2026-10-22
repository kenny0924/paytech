package com.paytech.channel.connector.ext.rest.converter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.api.enums.ExecutionPurpose;
import com.paytech.channel.connector.api.operation.request.ExecuteChannelOperationRequestBody;
import com.paytech.channel.connector.api.operation.response.ChannelOperationResponseBody;
import com.paytech.channel.connector.domain.ChannelOperationResult;
import com.paytech.channel.connector.service.processor.request.ExecuteChannelOperationRequest;
import com.paytech.channel.connector.service.processor.request.GetChannelExecutionConfigRequest;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.utils.EnumUtils;

/** 渠道操作协议转换：未知操作或用途代码转为空值，由 Processor 字段约束拒绝（400）。 */
@Component
public class ExecuteChannelOperationConverter {
    /** 转换请求。 */
    public ExecuteChannelOperationRequest convertRequest(ExecuteChannelOperationRequestBody body) {
        var target = new GetChannelExecutionConfigRequest();
        target.setEntityNo(body.getEntityNo());
        target.setChannelId(Long.valueOf(body.getChannelId()));
        target.setChannelAccountId(Long.valueOf(body.getChannelAccountId()));
        target.setMidId(Long.valueOf(body.getMidId()));
        target.setOperation(EnumUtils.getEnumByCode(ChannelOperation.class, body.getOperation()));
        target.setPurpose(EnumUtils.getEnumByCode(ExecutionPurpose.class, body.getPurpose()));
        target.setCriteria(MidCriteriaConverter.convert(body.getCriteria()));
        target.setAmount(body.getAmount() == null ? null : new BigDecimal(body.getAmount()));
        var request = new ExecuteChannelOperationRequest();
        request.setTarget(target);
        request.setInput(new LinkedHashMap<>(body.getInput()));
        return request;
    }

    /** 转换响应。 */
    public ChannelOperationResponseBody convertResponse(ChannelOperationResult result) {
        var body = new ChannelOperationResponseBody();
        body.setOutcome(result.getOutcome().getCode());
        body.setPaymentStatus(code(result.getPaymentStatus()));
        body.setRedirectUrl(result.getRedirectUrl());
        body.setNextActionType(result.getNextActionType());
        body.setAmount(result.getAmount());
        body.setCurrency(result.getCurrency());
        body.setChannelTransactionRef(result.getChannelTransactionRef());
        body.setChannelStatus(result.getChannelStatus());
        body.setFailureCode(result.getFailureCode());
        body.setFailureMessage(result.getFailureMessage());
        body.setRawOutputKeys(result.getRawOutputKeys());
        return body;
    }

    /** 稳定代码；空值原样。 */
    static String code(CodeEnum value) {
        return value == null ? null : value.getCode();
    }
}
