package com.paytech.compliance.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.compliance.api.request.ClientReviewClassificationsRequestBody;
import com.paytech.compliance.api.request.ClientReviewRegistrationRequestBody;
import com.paytech.compliance.api.response.ClientReviewCommandResponseBody;
import com.paytech.compliance.api.response.ClientReviewRegistrationResponseBody;

import jakarta.validation.Valid;

/** I01/I02：CCT 登记 Client 入驻审核任务并交付已确认分类的私网合同；只允许 CCT 调用，不注册到公共 Gateway。 */
@HttpExchange("/client-onboarding-review/v1")
public interface ClientReviewRegistrationApi {

    /**
     * 按来源提交编号登记主审核任务及关联人员子任务
     *
     * @param caller
     *            私网调用方，固定为 client-master-service
     * @param request
     *            提交时的完整受审事实
     * @return 主审核任务
     *         <li>同一 sourceSubmissionId 同内容重放返回原任务；同编号改内容返回 409。</li>
     *         <li>请求不合法返回 400/422；Risk 暂不可用返回 503，调用方用同一编号重试，不生成新提交。</li>
     */
    @PostExchange("/submissions")
    ClientReviewRegistrationResponseBody register(@RequestHeader("X-Caller-Service") String caller,
        @Valid @RequestBody ClientReviewRegistrationRequestBody request);

    /**
     * I02 接收 CCT 已确认的同一任务分类
     *
     * @param caller
     *            私网调用方，固定为 client-master-service
     * @param caseId
     *            主审核任务 ID
     * @param request
     *            申请、提交编号与固定分类集合
     * @return 命令结果
     *         <li>同集合重放返回原结果；集合不同 409 CLIENT_REVIEW_CLASSIFICATION_CONFLICT；任务已结束 409 CLIENT_REVIEW_CLOSED。</li>
     *         <li>申请或提交编号与任务不符返回 404，不暴露其他任务。</li>
     */
    @PostExchange("/cases/{caseId}/classifications")
    ClientReviewCommandResponseBody receiveClassifications(@RequestHeader("X-Caller-Service") String caller,
        @PathVariable("caseId") String caseId, @Valid @RequestBody ClientReviewClassificationsRequestBody request);
}
