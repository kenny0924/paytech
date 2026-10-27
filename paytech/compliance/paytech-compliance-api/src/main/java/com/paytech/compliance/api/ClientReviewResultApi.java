package com.paytech.compliance.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.compliance.api.response.ClientReviewResultResponseBody;

/** Client 申请的客户可见审核结果；只允许 Client BFF 在核对申请归属后私网调用，不注册到公共 Gateway。 */
@HttpExchange("/client-onboarding-review/v1")
public interface ClientReviewResultApi {

    /**
     * 读取申请最近一次主审核任务的客户可见结果
     *
     * @param caller
     *            私网调用方：client-console-bff（客户结果页）或 client-master-service（退回申请展示需求级错误）
     * @param applicationId
     *            CCT 申请 ID；调用方必须已经通过 CCT 核对该申请属于当前账号
     * @return 最近一次主任务的状态、结果、退回分区与客户可见说明
     *         <li>申请从未登记审核任务返回 404 CLIENT_REVIEW_NOT_FOUND。</li>
     *         <li>不返回内部理由、核验记录、决定人或子任务。</li>
     */
    @GetExchange("/applications/{applicationId}/result")
    ClientReviewResultResponseBody result(@RequestHeader("X-Caller-Service") String caller,
        @PathVariable("applicationId") String applicationId);
}
