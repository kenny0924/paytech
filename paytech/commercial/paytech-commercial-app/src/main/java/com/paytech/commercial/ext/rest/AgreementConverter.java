package com.paytech.commercial.ext.rest;

import static com.paytech.commercial.ext.rest.WireValues.code;
import static com.paytech.commercial.ext.rest.WireValues.decimal;
import static com.paytech.commercial.ext.rest.WireValues.id;
import static com.paytech.commercial.ext.rest.WireValues.text;
import static com.paytech.commercial.ext.rest.WireValues.time;

import java.util.List;

import com.paytech.commercial.api.agreement.request.AddAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.request.AgreementTransitionRequestBody;
import com.paytech.commercial.api.agreement.request.CapabilityLimitEntry;
import com.paytech.commercial.api.agreement.request.CreateAgreementRequestBody;
import com.paytech.commercial.api.agreement.request.RecordAgreementAcceptanceRequestBody;
import com.paytech.commercial.api.agreement.request.ReplaceAgreementProductsRequestBody;
import com.paytech.commercial.api.agreement.request.TerminateAgreementRequestBody;
import com.paytech.commercial.api.agreement.request.UpdateAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.response.AgreementCapabilityDetail;
import com.paytech.commercial.api.agreement.response.AgreementCapabilityResponseBody;
import com.paytech.commercial.api.agreement.response.AgreementDetailResponseBody;
import com.paytech.commercial.api.agreement.response.AgreementDocumentDetail;
import com.paytech.commercial.api.agreement.response.AgreementPartyDetail;
import com.paytech.commercial.api.agreement.response.AgreementResponseBody;
import com.paytech.commercial.api.agreement.response.CapabilityLimitDetail;
import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.CapabilityLimitCode;
import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.api.enums.PricingAuthority;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.domain.AgreementDetailView;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;
import com.paytech.commercial.domain.enums.AgreementPartyAcceptanceMethod;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;
import com.paytech.commercial.service.processor.request.AddAgreementCapabilityRequest;
import com.paytech.commercial.service.processor.request.AgreementCapabilityTermsRequest;
import com.paytech.commercial.service.processor.request.AgreementTransitionRequest;
import com.paytech.commercial.service.processor.request.CreateAgreementRequest;
import com.paytech.commercial.service.processor.request.RecordAgreementAcceptanceRequest;
import com.paytech.commercial.service.processor.request.ReplaceAgreementProductsRequest;
import com.paytech.commercial.service.processor.request.TerminateAgreementRequest;
import com.paytech.commercial.service.processor.request.UpdateAgreementCapabilityRequest;
import com.paytech.commercial.service.processor.response.AgreementCapabilityResponse;
import com.paytech.commercial.service.processor.response.AgreementResponse;
import com.paytech.foundation.common.error.InvalidParameterException;

/** 合同 Wire 与内部请求 / 结果的协议转换：字符串 ID、时间与 Code 解析，不访问数据库或执行业务校验。 */
final class AgreementConverter {
    /** 纯静态工具。 */
    private AgreementConverter() {
    }

    /** 创建合同请求。 */
    static CreateAgreementRequest createRequest(String actorId, CreateAgreementRequestBody body) {
        CreateAgreementRequest request = new CreateAgreementRequest();
        request.setActorId(actor(actorId));
        request.setAgreementNo(body.getAgreementNo());
        request.setAgreementName(body.getAgreementName());
        request.setAgreementType(code(AgreementAgreementType.class, "agreementType", body.getAgreementType()));
        request.setBusinessMode(code(BusinessMode.class, "businessMode", body.getBusinessMode()));
        request.setFundMode(code(FundMode.class, "fundMode", body.getFundMode()));
        request.setPricingAuthority(code(PricingAuthority.class, "pricingAuthority", body.getPricingAuthority()));
        request.setReferralPartnerId(id("referralPartnerId", body.getReferralPartnerId()));
        request.setClientId(id("clientId", body.getClientId()));
        request.setLocalPartnerId(id("localPartnerId", body.getLocalPartnerId()));
        request.setEntityNo(body.getEntityNo());
        request.setOperatingEntityNos(body.getOperatingEntityNos() == null ? List.of() : body.getOperatingEntityNos());
        request.setEffectiveFrom(time("effectiveFrom", body.getEffectiveFrom()));
        request.setEffectiveTo(time("effectiveTo", body.getEffectiveTo()));
        request.setParties(body.getParties().stream().map(entry -> {
            CreateAgreementRequest.Party party = new CreateAgreementRequest.Party();
            party.setPartyId(id("partyId", entry.getPartyId()));
            party.setPartyProfileId(id("partyProfileId", entry.getPartyProfileId()));
            party.setPartyRole(code(AgreementPartyPartyRole.class, "partyRole", entry.getPartyRole()));
            return party;
        }).toList());
        request.setDocuments(body.getDocuments() == null ? List.of() : body.getDocuments().stream().map(entry -> {
            CreateAgreementRequest.Document document = new CreateAgreementRequest.Document();
            document.setDocumentType(code(AgreementDocumentRefDocumentType.class, "documentType", entry.getDocumentType()));
            document.setFileId(id("fileId", entry.getFileId()));
            document.setContentHash(entry.getContentHash());
            return document;
        }).toList());
        return request;
    }

    /** 提交签署与激活请求。 */
    static AgreementTransitionRequest transitionRequest(String actorId, String agreementId,
        AgreementTransitionRequestBody body) {
        AgreementTransitionRequest request = new AgreementTransitionRequest();
        request.setActorId(actor(actorId));
        request.setAgreementId(id("agreementId", agreementId));
        request.setExpectedChangeVersion(body.getExpectedChangeVersion());
        return request;
    }

    /** 终止请求。 */
    static TerminateAgreementRequest terminateRequest(String actorId, String agreementId, TerminateAgreementRequestBody body) {
        TerminateAgreementRequest request = new TerminateAgreementRequest();
        request.setActorId(actor(actorId));
        request.setAgreementId(id("agreementId", agreementId));
        request.setExpectedChangeVersion(body.getExpectedChangeVersion());
        request.setEffectiveTo(time("effectiveTo", body.getEffectiveTo()));
        request.setNoticeFileId(id("terminationNotice.fileId", body.getTerminationNotice().getFileId()));
        request.setNoticeContentHash(body.getTerminationNotice().getContentHash());
        return request;
    }

    /** 签署登记请求；Path 中的 partyId 即被登记的 Party。 */
    static RecordAgreementAcceptanceRequest acceptanceRequest(String actorId, String agreementId, String partyId,
        RecordAgreementAcceptanceRequestBody body) {
        RecordAgreementAcceptanceRequest request = new RecordAgreementAcceptanceRequest();
        request.setActorId(actor(actorId));
        request.setAgreementId(id("agreementId", agreementId));
        request.setPartyId(id("partyId", partyId));
        request.setExpectedChangeVersion(body.getExpectedChangeVersion());
        request.setAcceptanceReference(body.getAcceptanceReference());
        request.setAcceptanceMethod(code(AgreementPartyAcceptanceMethod.class, "acceptanceMethod", body.getAcceptanceMethod()));
        request.setAcceptedByActorId(id("acceptedByActorId", body.getAcceptedByActorId()));
        request.setAcceptedAt(time("acceptedAt", body.getAcceptedAt()));
        if (body.getExecutedDocument() != null) {
            request.setExecutedFileId(id("executedDocument.fileId", body.getExecutedDocument().getFileId()));
            request.setExecutedContentHash(body.getExecutedDocument().getContentHash());
        }
        return request;
    }

    /** 订阅替换请求。 */
    static ReplaceAgreementProductsRequest productsRequest(String actorId, String agreementId,
        ReplaceAgreementProductsRequestBody body) {
        ReplaceAgreementProductsRequest request = new ReplaceAgreementProductsRequest();
        request.setActorId(actor(actorId));
        request.setAgreementId(id("agreementId", agreementId));
        request.setExpectedChangeVersion(body.getExpectedChangeVersion());
        request.setProductCodes(body.getProductCodes().stream()
            .map(value -> code(ProductCode.class, "productCodes", value)).toList());
        return request;
    }

    /** 新增开通项请求。 */
    static AddAgreementCapabilityRequest addCapabilityRequest(String actorId, String agreementId,
        AddAgreementCapabilityRequestBody body) {
        AddAgreementCapabilityRequest request = new AddAgreementCapabilityRequest();
        request.setActorId(actor(actorId));
        request.setAgreementId(id("agreementId", agreementId));
        request.setExpectedChangeVersion(body.getExpectedChangeVersion());
        request.setEntityNo(body.getEntityNo());
        request.setCapabilityCode(body.getCapabilityCode());
        terms(request, body.getEffectiveFrom(), body.getEffectiveTo(), body.getRollingReserveRate(),
            body.getRollingReserveDays(), body.getLimits());
        return request;
    }

    /** 修改开通项请求。 */
    static UpdateAgreementCapabilityRequest updateCapabilityRequest(String actorId, String agreementId,
        String agreementCapabilityId, UpdateAgreementCapabilityRequestBody body) {
        UpdateAgreementCapabilityRequest request = new UpdateAgreementCapabilityRequest();
        request.setActorId(actor(actorId));
        request.setAgreementId(id("agreementId", agreementId));
        request.setAgreementCapabilityId(id("agreementCapabilityId", agreementCapabilityId));
        request.setExpectedChangeVersion(body.getExpectedChangeVersion());
        request.setStatus(code(AgreementCapabilityStatus.class, "status", body.getStatus()));
        terms(request, body.getEffectiveFrom(), body.getEffectiveTo(), body.getRollingReserveRate(),
            body.getRollingReserveDays(), body.getLimits());
        return request;
    }

    /** 合同写入结果。 */
    static AgreementResponseBody response(AgreementResponse response) {
        AgreementResponseBody body = new AgreementResponseBody();
        body.setAgreementId(Long.toString(response.getAgreementId()));
        body.setAgreementNo(response.getAgreementNo());
        body.setStatus(text(response.getStatus()));
        body.setChangeVersion(response.getChangeVersion());
        return body;
    }

    /** 开通项写入结果。 */
    static AgreementCapabilityResponseBody capabilityResponse(AgreementCapabilityResponse response) {
        AgreementCapabilityResponseBody body = new AgreementCapabilityResponseBody();
        body.setAgreementId(Long.toString(response.getAgreementId()));
        body.setAgreementCapabilityId(Long.toString(response.getAgreementCapabilityId()));
        body.setStatus(text(response.getStatus()));
        body.setChangeVersion(response.getChangeVersion());
        return body;
    }

    /** 合同详情。 */
    static AgreementDetailResponseBody detail(AgreementDetailView view) {
        AgreementDetailResponseBody body = new AgreementDetailResponseBody();
        body.setAgreementId(Long.toString(view.getAgreementId()));
        body.setAgreementNo(view.getAgreementNo());
        body.setAgreementName(view.getAgreementName());
        body.setAgreementType(text(view.getAgreementType()));
        body.setBusinessMode(text(view.getBusinessMode()));
        body.setFundMode(text(view.getFundMode()));
        body.setPricingAuthority(text(view.getPricingAuthority()));
        body.setReferralPartnerId(text(view.getReferralPartnerId()));
        body.setClientId(text(view.getClientId()));
        body.setLocalPartnerId(text(view.getLocalPartnerId()));
        body.setEntityNo(view.getEntityNo());
        body.setOperatingEntityNos(view.getOperatingEntityNos());
        body.setStatus(text(view.getStatus()));
        body.setEffectiveFrom(text(view.getEffectiveFrom()));
        body.setEffectiveTo(text(view.getEffectiveTo()));
        body.setSignedTime(text(view.getSignedTime()));
        body.setActivatedTime(text(view.getActivatedTime()));
        body.setChangeVersion(view.getChangeVersion());
        body.setParties(view.getParties().stream().map(AgreementConverter::party).toList());
        body.setDocuments(view.getDocuments().stream().map(AgreementConverter::document).toList());
        body.setProductCodes(view.getProductCodes().stream().map(ProductCode::getCode).toList());
        body.setCapabilities(view.getCapabilities().stream().map(AgreementConverter::capability).toList());
        return body;
    }

    /** 操作者请求头。 */
    static Long actor(String actorId) {
        if (actorId == null) {
            throw new InvalidParameterException("缺少操作者请求头");
        }
        return id("X-Gateway-Actor-Subject", actorId);
    }

    /** 开通项条款与约束行。 */
    static void terms(AgreementCapabilityTermsRequest request, String effectiveFrom, String effectiveTo, String rate,
        Integer days, List<CapabilityLimitEntry> limits) {
        request.setEffectiveFrom(time("effectiveFrom", effectiveFrom));
        request.setEffectiveTo(time("effectiveTo", effectiveTo));
        request.setRollingReserveRate(decimal("rollingReserveRate", rate));
        request.setRollingReserveDays(days);
        request.setLimits(limits.stream().map(entry -> {
            AgreementCapabilityTermsRequest.Limit limit = new AgreementCapabilityTermsRequest.Limit();
            limit.setLimitCode(code(CapabilityLimitCode.class, "limitCode", entry.getLimitCode()));
            limit.setLimitValue(entry.getLimitValue());
            return limit;
        }).toList());
    }

    /** 参与方详情。 */
    static AgreementPartyDetail party(AgreementDetailView.Party party) {
        AgreementPartyDetail detail = new AgreementPartyDetail();
        detail.setAgreementPartyId(Long.toString(party.getAgreementPartyId()));
        detail.setPartyId(Long.toString(party.getPartyId()));
        detail.setPartyProfileId(Long.toString(party.getPartyProfileId()));
        detail.setPartyRole(text(party.getPartyRole()));
        detail.setAcceptanceMethod(text(party.getAcceptanceMethod()));
        detail.setAcceptedByActorId(text(party.getAcceptedByActorId()));
        detail.setAcceptedAt(text(party.getAcceptedTime()));
        detail.setAcceptanceReference(party.getAcceptanceReference());
        detail.setEvidenceDocumentId(text(party.getEvidenceDocumentId()));
        return detail;
    }

    /** 文件详情。 */
    static AgreementDocumentDetail document(AgreementDetailView.Document document) {
        AgreementDocumentDetail detail = new AgreementDocumentDetail();
        detail.setAgreementDocumentId(Long.toString(document.getAgreementDocumentId()));
        detail.setDocumentType(text(document.getDocumentType()));
        detail.setFileId(Long.toString(document.getFileId()));
        detail.setContentHash(document.getContentHash());
        return detail;
    }

    /** 开通项详情。 */
    static AgreementCapabilityDetail capability(AgreementDetailView.Capability capability) {
        AgreementCapabilityDetail detail = new AgreementCapabilityDetail();
        detail.setAgreementCapabilityId(Long.toString(capability.getAgreementCapabilityId()));
        detail.setEntityNo(capability.getEntityNo());
        detail.setCapabilityCode(capability.getCapabilityCode());
        detail.setStatus(text(capability.getStatus()));
        detail.setEffectiveFrom(text(capability.getEffectiveFrom()));
        detail.setEffectiveTo(text(capability.getEffectiveTo()));
        detail.setRollingReserveRate(text(capability.getRollingReserveRate()));
        detail.setRollingReserveDays(capability.getRollingReserveDays());
        detail.setLimits(capability.getLimits().stream().map(limit -> {
            CapabilityLimitDetail item = new CapabilityLimitDetail();
            item.setLimitCode(text(limit.getLimitCode()));
            item.setLimitValue(limit.getLimitValue());
            return item;
        }).toList());
        return detail;
    }
}
