package com.paytech.compliance.test.mock.external;

import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.api.checkout.ClientMasterCheckoutApi;
import com.paytech.client.master.api.checkout.request.CheckoutCandidatesRequestBody;
import com.paytech.client.master.api.checkout.request.VerifyCheckoutCandidateRequestBody;
import com.paytech.client.master.api.checkout.response.CheckoutCandidateVerificationResponseBody;
import com.paytech.client.master.api.checkout.response.CheckoutCandidatesResponseBody;
import com.paytech.client.master.api.checkout.response.PartyProfilesResponseBody;
import com.paytech.compliance.test.mocker.external.PartyProfileMocker;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务实现 client-master 资料读取合同；compliance 只调用 party-profiles，其他接口记为意外。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockClientMasterCheckoutApi implements ClientMasterCheckoutApi {
    private final PartyProfileMocker mocker;

    /** compliance 不应调用。 */
    @Override
    public CheckoutCandidatesResponseBody candidates(String callerService, CheckoutCandidatesRequestBody request) {
        MockServiceChannelExecutionApi.markRequestId();
        throw mocker.unexpectedCall("checkout-candidates");
    }

    /** compliance 不应调用。 */
    @Override
    public CheckoutCandidateVerificationResponseBody verify(String callerService, VerifyCheckoutCandidateRequestBody request) {
        MockServiceChannelExecutionApi.markRequestId();
        throw mocker.unexpectedCall("checkout-candidates/verify");
    }

    /** 委托 Mocker 应答。 */
    @Override
    public PartyProfilesResponseBody partyProfiles(String callerService, List<String> ids) {
        MockServiceChannelExecutionApi.markRequestId();
        return mocker.answer(callerService, ids);
    }
}
