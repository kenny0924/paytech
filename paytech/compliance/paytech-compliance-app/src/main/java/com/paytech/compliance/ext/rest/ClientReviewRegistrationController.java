package com.paytech.compliance.ext.rest;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.compliance.api.ClientReviewRegistrationApi;
import com.paytech.compliance.api.request.ClientReviewClassificationsRequestBody;
import com.paytech.compliance.api.request.ClientReviewDocumentBody;
import com.paytech.compliance.api.request.ClientReviewRegistrationRequestBody;
import com.paytech.compliance.api.request.ClientReviewRelationBody;
import com.paytech.compliance.api.request.ClientReviewSubjectBody;
import com.paytech.compliance.api.request.ClientReviewWithdrawalRequestBody;
import com.paytech.compliance.api.response.ClientReviewCommandResponseBody;
import com.paytech.compliance.api.response.ClientReviewRegistrationResponseBody;
import com.paytech.compliance.service.processor.ReceiveClientReviewClassificationsProcessor;
import com.paytech.compliance.service.processor.RegisterClientReviewProcessor;
import com.paytech.compliance.service.processor.WithdrawClientReviewProcessor;
import com.paytech.compliance.service.processor.request.WithdrawClientReviewRequest;
import com.paytech.compliance.service.processor.request.ReceiveClientReviewClassificationsRequest;
import com.paytech.compliance.service.processor.request.RegisterClientReviewRequest;
import com.paytech.compliance.service.processor.response.RegisterClientReviewResponse;

import lombok.AllArgsConstructor;

/**
 * I01/I02/I03：client-master 正式提交后登记 Client 入驻审核案件、交付已确认分类、撤回案件的私网入口
 * <li>仅在 onboarding.review.enabled=true 时注册；请求体含姓名、生日等个人资料，路径为 metadata-only 日志。</li>
 * <li>X-Caller-Service 暂不校验，调用方鉴权待后续统一安全方案处理。</li>
 * <li>资料快照本期不做：姓名、证件 Token、属性与声明只做合同校验，不进入 Owner 输入。</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "onboarding.review.enabled", havingValue = "true")
public class ClientReviewRegistrationController implements ClientReviewRegistrationApi {
    private final RegisterClientReviewProcessor processor;
    private final WithdrawClientReviewProcessor withdrawProcessor;
    private final ReceiveClientReviewClassificationsProcessor classificationProcessor;

    /** 转换为 Owner 输入后委托 risk-compliance。 */
    @Override
    public ClientReviewRegistrationResponseBody register(String caller, ClientReviewRegistrationRequestBody request) {
        RegisterClientReviewResponse response = processor.process(toRequest(request));
        var body = new ClientReviewRegistrationResponseBody();
        body.setSourceSubmissionId(response.getSourceSubmissionId());
        body.setCaseId(Long.toString(response.getCaseId()));
        return body;
    }

    /** I02：转换为 Owner 输入后委托 risk-compliance。 */
    @Override
    public ClientReviewCommandResponseBody receiveClassifications(String caller, String caseId,
        ClientReviewClassificationsRequestBody request) {
        var input = new ReceiveClientReviewClassificationsRequest(positive(caseId, "审核任务 ID 无效"),
            id(request.getApplicationId(), "applicationId"), id(request.getConfirmedBy(), "confirmedBy"), request.getSubmissionId(),
            instant(request.getConfirmedAt()), request.getClassifications().stream()
                .map(item -> new ReceiveClientReviewClassificationsRequest.Item(item.getPrimary(), item.getMccCode(), item.getIndustryCode()))
                .toList());
        return ClientReviewWireConverter.command(classificationProcessor.process(input));
    }

    /** I03：转换为 Owner 输入后委托撤回。 */
    @Override
    public ClientReviewCommandResponseBody withdraw(String caller, String caseId, ClientReviewWithdrawalRequestBody request) {
        return ClientReviewWireConverter.command(withdrawProcessor.process(new WithdrawClientReviewRequest(
            positive(caseId, "审核案件 ID 无效"), id(request.getApplicationId(), "applicationId"),
            request.getRequestedByAccountId() == null ? null : id(request.getRequestedByAccountId(), "requestedByAccountId"))));
    }

    /** 只保留建案需要的稳定标识与冻结属性。 */
    RegisterClientReviewRequest toRequest(ClientReviewRegistrationRequestBody body) {
        return new RegisterClientReviewRequest(body.getSourceSubmissionId(), id(body.getApplicationId(), "applicationId"),
            id(body.getClientId(), "clientId"), body.getClientType(), body.getJurisdictionCode(), body.getOrganizationTypeCode(),
            body.getResponsiblePartnerId() == null ? null : id(body.getResponsiblePartnerId(), "responsiblePartnerId"),
            body.getPreviousCaseId() == null ? null : id(body.getPreviousCaseId(), "previousCaseId"), instant(body.getSubmittedAt()),
            body.getSubjects().stream().map(this::subject).toList(), body.getRelations().stream().map(this::relation).toList(),
            body.getDocuments().stream().map(this::document).toList());
    }

    /** 主体。 */
    RegisterClientReviewRequest.Subject subject(ClientReviewSubjectBody body) {
        return new RegisterClientReviewRequest.Subject(id(body.getPartyId(), "partyId"), id(body.getProfileId(), "profileId"),
            body.getSubjectRole(), body.getQualificationType());
    }

    /** 关系。 */
    RegisterClientReviewRequest.Relation relation(ClientReviewRelationBody body) {
        return new RegisterClientReviewRequest.Relation(id(body.getRelationId(), "relationId"), id(body.getRelatedPartyId(), "relatedPartyId"),
            body.getRelationType(), body.getOwnershipPercentage() == null ? null : decimal(body.getOwnershipPercentage()),
            body.getUboBasis(), body.getJobTitle());
    }

    /** 材料。 */
    RegisterClientReviewRequest.Document document(ClientReviewDocumentBody body) {
        return new RegisterClientReviewRequest.Document(id(body.getDocumentId(), "documentId"), id(body.getSubjectPartyId(), "subjectPartyId"),
            body.getRequirementCode(), body.getDocumentType(), body.getFileAddress(), body.getContentHash(), date(body.getIssueDate()),
            date(body.getExpiryDate()));
    }

    /** UTC ISO-8601 时间。 */
    static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new InvalidParameterException("时间格式无效");
        }
    }

    /** 可空 YYYY-MM-DD 日期。 */
    static LocalDate date(String value) {
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new InvalidParameterException("日期格式无效");
        }
    }

    /** 持股比例。 */
    static BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException invalid) {
            throw new InvalidParameterException("持股比例格式无效");
        }
    }

    /** 请求体中的 BIGINT 标识：格式由 Bean Validation 校验，超出 long 范围按参数错误处理。 */
    static long id(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw new InvalidParameterException(field + " 超出范围");
        }
    }

    /** 路径中的正 BIGINT。 */
    static long positive(String value, String message) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new InvalidParameterException(message);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw new InvalidParameterException(message);
        }
    }
}
