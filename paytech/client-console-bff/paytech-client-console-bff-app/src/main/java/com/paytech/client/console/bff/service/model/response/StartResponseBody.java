package com.paytech.client.console.bff.service.model.response;

import java.util.List;

import com.paytech.client.contract.api.onboarding.ClientDraftModels.ApplicationSummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 入驻开始页：当前账号的申请导航与“我的工作区”；工作区切换另由 Platform 授权。 */
@Getter
@AllArgsConstructor
public class StartResponseBody {

    /** 最近申请，最多 20 条 */
    private final List<ApplicationSummary> applications;

    /** 是否还有更早的申请 */
    private final boolean hasMore;

    /**
     * 唯一未完成申请的 ID
     * <li>没有未完成申请时为空。</li>
     */
    private final String activeApplicationId;

    /** 我的工作区 */
    private final WorkspaceList workspaces;
}
