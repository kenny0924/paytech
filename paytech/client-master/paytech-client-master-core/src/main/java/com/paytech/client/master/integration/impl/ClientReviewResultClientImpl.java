package com.paytech.client.master.integration.impl;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.ONBOARDING_DEPENDENCY_UNAVAILABLE;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.client.master.integration.ClientReviewResultClient;
import com.paytech.compliance.api.ClientReviewResultApi;
import com.paytech.compliance.api.enums.ClientReviewErrorCode;
import com.paytech.compliance.api.response.ClientReviewRequirementErrorBody;
import com.paytech.compliance.api.response.ClientReviewResultResponseBody;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/** 调用 compliance applications/{id}/result，只取当前退回任务的需求级错误；不缓存。 */
@AllArgsConstructor
public class ClientReviewResultClientImpl implements ClientReviewResultClient {
    private static final String CALLER = "client-master-service";
    private static final String RETURNED = "RETURNED";

    private final ClientReviewResultApi results;

    /** 读取并校验退回错误；格式不对的条目跳过，不回显内容。 */
    @Override
    public List<ReturnedError> returnedErrors(long applicationId, long caseId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("compliance 调用不能位于数据库事务内");
        }
        ClientReviewResultResponseBody result;
        try {
            result = results.result(CALLER, Long.toString(applicationId));
        } catch (FailException rejected) {
            if (ClientReviewErrorCode.CLIENT_REVIEW_NOT_FOUND.getCode().equals(rejected.getCode())) {
                return List.of();
            }
            throw new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE);
        } catch (RuntimeException unavailable) {
            throw new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE);
        }
        if (result == null || !Long.toString(caseId).equals(result.getCaseId()) || !RETURNED.equals(result.getOutcome())
            || result.getErrors() == null) {
            return List.of();
        }
        var errors = new ArrayList<ReturnedError>();
        for (ClientReviewRequirementErrorBody error : result.getErrors()) {
            ReturnedError converted = convert(error, result.getDueTime());
            if (converted != null) {
                errors.add(converted);
            }
        }
        return List.copyOf(errors);
    }

    /** 单项转换；缺键、缺错误码、主体格式或时间不合法返回 null；主体为空表示申请级错误。 */
    static ReturnedError convert(ClientReviewRequirementErrorBody error, String caseDueTime) {
        if (error == null || error.getRequirementKey() == null || error.getCode() == null
            || (error.getSubjectPartyId() != null && !error.getSubjectPartyId().matches("[1-9][0-9]{0,18}"))) {
            return null;
        }
        String due = error.getDueTime() != null ? error.getDueTime() : caseDueTime;
        try {
            return new ReturnedError(error.getRequirementKey(),
                error.getSubjectPartyId() == null ? null : Long.valueOf(error.getSubjectPartyId()), error.getCode(),
                error.getRemediation(), due == null ? null : Instant.parse(due));
        } catch (DateTimeParseException malformed) {
            return null;
        }
    }
}
