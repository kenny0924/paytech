package com.paytech.commercial.test.mock.external.clientmaster;

import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.client.master.api.checkout.ClientMasterCheckoutApi;
import com.paytech.client.master.api.checkout.request.CheckoutCandidatesRequestBody;
import com.paytech.client.master.api.checkout.request.VerifyCheckoutCandidateRequestBody;
import com.paytech.client.master.api.checkout.response.CheckoutCandidateVerificationResponseBody;
import com.paytech.client.master.api.checkout.response.CheckoutCandidatesResponseBody;
import com.paytech.client.master.api.checkout.response.PartyProfilesResponseBody;
import com.paytech.commercial.test.mocker.clientmaster.ClientMasterCandidateMocker;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/** client-master 两段式解析接口的测试 HTTP 实现；commercial 必须以 commercial-service 调用。 */
@RestController
@TestComponent
public class MockClientMasterCheckoutApi implements ClientMasterCheckoutApi {
    private final ClientMasterCandidateMocker mocker;

    /** 与用例共享配置与记录。 */
    public MockClientMasterCheckoutApi(ClientMasterCandidateMocker mocker) {
        this.mocker = mocker;
    }

    /** 返回本例配置的候选或故障。 */
    @Override
    public CheckoutCandidatesResponseBody candidates(String callerService, CheckoutCandidatesRequestBody request) {
        prepare(callerService);
        return (CheckoutCandidatesResponseBody) reply(mocker.candidates(request.getClientId()));
    }

    /** 返回本例配置的复核结果或故障。 */
    @Override
    public CheckoutCandidateVerificationResponseBody verify(String callerService, VerifyCheckoutCandidateRequestBody request) {
        prepare(callerService);
        return (CheckoutCandidateVerificationResponseBody) reply(mocker.verification(request.getCandidate().getEntityNo()));
    }

    /** 返回本例配置的参与方资料。 */
    @Override
    public PartyProfilesResponseBody partyProfiles(String callerService, List<String> ids) {
        prepare(callerService);
        return (PartyProfilesResponseBody) reply(mocker.profiles(ids));
    }

    /** 调用方必须为 commercial-service；响应 Request ID 与 Problem Body 一致。 */
    private static void prepare(String callerService) {
        if (!"commercial-service".equals(callerService)) {
            throw new IllegalStateException("意外的调用方: " + callerService);
        }
        var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", ClientMasterCandidateMocker.REQUEST_ID);
    }

    /** DTO 原样返回；Fault 转为约定故障；未配置记为意外调用。 */
    private static Object reply(Object configured) {
        if (configured instanceof ClientMasterCandidateMocker.Fault fault) {
            throw fault.disconnect() ? MockHttpFault.disconnect() : MockHttpFault.json(fault.status(), fault.problem());
        }
        if (configured == null) {
            throw new IllegalStateException("未配置的 client-master 调用");
        }
        return configured;
    }
}
