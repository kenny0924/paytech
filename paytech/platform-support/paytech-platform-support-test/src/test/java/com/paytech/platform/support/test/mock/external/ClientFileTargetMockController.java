package com.paytech.platform.support.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.client.contract.api.onboarding.ClientFileTargetApi;
import com.paytech.client.contract.api.onboarding.ClientFileTargetModels.FileTargetResponseBody;
import com.paytech.platform.support.test.mocker.external.CctFileTargetMocker;

import lombok.RequiredArgsConstructor;

/** 独立 Mock HTTP 服务以 client-master-service 实现正式文件目标授权 Wire，委托 CctFileTargetMocker；不装入 Platform 路由。 */
@RestController
@TestComponent
@RequiredArgsConstructor
public class ClientFileTargetMockController implements ClientFileTargetApi {

    private final CctFileTargetMocker targets;

    /** 写入与 Problem 体一致的响应 Request ID，再委托 Mocker 返回本例登记的授权投影或 Problem。 */
    @Override
    public FileTargetResponseBody read(String caller, String applicationId, String subjectPartyId, String accountId) {
        var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", CctFileTargetMocker.REQUEST_ID);
        return targets.read(caller, applicationId, subjectPartyId, accountId);
    }
}
