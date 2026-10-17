package com.paytech.risk.ext.rest.compliance;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.api.compliance.ClientReviewRegistrationApi;
import com.paytech.risk.api.compliance.request.ClientReviewClassificationsRequestBody;
import com.paytech.risk.api.compliance.request.ClientReviewDocumentBody;
import com.paytech.risk.api.compliance.request.ClientReviewRegistrationRequestBody;
import com.paytech.risk.api.compliance.request.ClientReviewRelationBody;
import com.paytech.risk.api.compliance.request.ClientReviewSubjectBody;
import com.paytech.risk.api.compliance.response.ClientReviewCommandResponseBody;
import com.paytech.risk.api.compliance.response.ClientReviewRegistrationResponseBody;
import com.paytech.risk.compliance.service.processor.ReceiveClientReviewClassificationsProcessor;
import com.paytech.risk.compliance.service.processor.RegisterClientReviewProcessor;
import com.paytech.risk.compliance.service.processor.request.ReceiveClientReviewClassificationsRequest;
import com.paytech.risk.compliance.service.processor.request.RegisterClientReviewRequest;
import com.paytech.risk.compliance.service.processor.response.RegisterClientReviewResponse;

import lombok.AllArgsConstructor;

/**
 * I01/I02：CCT 正式提交后登记 Client 入驻审核任务、交付已确认分类的私网入口
 * <li>仅在 onboarding.review.enabled=true 时注册；请求体含个人资料原值，路径为 metadata-only 日志。</li>
 * <li>X-Caller-Service 暂不校验，调用方鉴权待后续统一安全方案处理。</li>
 * <li>资料快照本期不做，主体个人资料字段不进入 risk-compliance。</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "onboarding.review.enabled", havingValue = "true")
public class ClientReviewRegistrationController implements ClientReviewRegistrationApi {
    private final RegisterClientReviewProcessor processor;
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

    /** 只保留建任务需要的稳定标识。 */
    RegisterClientReviewRequest toRequest(ClientReviewRegistrationRequestBody body) {
        return new RegisterClientReviewRequest(body.getSourceSubmissionId(), id(body.getApplicationId(), "applicationId"),
            id(body.getClientId(), "clientId"), body.getClientType(), instant(body.getSubmittedAt()),
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
            body.getRelationType(), body.getOwnershipPercentage() == null ? null : decimal(body.getOwnershipPercentage()));
    }

    /** 材料。 */
    RegisterClientReviewRequest.Document document(ClientReviewDocumentBody body) {
        return new RegisterClientReviewRequest.Document(id(body.getDocumentId(), "documentId"), id(body.getSubjectPartyId(), "subjectPartyId"),
            body.getRequirementCode(), body.getDocumentType(), id(body.getFileId(), "fileId"), body.getContentHash());
    }

    /** UTC ISO-8601 时间。 */
    static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new InvalidParameterException("时间格式无效");
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
