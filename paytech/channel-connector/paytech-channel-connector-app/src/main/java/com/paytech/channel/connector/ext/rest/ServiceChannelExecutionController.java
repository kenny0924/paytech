package com.paytech.channel.connector.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.serviceexecution.ServiceChannelExecutionApi;
import com.paytech.channel.connector.api.serviceexecution.request.ExecuteServiceChannelRequestBody;
import com.paytech.channel.connector.api.serviceexecution.response.ServiceChannelExecutionResponseBody;
import com.paytech.channel.connector.protocol.domain.execution.ExecutionResult;
import com.paytech.channel.connector.protocol.domain.execution.JsonValues;
import com.paytech.channel.connector.service.processor.ExecuteServiceChannelProcessor;
import com.paytech.channel.connector.service.processor.request.ExecuteServiceChannelRequest;

import lombok.AllArgsConstructor;

/** 服务渠道通用执行入口的协议适配：确认调用方后委托 core 的执行 Processor，输出中的十进制写为字符串。 */
@RestController
@AllArgsConstructor
public class ServiceChannelExecutionController implements ServiceChannelExecutionApi {
    private final ChannelCallerResolver callerResolver;
    private final ExecuteServiceChannelProcessor executeServiceChannelProcessor;

    /** 确认调用方并执行。 */
    @Override
    public ServiceChannelExecutionResponseBody execute(String callerService,
        ExecuteServiceChannelRequestBody requestBody) {
        ExecutionResult result = executeServiceChannelProcessor.process(new ExecuteServiceChannelRequest(
            callerResolver.resolve(callerService), requestBody.getChannelCode(), requestBody.getStandardApiCode(),
            requestBody.getInput()));
        ServiceChannelExecutionResponseBody body = new ServiceChannelExecutionResponseBody();
        body.setExecuteStatus(result.getExecuteStatus().getCode());
        body.setOutputs(JsonValues.toJson(result.getOutputs()));
        return body;
    }
}
