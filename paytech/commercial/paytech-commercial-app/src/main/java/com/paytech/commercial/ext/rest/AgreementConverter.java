package com.paytech.commercial.ext.rest;

import static com.paytech.commercial.ext.rest.WireValues.code;
import static com.paytech.commercial.ext.rest.WireValues.decimal;
import static com.paytech.commercial.ext.rest.WireValues.id;
import static com.paytech.commercial.ext.rest.WireValues.text;
import static com.paytech.commercial.ext.rest.WireValues.time;

import java.util.List;

import com.paytech.commercial.api.agreement.request.AddAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.request.AgreementTransitionRequestBody;
import com.paytech.commercial.api.agreement.request.CapabilityConditionEntry;
import com.paytech.commercial.api.agreement.request.CreateAgreementRequestBody;
import com.paytech.commercial.api.agreement.request.RecordAgreementAcceptanceRequestBody;
import com.paytech.commercial.api.agreement.request.ReplaceAgreementProductsRequestBody;
import com.paytech.commercial.api.agreement.request.ResumeAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.request.SuspendAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.request.TerminateAgreementRequestBody;
import com.paytech.commercial.api.agreement.request.UpdateAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.response.AgreementCapabilityDetail;
import com.paytech.commercial.api.agreement.response.AgreementCapabilityResponseBody;
import com.paytech.commercial.api.agreement.response.AgreementDetailResponseBody;
import com.paytech.commercial.api.agreement.response.AgreementDocumentDetail;
import com.paytech.commercial.api.agreement.response.AgreementPartyDetail;
import com.paytech.commercial.api.agreement.response.AgreementResponseBody;
import com.paytech.commercial.api.agreement.response.CapabilityConditionDetail;
import com.paytech.commercial.api.agreement.response.CapabilityPrerequisiteCheckResponseBody;
import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.api.enums.PricingAuthority;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.domain.AgreementDetailView;
import com.paytech.commercial.domain.CapabilityPrerequisiteDecision;
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
import com.paytech.commercial.service.processor.request.ResumeAgreementCapabilityRequest;
import com.paytech.commercial.service.processor.request.SuspendAgreementCapabilityRequest;
import com.paytech.commercial.service.processor.request.TerminateAgreementRequest;
import com.paytech.commercial.service.processor.request.UpdateAgreementCapabilityRequest;
import com.paytech.commercial.service.processor.response.AgreementCapabilityResponse;
import com.paytech.commercial.service.processor.response.AgreementResponse;
import com.paytech.commercial.service.processor.response.CapabilityPrerequisiteCheckResponse;
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
            body.getRollingReserveDays(), body.getConditions(), body.getRequire3ds());
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
            body.getRollingReserveDays(), body.getConditions(), body.getRequire3ds());
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
        body.setExecutionStatus(text(response.getExecutionStatus()));
        body.setChangeVersion(response.getChangeVersion());
        return body;
    }

    /** 暂停开通项请求。 */
    static SuspendAgreementCapabilityRequest suspendCapabilityRequest(String actorId, String agreementId,
        String agreementCapabilityId, SuspendAgreementCapabilityRequestBody body) {
        SuspendAgreementCapabilityRequest request = new SuspendAgreementCapabilityRequest();
        request.setActorId(actor(actorId));
        request.setAgreementId(id("agreementId", agreementId));
        request.setAgreementCapabilityId(id("agreementCapabilityId", agreementCapabilityId));
        request.setExpectedChangeVersion(body.getExpectedChangeVersion());
        request.setReason(body.getReason());
        return request;
    }

    /** 恢复开通项请求。 */
    static ResumeAgreementCapabilityRequest resumeCapabilityRequest(String actorId, String agreementId,
        String agreementCapabilityId, ResumeAgreementCapabilityRequestBody body) {
        ResumeAgreementCapabilityRequest request = new ResumeAgreementCapabilityRequest();
        request.setActorId(actor(actorId));
        request.setAgreementId(id("agreementId", agreementId));
        request.setAgreementCapabilityId(id("agreementCapabilityId", agreementCapabilityId));
        request.setExpectedChangeVersion(body.getExpectedChangeVersion());
        return request;
    }

    /** 前置核对结果：每个开通项的执行状态取核对后的目标值。 */
    static CapabilityPrerequisiteCheckResponseBody prerequisiteCheckResponse(CapabilityPrerequisiteCheckResponse response) {
        CapabilityPrerequisiteCheckResponseBody body = new CapabilityPrerequisiteCheckResponseBody();
        body.setAgreementId(Long.toString(response.getAgreementId()));
        body.setChangeVersion(response.getChangeVersion());
        body.setCapabilities(response.getDecisions().stream().map(AgreementConverter::prerequisiteCapability).toList());
        return body;
    }

    /** 一个开通项的核对结果。 */
    static CapabilityPrerequisiteCheckResponseBody.Capability prerequisiteCapability(
        CapabilityPrerequisiteDecision decision) {
        CapabilityPrerequisiteCheckResponseBody.Capability capability = new CapabilityPrerequisiteCheckResponseBody.Capability();
        capability.setAgreementCapabilityId(Long.toString(decision.getCapability().getAgreementCapabilityId()));
        capability.setEntityNo(decision.getCapability().getEntityNo());
        capability.setCapabilityCode(decision.getCapability().getCapabilityCode());
        capability.setCapabilityKind(text(decision.getCapabilityKind()));
        capability.setStatus(text(decision.getCapability().getStatus()));
        capability.setExecutionStatus(text(decision.getTargetExecutionStatus()));
        capability.setChecks(decision.getChecks().stream().map(check -> {
            CapabilityPrerequisiteCheckResponseBody.Check item = new CapabilityPrerequisiteCheckResponseBody.Check();
            item.setCode(text(check.getCode()));
            item.setResult(text(check.getResult()));
            item.setReasonCode(check.getReasonCode());
            item.setParams(check.getParams());
            return item;
        }).toList());
        return capability;
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

    /** 开通项条款、接受条件与 3DS 要求。 */
    static void terms(AgreementCapabilityTermsRequest request, String effectiveFrom, String effectiveTo, String rate,
        Integer days, List<CapabilityConditionEntry> conditions, Boolean require3ds) {
        request.setEffectiveFrom(time("effectiveFrom", effectiveFrom));
        request.setEffectiveTo(time("effectiveTo", effectiveTo));
        request.setRollingReserveRate(decimal("rollingReserveRate", rate));
        request.setRollingReserveDays(days);
        request.setRequire3ds(require3ds);
        request.setConditions(conditions.stream().map(entry -> {
            PricingDimension dimension = code(PricingDimension.class, "dimensionCode", entry.getDimensionCode());
            if (!dimension.isAcceptanceAllowed()) {
                throw new InvalidParameterException("维度不可用于开通接受条件: " + entry.getDimensionCode());
            }
            AgreementCapabilityTermsRequest.Condition condition = new AgreementCapabilityTermsRequest.Condition();
            condition.setDimensionCode(dimension);
            condition.setValues(List.copyOf(entry.getValues()));
            return condition;
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
        detail.setExecutionStatus(text(capability.getExecutionStatus()));
        detail.setEffectiveFrom(text(capability.getEffectiveFrom()));
        detail.setEffectiveTo(text(capability.getEffectiveTo()));
        detail.setRollingReserveRate(text(capability.getRollingReserveRate()));
        detail.setRollingReserveDays(capability.getRollingReserveDays());
        detail.setRequire3ds(capability.isRequire3ds());
        detail.setConditions(conditions(capability.getLimits()));
        return detail;
    }

    /** 条件行按维度归并为接受条件，维度与取值按代码排序。 */
    static List<CapabilityConditionDetail> conditions(List<AgreementDetailView.Limit> limits) {
        java.util.Map<String, java.util.TreeSet<String>> grouped = new java.util.TreeMap<>();
        limits.forEach(limit -> grouped.computeIfAbsent(limit.getLimitCode().getCode(), key -> new java.util.TreeSet<>())
            .add(limit.getLimitValue()));
        return grouped.entrySet().stream().map(entry -> {
            CapabilityConditionDetail item = new CapabilityConditionDetail();
            item.setDimensionCode(entry.getKey());
            item.setValues(List.copyOf(entry.getValue()));
            return item;
        }).toList();
    }
}
