package com.paytech.platform.support.ext.rest.dataquery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.dataquery.JointQueryApi;
import com.paytech.platform.support.api.dataquery.request.JointQueryStepRequestBody;
import com.paytech.platform.support.api.dataquery.response.JointQueryGraphResponseBody;
import com.paytech.platform.support.api.dataquery.response.JointQuerySettingsResponseBody;
import com.paytech.platform.support.api.dataquery.response.JointQueryStepResponseBody;
import com.paytech.platform.support.dataquery.service.processor.ExecuteJointQueryStepProcessor;
import com.paytech.platform.support.dataquery.service.processor.GetJointQuerySettingsProcessor;
import com.paytech.platform.support.dataquery.service.processor.PreviewJointQueryGraphProcessor;
import com.paytech.platform.support.dataquery.service.processor.request.ExecuteJointQueryStepRequest;
import com.paytech.platform.support.dataquery.service.processor.request.GetJointQuerySettingsRequest;
import com.paytech.platform.support.dataquery.service.processor.request.PreviewJointQueryGraphRequest;
import com.paytech.platform.support.ext.rest.internalauth.InternalCallerResolver;

import lombok.AllArgsConstructor;

/**
 * 联合查询的协议适配；不开事务。
 * <li>调用方经 {@link InternalCallerResolver} 确定（携带已验证内部 Token 时以 Token sub 为准），白名单校验在 Processor。</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class JointQueryController implements JointQueryApi {

    private final InternalCallerResolver internalCallers;
    private final ExecuteJointQueryStepProcessor stepProcessor;
    private final GetJointQuerySettingsProcessor settingsProcessor;
    private final PreviewJointQueryGraphProcessor previewProcessor;

    /** 解析调用方后委托。 */
    @Override
    public JointQuerySettingsResponseBody getJointQuerySettings(String callerService, String actor, String session) {
        return JointQueryConverter.settings(settingsProcessor.process(new GetJointQuerySettingsRequest(
            internalCallers.resolve(callerService), actor, session)));
    }

    /** 解析调用方后委托。 */
    @Override
    public JointQueryGraphResponseBody getJointQueryGraph(String callerService, String actor, String session,
        String entryKeyType) {
        return JointQueryConverter.graph(previewProcessor.process(new PreviewJointQueryGraphRequest(
            internalCallers.resolve(callerService), actor, session, entryKeyType)));
    }

    /** 解析调用方与本层键值后委托。 */
    @Override
    public JointQueryStepResponseBody executeJointQueryStep(String callerService, String actor, String session,
        JointQueryStepRequestBody request) {
        return JointQueryConverter.step(stepProcessor.process(new ExecuteJointQueryStepRequest(
            internalCallers.resolve(callerService), actor, session, request.getDepth(),
            JointQueryConverter.keys(request.getKeys()), request.getTemplateCodes(),
            JointQueryConverter.loadedSubjects(request.getLoadedSubjects()))));
    }
}
