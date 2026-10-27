package com.paytech.client.console.bff.integration;

import java.util.List;

import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody;
import com.paytech.client.master.api.onboarding.response.RequirementItemResponseBody;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.StartResponseBody;

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
     * 读取实时需求清单（CM-7）。
     *
     * @param actor 当前可信身份
     * @param applicationId 申请 ID
     * @return 需求项（有界）；形状不符按依赖不可用抛出
     */
    List<RequirementItemResponseBody> requirements(ClientActor actor, String applicationId);

    /**
     * 读取采集 Schema（CM-2）。
     *
     * @param actor 当前可信身份
     * @param applicationId 可选申请 ID；有值时 Owner 返回该申请冻结的版本
     * @param jurisdiction 可选辖区代码
     * @param partyType 可选主体类型
     * @param organizationType 可选组织类型
     * @return 带 Schema 版本与分区的 Schema；形状不符按依赖不可用抛出
     */
    FormSchemaResponseBody formSchema(ClientActor actor, String applicationId, String jurisdiction, String partyType,
        String organizationType);
}
