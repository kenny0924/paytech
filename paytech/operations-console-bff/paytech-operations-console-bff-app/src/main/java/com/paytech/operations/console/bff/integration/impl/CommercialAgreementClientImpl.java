package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.request;
import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.require;
import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.write;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.commercial.api.agreement.AgreementApi;
import com.paytech.commercial.api.ratecard.RateCardApi;
import com.paytech.commercial.api.ratecard.response.RateCardResponseBody;
import com.paytech.operations.console.bff.integration.CommercialAgreementClient;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AddAgreementCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementCapabilityResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.SuspendAgreementCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.ResumeAgreementCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.CapabilityPrerequisiteCheckResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementDetail;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementTermVersion;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementTransitionRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.CreateAgreementRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.RecordAgreementAcceptanceRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.ReplaceAgreementProductsRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.TerminateAgreementRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.UpdateAgreementCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/**
 * commercial 合同类接口的 HTTP 转发：每个入口只调用一次 commercial，不重试
 * <li>命令把会话 Account ID 填入 X-Gateway-Actor-Subject；不转发浏览器 Authorization、Cookie 或 Idempotency-Key。</li>
 * <li>命令回执的合同 ID 必须与路径一致（创建时与请求的合同编号一致），开通项更新、暂停与恢复回执的开通项 ID 必须与路径一致。</li>
 * <li>前置核对不携带操作人（commercial 以系统身份记录迁移），回执合同 ID 必须与路径一致。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class CommercialAgreementClientImpl implements CommercialAgreementClient {
    private final AgreementApi agreementApi;
    private final RateCardApi rateCardApi;

    /** 合同详情，回执合同 ID 与请求一致。 */
    @Override
    public AgreementDetail detail(String agreementId) {
        var detail = convert(agreementApi.getAgreement(agreementId), AgreementDetail.class);
        requireSame(agreementId, detail.getAgreementId());
        return detail;
    }

    /** 读取费率卡并只保留本合同的版本概要，丢弃价格块。 */
    @Override
    public List<AgreementTermVersion> termVersions(String clientId, String agreementId) {
        RateCardResponseBody card = rateCardApi.getRateCard(clientId, null, null);
        require(card != null && Objects.equals(clientId, card.getClientId()) && card.getAgreements() != null);
        return card.getAgreements().stream().filter(agreement -> agreementId.equals(agreement.getAgreementId()))
            .flatMap(agreement -> Objects.requireNonNullElse(agreement.getTermVersions(),
                List.<RateCardResponseBody.TermVersionRateCard>of()).stream())
            .map(CommercialAgreementClientImpl::termVersion).toList();
    }

    /** 创建合同；回执合同编号与请求一致。 */
    @Override
    public AgreementResponseBody create(OperatorContext operator, CreateAgreementRequestBody request) {
        var created = write(request, () -> agreementApi.createAgreement(operator.accountId(), request(request,
            com.paytech.commercial.api.agreement.request.CreateAgreementRequestBody.class)), AgreementResponseBody.class);
        requireSame(request.getAgreementNo(), created.getAgreementNo());
        return created;
    }

    /** 提交签署。 */
    @Override
    public AgreementResponseBody submitForSignature(OperatorContext operator, String agreementId,
        AgreementTransitionRequestBody request) {
        return sameAgreement(agreementId, write(request, () -> agreementApi.submitForSignature(operator.accountId(),
            agreementId, transition(request)), AgreementResponseBody.class));
    }

    /** 登记签署结果。 */
    @Override
    public AgreementResponseBody recordAcceptance(OperatorContext operator, String agreementId, String partyId,
        RecordAgreementAcceptanceRequestBody request) {
        return sameAgreement(agreementId, write(request, () -> agreementApi.recordAcceptance(operator.accountId(),
            agreementId, partyId, request(request,
                com.paytech.commercial.api.agreement.request.RecordAgreementAcceptanceRequestBody.class)),
            AgreementResponseBody.class));
    }

    /** 激活合同。 */
    @Override
    public AgreementResponseBody activate(OperatorContext operator, String agreementId,
        AgreementTransitionRequestBody request) {
        return sameAgreement(agreementId, write(request, () -> agreementApi.activate(operator.accountId(), agreementId,
            transition(request)), AgreementResponseBody.class));
    }

    /** 终止合同。 */
    @Override
    public AgreementResponseBody terminate(OperatorContext operator, String agreementId,
        TerminateAgreementRequestBody request) {
        return sameAgreement(agreementId, write(request, () -> agreementApi.terminate(operator.accountId(), agreementId,
            request(request, com.paytech.commercial.api.agreement.request.TerminateAgreementRequestBody.class)),
            AgreementResponseBody.class));
    }

    /** 整体替换订阅产品。 */
    @Override
    public AgreementResponseBody replaceProducts(OperatorContext operator, String agreementId,
        ReplaceAgreementProductsRequestBody request) {
        return sameAgreement(agreementId, write(request, () -> agreementApi.replaceProducts(operator.accountId(),
            agreementId, request(request,
                com.paytech.commercial.api.agreement.request.ReplaceAgreementProductsRequestBody.class)),
            AgreementResponseBody.class));
    }

    /** 新增开通项；回执合同 ID 与路径一致。 */
    @Override
    public AgreementCapabilityResponseBody addCapability(OperatorContext operator, String agreementId,
        AddAgreementCapabilityRequestBody request) {
        var result = write(request, () -> agreementApi.addCapability(operator.accountId(), agreementId, request(request,
            com.paytech.commercial.api.agreement.request.AddAgreementCapabilityRequestBody.class)),
            AgreementCapabilityResponseBody.class);
        requireSame(agreementId, result.getAgreementId());
        return result;
    }

    /** 改写开通项；回执合同与开通项 ID 与路径一致。 */
    @Override
    public AgreementCapabilityResponseBody updateCapability(OperatorContext operator, String agreementId,
        String agreementCapabilityId, UpdateAgreementCapabilityRequestBody request) {
        return sameCapability(agreementId, agreementCapabilityId, write(request, () -> agreementApi.updateCapability(
            operator.accountId(), agreementId, agreementCapabilityId, request(request,
                com.paytech.commercial.api.agreement.request.UpdateAgreementCapabilityRequestBody.class)),
            AgreementCapabilityResponseBody.class));
    }

    /** 暂停开通项；回执合同与开通项 ID 与路径一致。 */
    @Override
    public AgreementCapabilityResponseBody suspendCapability(OperatorContext operator, String agreementId,
        String agreementCapabilityId, SuspendAgreementCapabilityRequestBody request) {
        return sameCapability(agreementId, agreementCapabilityId, write(request, () -> agreementApi.suspendCapability(
            operator.accountId(), agreementId, agreementCapabilityId, request(request,
                com.paytech.commercial.api.agreement.request.SuspendAgreementCapabilityRequestBody.class)),
            AgreementCapabilityResponseBody.class));
    }

    /** 恢复开通项；回执合同与开通项 ID 与路径一致。 */
    @Override
    public AgreementCapabilityResponseBody resumeCapability(OperatorContext operator, String agreementId,
        String agreementCapabilityId, ResumeAgreementCapabilityRequestBody request) {
        return sameCapability(agreementId, agreementCapabilityId, write(request, () -> agreementApi.resumeCapability(
            operator.accountId(), agreementId, agreementCapabilityId, request(request,
                com.paytech.commercial.api.agreement.request.ResumeAgreementCapabilityRequestBody.class)),
            AgreementCapabilityResponseBody.class));
    }

    /** 前置核对；回执合同 ID 与路径一致。 */
    @Override
    public CapabilityPrerequisiteCheckResponseBody checkPrerequisites(String agreementId) {
        var result = convert(agreementApi.checkPrerequisites(agreementId), CapabilityPrerequisiteCheckResponseBody.class);
        requireSame(agreementId, result.getAgreementId());
        return result;
    }

    private static com.paytech.commercial.api.agreement.request.AgreementTransitionRequestBody transition(
        AgreementTransitionRequestBody request) {
        return request(request, com.paytech.commercial.api.agreement.request.AgreementTransitionRequestBody.class);
    }

    private static AgreementResponseBody sameAgreement(String agreementId, AgreementResponseBody result) {
        requireSame(agreementId, result.getAgreementId());
        return result;
    }

    private static AgreementCapabilityResponseBody sameCapability(String agreementId, String agreementCapabilityId,
        AgreementCapabilityResponseBody result) {
        requireSame(agreementId, result.getAgreementId());
        requireSame(agreementCapabilityId, result.getAgreementCapabilityId());
        return result;
    }

    /** 费率卡版本概要投影：只保留版本身份、状态、时点与口径。 */
    private static AgreementTermVersion termVersion(RateCardResponseBody.TermVersionRateCard source) {
        require(source != null && source.getVersionId() != null && source.getPlanId() != null);
        var version = new AgreementTermVersion();
        version.setPlanId(source.getPlanId());
        version.setTermType(source.getTermType());
        version.setVersionId(source.getVersionId());
        version.setVersionNo(source.getVersionNo());
        version.setStatus(source.getStatus());
        version.setTiming(source.getTiming());
        version.setEffectiveFrom(source.getEffectiveFrom());
        version.setEffectiveTo(source.getEffectiveTo());
        version.setPricingModel(source.getPricingModel());
        version.setFeeTaxTreatment(source.getFeeTaxTreatment());
        return version;
    }
}
