package com.paytech.client.master.ext.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.contract.api.onboarding.ClientSubmissionApi;
import com.paytech.client.contract.api.onboarding.response.SubmissionCheckResponseBody;
import com.paytech.client.contract.api.onboarding.response.SubmissionIssueResponseBody;
import com.paytech.client.contract.api.onboarding.response.SubmissionResponseBody;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.service.processor.CheckClientSubmissionProcessor;
import com.paytech.client.master.service.processor.SubmitClientApplicationProcessor;
import com.paytech.client.master.service.processor.request.CheckClientSubmissionRequest;
import com.paytech.client.master.service.processor.request.SubmitClientApplicationRequest;
import com.paytech.foundation.common.error.FailException;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;

/** C14 私网协议适配；新建提交 202，已提交 200。 */
@RestController
@ConditionalOnProperty(prefix = "onboarding", name = "draft-enabled", havingValue = "true")
public class ClientSubmissionController implements ClientSubmissionApi {
    private final CheckClientSubmissionProcessor checks;
    private final SubmitClientApplicationProcessor processor;

    /** 注入提交与核对用例。 */
    public ClientSubmissionController(CheckClientSubmissionProcessor checks, SubmitClientApplicationProcessor processor) {
        this.checks = checks;
        this.processor = processor;
    }

    /** Caller 仅防误调用；会话由 Processor 向 Platform 核对。 */
    @Override
    public ResponseEntity<SubmissionResponseBody> submit(String caller, String accountId, String sessionId, String applicationId) {
        if (!"client-console-bff".equals(caller)) {
            throw new FailException(APPLICATION_NOT_FOUND);
        }
        var response = processor.process(new SubmitClientApplicationRequest(session(accountId), session(sessionId),
            ClientDraftCommand.id(applicationId)));
        var body = new SubmissionResponseBody(Long.toString(response.getApplicationId()), response.getSubmissionId(),
            response.getStatus());
        return ResponseEntity.status(response.isCreated() ? 202 : 200).cacheControl(CacheControl.noStore()).body(body);
    }

    /** 提交前核对，不改变状态。 */
    @Override
    public SubmissionCheckResponseBody check(String caller, String accountId, String sessionId, String applicationId) {
        if (!"client-console-bff".equals(caller)) {
            throw new FailException(APPLICATION_NOT_FOUND);
        }
        var preparation = checks.process(new CheckClientSubmissionRequest(session(accountId), session(sessionId),
            ClientDraftCommand.id(applicationId)));
        return new SubmissionCheckResponseBody(preparation.ready(), preparation.getIssues().stream()
            .map(issue -> new SubmissionIssueResponseBody(issue.field(), issue.message())).toList());
    }

    /** 非法身份坐标与无效会话统一响应。 */
    long session(String value) {
        try {
            return ClientDraftCommand.id(value);
        } catch (FailException invalid) {
            throw new FailException(CLIENT_SESSION_INVALID);
        }
    }
}
