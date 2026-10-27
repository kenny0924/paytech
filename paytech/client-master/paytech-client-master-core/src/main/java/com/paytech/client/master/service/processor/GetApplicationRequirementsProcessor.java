package com.paytech.client.master.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatterBuilder;

import com.paytech.client.master.api.enums.OnboardingAwaitingParty;
import com.paytech.client.master.api.enums.OnboardingRequirementKind;
import com.paytech.client.master.api.enums.OnboardingRequirementStatus;
import com.paytech.client.master.api.onboarding.response.RequirementItemResponseBody;
import com.paytech.client.master.domain.enums.ClientApplicationStatus;
import com.paytech.client.master.integration.ClientReviewResultClient;
import com.paytech.client.master.api.onboarding.response.RequirementsResponseBody;
import com.paytech.client.master.core.dal.dataobject.ClientDraftIdentity;
import com.paytech.client.master.domain.onboarding.ClientDraftPolicy;
import com.paytech.client.master.domain.onboarding.requirement.OnboardingRequirement;
import com.paytech.client.master.domainservice.repository.ClientDraftRepository;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.service.processor.request.ApplicationQueryRequest;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * CM-7 需求清单：与正式提交校验使用同一准备步骤与计算，实时计算、不落表、不加锁
 * <li>会话在事务外核验；compliance 程序与 Platform 文件核验在事务外完成，任一不可用整体 503。</li>
 * <li>退回中的申请再读取 compliance 审核结果，审核员的需求级错误覆盖同键同主体的本地项（awaitingActionFrom=CLIENT，带补件期限，
 * 超期为 PAST_DUE），直到重新提交；它们不改变 ready，客户处理后仍按本地计算决定能否重提。</li>
 */
@AllArgsConstructor
public class GetApplicationRequirementsProcessor extends AbstractProcessor<ApplicationQueryRequest, RequirementsResponseBody> {
    private final ClientDraftRepository drafts;
    private final ClientDraftAccountClient accounts;
    private final ClientSubmissionPreparer preparer;
    private final ClientReviewResultClient reviewResults;
    private final Clock clock;

    /** 归属 → 准备与计算 → 响应。 */
    @Override
    protected RequirementsResponseBody processRequest(ApplicationQueryRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        ClientDraftIdentity identity = ClientDraftPolicy.requireValid(
            drafts.identity(request.getApplicationId(), request.getAccountId(), false));
        var formatter = new DateTimeFormatterBuilder().appendInstant(6).toFormatter();
        var body = new RequirementsResponseBody();
        body.setApplicationId(Long.toString(identity.applicationId()));
        body.setStatus(identity.status().getCode());
        body.setDueTime(identity.reviewDueTime() == null ? null : formatter.format(identity.reviewDueTime()));
        if (identity.schemaId() == null) {
            // 2026-10-27 之前的遗留申请没有采集 Schema：没有需求可算，也不调用 compliance 与 Platform。
            body.setReady(false);
            body.setItems(List.of());
            return body;
        }
        var preparation = preparer.prepare(request.getAccountId(), identity);
        body.setReady(preparation.ready());
        var items = new ArrayList<>(preparation.getRequirements().stream().map(item -> item(item, formatter)).toList());
        if (identity.status() == ClientApplicationStatus.RETURNED && identity.dueDiligenceCaseId() != null) {
            mergeReturned(items, reviewResults.returnedErrors(identity.applicationId(), identity.dueDiligenceCaseId()),
                identity.partyId(), formatter);
        }
        body.setItems(List.copyOf(items));
        return body;
    }

    /** 审核员退回的错误覆盖同键同主体的本地项，其余追加在后；申请级错误（无主体）归主主体。 */
    void mergeReturned(List<RequirementItemResponseBody> items, List<ClientReviewResultClient.ReturnedError> errors,
        long primaryPartyId, java.time.format.DateTimeFormatter formatter) {
        Instant now = clock.instant();
        for (ClientReviewResultClient.ReturnedError error : errors) {
            String party = Long.toString(error.subjectPartyId() == null ? primaryPartyId : error.subjectPartyId());
            items.removeIf(item -> error.requirementKey().equals(item.getRequirementKey()) && party.equals(item.getSubjectPartyId()));
            var body = new RequirementItemResponseBody();
            body.setRequirementKind(kind(error.requirementKey()).getCode());
            body.setRequirementKey(error.requirementKey());
            body.setSubjectPartyId(party);
            body.setStatus((error.dueTime() != null && now.isAfter(error.dueTime()) ? OnboardingRequirementStatus.PAST_DUE
                : OnboardingRequirementStatus.CURRENTLY_DUE).getCode());
            body.setAwaitingActionFrom(OnboardingAwaitingParty.CLIENT.getCode());
            body.setErrorCode(error.code());
            body.setRemediationCode(error.remediation());
            body.setDueTime(error.dueTime() == null ? null : formatter.format(error.dueTime()));
            items.add(body);
        }
    }

    /** 按需求键前缀判断类别。 */
    static OnboardingRequirementKind kind(String key) {
        if (key.startsWith("document")) {
            return OnboardingRequirementKind.DOCUMENT;
        }
        if (key.startsWith("declaration.")) {
            return OnboardingRequirementKind.DECLARATION;
        }
        if (key.startsWith("relation.")) {
            return OnboardingRequirementKind.RELATION;
        }
        if (key.equals("people")) {
            return OnboardingRequirementKind.PERSON;
        }
        return key.startsWith("identifier.") || key.startsWith("group.") ? OnboardingRequirementKind.IDENTIFIER
            : OnboardingRequirementKind.FIELD;
    }

    /** 单项转换。 */
    static RequirementItemResponseBody item(OnboardingRequirement item, java.time.format.DateTimeFormatter formatter) {
        var body = new RequirementItemResponseBody();
        body.setRequirementKind(item.getKind().getCode());
        body.setRequirementKey(item.getRequirementKey());
        body.setSubjectPartyId(Long.toString(item.getSubjectPartyId()));
        body.setStatus(item.getStatus().getCode());
        body.setAwaitingActionFrom(item.getAwaitingActionFrom() == null ? null : item.getAwaitingActionFrom().getCode());
        body.setErrorCode(item.getErrorCode() == null ? null : item.getErrorCode().getCode());
        body.setRemediationCode(item.getRemediationCode() == null ? null : item.getRemediationCode().getCode());
        body.setDueTime(item.getDueTime() == null ? null : formatter.format(item.getDueTime()));
        return body;
    }
}
