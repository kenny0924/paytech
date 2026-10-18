package com.paytech.client.console.bff.service.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 接受邀请页详情：Platform 邀请事实加 CCT 工作区名称。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientInvitationDetailsResponseBody {

    /** 受邀邮箱（脱敏）。 */
    private String emailMasked;

    /** 邀请的初始角色代码，如 TENANT_DEVELOPER。 */
    private String roleCode;

    /** 邀请状态：PENDING、EXPIRED、ACCEPTED 或 REVOKED。 */
    private String status;

    /** 到期时间，UTC 六位小数。 */
    private String expiresAt;

    /** 当前登录账号邮箱是否与受邀邮箱一致。 */
    private boolean emailMatches;

    /**
     * 工作区所属 Client ID
     * <li>邮箱不一致时为空。</li>
     */
    private String clientId;

    /**
     * 工作区名称（CCT 已批准资料的显示名）
     * <li>邮箱不一致、CCT 未返回或不可用时为空，页面改显通用文案。</li>
     */
    private String workspaceName;

    /** 客户编号；为空条件同 workspaceName。 */
    private String clientNo;

    /** 客户类型 ORGANIZATION 或 INDIVIDUAL；为空条件同 workspaceName。 */
    private String clientType;
}
