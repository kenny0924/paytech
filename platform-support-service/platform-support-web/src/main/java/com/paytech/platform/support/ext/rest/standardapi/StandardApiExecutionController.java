package com.paytech.platform.support.ext.rest.standardapi;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.standardapi.StandardApiCommandApi;
import com.paytech.platform.support.api.standardapi.request.ExecuteStandardApiRequestBody;
import com.paytech.platform.support.api.standardapi.response.ExecuteStandardApiResponseBody;
import com.paytech.platform.support.integration.domain.execution.ExecutionResult;
import com.paytech.platform.support.integration.domain.execution.JsonValues;
import com.paytech.platform.support.integration.service.processor.ExecuteStandardApiProcessor;
import com.paytech.platform.support.integration.service.processor.request.ExecuteStandardApiRequest;

import lombok.AllArgsConstructor;

/** 标准 API 内部执行的协议适配：只做请求与结果转换，加载、校验与执行交给 integration 模块的 Processor。 */
@RestController
@AllArgsConstructor
public class StandardApiExecutionController implements StandardApiCommandApi {

    private final ExecuteStandardApiProcessor executeProcessor;

    /** 转换请求后委托执行用例，输出中的十进制写为字符串。 */
    @Override
    public ExecuteStandardApiResponseBody executeStandardApi(String standardApiCode,
        ExecuteStandardApiRequestBody requestBody) {
        ExecutionResult result = executeProcessor.process(
            new ExecuteStandardApiRequest(standardApiCode, requestBody.getIntegrationCode(), requestBody.getInput()));
        return new ExecuteStandardApiResponseBody(result.getExecuteStatus().getCode(),
            JsonValues.toJson(result.getOutputs()));
    }
}
