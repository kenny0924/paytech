package com.paytech.client.contract.api.onboarding;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;

/**
 * O02 申请资料部分：Operations 审核时读取审核任务对应的申请资料；由 Operations BFF 调用
 * <li>操作者来自 Gateway 可信头；路由权限由 Gateway/BFF 绑定，CCT 不做菜单权限判断。</li>
 */
@HttpExchange("/client-onboarding-admin/v1")
public interface ClientApplicationAdminApi {

    /**
     * 读取审核任务对应的申请资料
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param caseId Risk 主审核任务 ID
     * @return 与 C05 相同结构的申请资料、人员、关系与材料引用；联系方式不 Reveal，contact 分区为空
     *         <li>任务不是任何申请的当前任务返回 404 REVIEW_CASE_NOT_FOUND。</li>
     */
    @GetExchange("/cases/{caseId}/application")
    ClientDraftResponseBody application(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId);
}
