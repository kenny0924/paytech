package com.paytech.platform.support.accesscontrol.domain.clientteam;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 团队成员查询结果：成员、账号脱敏邮箱与当前有效角色（多表组合）。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberView {
    /** 成员。 */
    private long memberId;

    /** 成员账号。 */
    private long accountId;

    /** 账号邮箱脱敏值。 */
    private String emailMasked;

    /** 成员类型代码。 */
    private String memberType;

    /** 当前有效角色代码；无有效授权为空。 */
    private String roleCode;

    /** 成员状态代码。 */
    private String status;

    /** 加入时间。 */
    private Instant joinedAt;

    /** 不输出成员坐标。 */
    @Override
    public String toString() {
        return "TeamMemberView[REDACTED]";
    }
}
