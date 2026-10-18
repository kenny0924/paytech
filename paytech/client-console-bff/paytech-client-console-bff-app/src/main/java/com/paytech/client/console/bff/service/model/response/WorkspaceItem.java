package com.paytech.client.console.bff.service.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 单个工作区。 */
@Getter
@AllArgsConstructor
public class WorkspaceItem {

    /** 当前账号在该工作区的成员 ID */
    private final String memberId;

    /** 工作区所属 Client ID */
    private final String clientId;

    /**
     * 客户编号
     * <li>CCT 未返回已批准资料时为空。</li>
     */
    private final String clientNo;

    /**
     * 显示名称
     * <li>CCT 未返回已批准资料时为空，页面改显客户编号。</li>
     */
    private final String displayName;

    /** 客户类型；为空条件同 clientNo */
    private final String clientType;

    /** 访问状态：ACCESSIBLE 或 RESTRICTED */
    private final String state;

    /**
     * 受限时的公开说明
     * <li>可访问时为空。</li>
     */
    private final String reasonText;

    /** 租户上下文下是否为当前工作区 */
    private final boolean current;
}
