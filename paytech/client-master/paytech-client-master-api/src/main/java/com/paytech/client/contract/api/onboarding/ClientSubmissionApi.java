package com.paytech.client.contract.api.onboarding;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.client.contract.api.onboarding.response.SubmissionCheckResponseBody;
import com.paytech.client.contract.api.onboarding.response.SubmissionResponseBody;

/** C14：Client BFF 使用的 CCT 私网正式提交命令。 */
@HttpExchange("/client-onboarding/v1/applications")
public interface ClientSubmissionApi {

    /**
     * 正式提交申请
     *
     * @param caller 私网调用方，固定为 client-console-bff
     * @param accountId 当前 CLIENT 账号
     * @param sessionId 当前 CLIENT 会话
     * @param applicationId 申请 ID
     * @return 当前提交
     *         <li>DRAFT/RETURNED 检查通过后返回 202 并生成新 submissionId。</li>
     *         <li>已是 SUBMITTED/UNDER_REVIEW 返回 200 和当前 submissionId，不重新检查。</li>
     *         <li>资料或材料不完整返回 422 SUBMISSION_INCOMPLETE，逐项问题由 check 查询；证件号属于其他主体返回 409 IDENTITY_CONFLICT。</li>
     *         <li>国家、币种、行政区不在 Platform 参考数据或不是 ACTIVE、行政区与地址国家不一致：对应字段 FIELD_VALUE_NOT_IN_CATALOG，
     *         按资料不完整拒绝；Platform 不可用返回 503 ONBOARDING_DEPENDENCY_UNAVAILABLE，不写入。</li>
     *         <li>Body 为空；不读取 Idempotency-Key，重复提交按申请当前状态处理。</li>
     */
    @PostExchange("/{applicationId}/submissions")
    ResponseEntity<SubmissionResponseBody> submit(@RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("applicationId") String applicationId);

    /**
     * 提交前核对：执行与正式提交相同的检查，不改变任何状态
     *
     * @param caller 私网调用方，固定为 client-console-bff
     * @param accountId 当前 CLIENT 账号
     * @param sessionId 当前 CLIENT 会话
     * @param applicationId 申请 ID
     * @return 是否可以提交及逐项问题
     *         <li>只适用于 DRAFT/RETURNED；其他状态返回 409 APPLICATION_NOT_EDITABLE。</li>
     *         <li>Risk 或 Platform 暂不可用返回 503，不视为没有问题。</li>
     */
    @GetExchange("/{applicationId}/submission-check")
    SubmissionCheckResponseBody check(@RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("applicationId") String applicationId);
}
