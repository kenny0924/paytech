package com.paytech.client.console.bff.integration;

import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.StartResponseBody;
import com.paytech.client.contract.api.onboarding.response.ClientFormOptionsResponseBody;

/** CCT 入驻申请只读查询的出站端口；写命令由 Controller 直接转发 CCT。 */
public interface CctOnboardingClient {

    /**
     * 当前账号的申请导航（只导航既有申请，不触发创建）。
     *
     * @param actor 当前可信身份
     * @return 最多 20 条且至多一条未完成申请的导航；否则按依赖不可用抛出
     */
    StartResponseBody start(ClientActor actor);

    /**
     * 读取申请草稿。
     *
     * @param actor 当前可信身份
     * @param applicationId 申请 ID
     * @return 申请 ID 一致、主主体与客户类型有效且规模有界的草稿；否则按依赖不可用抛出
     */
    ClientDraftResponseBody draft(ClientActor actor, String applicationId);

    /**
     * 读取受控表单目录（C03）。
     *
     * @param actor 当前可信身份
     * @param clientType INDIVIDUAL 或 ORGANIZATION
     * @return 属于该客户类型、状态已识别且列表有界的目录；否则按依赖不可用抛出
     */
    ClientFormOptionsResponseBody formOptions(ClientActor actor, String clientType);
}
