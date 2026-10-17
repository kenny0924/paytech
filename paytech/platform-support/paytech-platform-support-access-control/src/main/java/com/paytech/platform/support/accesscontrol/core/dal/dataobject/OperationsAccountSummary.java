package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.api.enums.OperationsAccountStatus;

/** 人员分页的最小展示事实，不读取邮箱 Token 或密码校验材料。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class OperationsAccountSummary {
    /** Operations 人工账号的稳定主键。 */
    private long accountId;

    /** 已获批可展示的脱敏邮箱。 */
    private String emailMasked;

    /** 人员当前账号生命周期。 */
    private OperationsAccountStatus status;

    /** 临时锁定截止时刻，未锁定时可空。 */
    private Instant lockedUntil;

    /** 人员账号的当前并发版本。 */
    private int accountVersion;

    /** 固定 INTERNAL Tenant 的成员主键；缺失时保留 null 供调用方失败关闭。 */
    private Long memberId;

    /** 对应成员的当前并发版本；成员缺失时为 null。 */
    private Integer memberVersion;

    /** 是否必须改密；ARGON2ID 凭据缺失时为 null，不能默认为无需改密。 */
    private Boolean forceChange;

}
