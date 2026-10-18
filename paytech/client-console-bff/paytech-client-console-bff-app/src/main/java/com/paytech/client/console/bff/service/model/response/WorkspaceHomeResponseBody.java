package com.paytech.client.console.bff.service.model.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** W02 工作区首页。 */
@Getter
@AllArgsConstructor
public class WorkspaceHomeResponseBody {

    /** 当前工作区身份 */
    private final HomeWorkspace workspace;

    /**
     * 已批准资料摘要
     * <li>CCT 未返回已批准资料时为空。</li>
     */
    private final HomeProfile profile;

    /** 固定提示 PAYMENTS_NOT_YET_ENABLED：进入工作区不代表已开通收款 */
    private final String notice;

    /**
     * 当前成员被授予的页面能力
     * <li>API_CREDENTIAL_READ/MANAGE、WEBHOOK_READ/MANAGE、TEAM_READ/MANAGE，只用于显示菜单与按钮。</li>
     */
    private final List<String> capabilities;
}
