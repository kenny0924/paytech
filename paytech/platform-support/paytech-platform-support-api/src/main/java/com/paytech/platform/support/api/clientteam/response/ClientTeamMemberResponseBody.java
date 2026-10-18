package com.paytech.platform.support.api.clientteam.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 工作区成员摘要；只含脱敏邮箱。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientTeamMemberResponseBody {

    /** 成员 ID，十进制字符串。 */
    private String memberId;

    /** 账号邮箱脱敏值。 */
    private String emailMasked;

    /** OWNER、ADMIN 或 MEMBER。 */
    private String memberType;

    /** 当前角色代码，见 ClientTeamRole；没有有效授权时为空。 */
    private String roleCode;

    /** ACTIVE 或 REVOKED（列表只返回 ACTIVE；移除操作返回 REVOKED）。 */
    private String status;

    /** 加入时间，UTC ISO-8601。 */
    private String joinedAt;

    /** 是否为当前操作人自己。 */
    private boolean self;
}
