package com.paytech.client.console.bff.service.model;

import java.util.List;

/** 工作区页面投影；成员与租户事实来自 Platform，名称与已批准资料来自 CCT。 */
public final class ClientWorkspaceModels {

    /** 仅作为响应容器。 */
    private ClientWorkspaceModels() {}

    /**
     * 开始页“我的工作区”
     *
     * @param status AVAILABLE；Platform 或 CCT 不可用时为 UNAVAILABLE，且 items 为空
     * @param items 不含 PENDING 工作区
     */
    public record WorkspaceList(String status, List<WorkspaceItem> items) {}

    /**
     * 单个工作区
     *
     * @param state ACCESSIBLE 或 RESTRICTED
     * @param reasonText 受限时的公开说明，可访问时为空
     * @param current 租户上下文下是否为当前工作区
     */
    public record WorkspaceItem(
        String memberId,
        String clientId,
        String clientNo,
        String displayName,
        String clientType,
        String state,
        String reasonText,
        boolean current
    ) {}

    /**
     * 工作区首页
     *
     * @param notice 固定提示进入工作区不代表已开通收款
     * @param capabilities 当前成员被授予的页面能力（API_CREDENTIAL_READ/MANAGE、WEBHOOK_READ/MANAGE、TEAM_READ/MANAGE），用于显示菜单与按钮
     */
    public record WorkspaceHomeResponseBody(HomeWorkspace workspace, HomeProfile profile, String notice,
        List<String> capabilities) {}

    /** 当前工作区身份；CCT 未返回已批准资料时只有 clientId。 */
    public record HomeWorkspace(String clientId, String clientNo, String displayName, String clientType, String clientStatus) {}

    /** 已批准资料摘要。 */
    public record HomeProfile(String country, String businessModelCode, String approvedAt) {}
}
