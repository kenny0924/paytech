package com.paytech.commercial.ext.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.commercial.api.agreement.AgreementApi;
import com.paytech.commercial.api.agreement.request.AddAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.request.AgreementTransitionRequestBody;
import com.paytech.commercial.api.agreement.request.CreateAgreementRequestBody;
import com.paytech.commercial.api.agreement.request.RecordAgreementAcceptanceRequestBody;
import com.paytech.commercial.api.agreement.request.ReplaceAgreementProductsRequestBody;
import com.paytech.commercial.api.agreement.request.TerminateAgreementRequestBody;
import com.paytech.commercial.api.agreement.request.UpdateAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.response.AgreementCapabilityResponseBody;
import com.paytech.commercial.api.agreement.response.AgreementDetailResponseBody;
import com.paytech.commercial.api.agreement.response.AgreementResponseBody;
import com.paytech.commercial.domainservice.service.AgreementService;
import com.paytech.commercial.service.processor.ActivateAgreementProcessor;
import com.paytech.commercial.service.processor.AddAgreementCapabilityProcessor;
import com.paytech.commercial.service.processor.CreateAgreementProcessor;
import com.paytech.commercial.service.processor.RecordAgreementAcceptanceProcessor;
import com.paytech.commercial.service.processor.ReplaceAgreementProductsProcessor;
import com.paytech.commercial.service.processor.SubmitAgreementForSignatureProcessor;
import com.paytech.commercial.service.processor.TerminateAgreementProcessor;
import com.paytech.commercial.service.processor.UpdateAgreementCapabilityProcessor;

import lombok.AllArgsConstructor;

/** 合同运营写入与详情的入站适配（technique.md §2.2）：只做协议转换与委托，写入走 Processor，详情直接委托 AgreementService。 */
@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@AllArgsConstructor
public class AgreementController implements AgreementApi {
    private final AgreementService agreementService;
    private final CreateAgreementProcessor createAgreementProcessor;
    private final ActivateAgreementProcessor activateAgreementProcessor;
    private final TerminateAgreementProcessor terminateAgreementProcessor;
    private final AddAgreementCapabilityProcessor addAgreementCapabilityProcessor;
    private final ReplaceAgreementProductsProcessor replaceAgreementProductsProcessor;
    private final RecordAgreementAcceptanceProcessor recordAgreementAcceptanceProcessor;
    private final UpdateAgreementCapabilityProcessor updateAgreementCapabilityProcessor;
    private final SubmitAgreementForSignatureProcessor submitAgreementForSignatureProcessor;

    /** 创建合同。 */
    @Override
    public AgreementResponseBody createAgreement(String actorId, CreateAgreementRequestBody body) {
        return AgreementConverter.response(createAgreementProcessor.process(AgreementConverter.createRequest(actorId, body)));
    }

    /** 合同详情。 */
    @Override
    public AgreementDetailResponseBody getAgreement(String agreementId) {
        return AgreementConverter.detail(agreementService.getAgreementDetail(WireValues.id("agreementId", agreementId)));
    }

    /** 提交签署。 */
    @Override
    public AgreementResponseBody submitForSignature(String actorId, String agreementId, AgreementTransitionRequestBody body) {
        return AgreementConverter.response(submitAgreementForSignatureProcessor.process(
            AgreementConverter.transitionRequest(actorId, agreementId, body)));
    }

    /** 签署登记。 */
    @Override
    public AgreementResponseBody recordAcceptance(String actorId, String agreementId, String partyId,
        RecordAgreementAcceptanceRequestBody body) {
        return AgreementConverter.response(recordAgreementAcceptanceProcessor.process(
            AgreementConverter.acceptanceRequest(actorId, agreementId, partyId, body)));
    }

    /** 激活。 */
    @Override
    public AgreementResponseBody activate(String actorId, String agreementId, AgreementTransitionRequestBody body) {
        return AgreementConverter.response(activateAgreementProcessor.process(
            AgreementConverter.transitionRequest(actorId, agreementId, body)));
    }

    /** 终止。 */
    @Override
    public AgreementResponseBody terminate(String actorId, String agreementId, TerminateAgreementRequestBody body) {
        return AgreementConverter.response(terminateAgreementProcessor.process(
            AgreementConverter.terminateRequest(actorId, agreementId, body)));
    }

    /** 替换订阅产品。 */
    @Override
    public AgreementResponseBody replaceProducts(String actorId, String agreementId,
        ReplaceAgreementProductsRequestBody body) {
        return AgreementConverter.response(replaceAgreementProductsProcessor.process(
            AgreementConverter.productsRequest(actorId, agreementId, body)));
    }

    /** 新增开通项。 */
    @Override
    public AgreementCapabilityResponseBody addCapability(String actorId, String agreementId,
        AddAgreementCapabilityRequestBody body) {
        return AgreementConverter.capabilityResponse(addAgreementCapabilityProcessor.process(
            AgreementConverter.addCapabilityRequest(actorId, agreementId, body)));
    }

    /** 修改开通项。 */
    @Override
    public AgreementCapabilityResponseBody updateCapability(String actorId, String agreementId,
        String agreementCapabilityId, UpdateAgreementCapabilityRequestBody body) {
        return AgreementConverter.capabilityResponse(updateAgreementCapabilityProcessor.process(
            AgreementConverter.updateCapabilityRequest(actorId, agreementId, agreementCapabilityId, body)));
    }
}
