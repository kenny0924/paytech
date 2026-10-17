package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientRefresh;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientSession;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerAccount;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.SwitchPlan;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 本次上下文切换的完整本库事实；事务失败后整体丢弃。 */
@Getter
@AllArgsConstructor
public class SwitchClientContext {
    /** 事务外准备结果。 */
    private final SwitchPlan plan;
    /** 锁定后的账号。 */
    private final CustomerAccount account;
    /** 锁定后的当前会话。 */
    private final ClientSession session;
    /** 锁定后的当前刷新代。 */
    private final ClientRefresh refresh;
    /** 共享锁读取的目标成员；返回账号上下文时为空。 */
    private final ClientMember member;
    /** 本次数据库操作时刻。 */
    private final Instant now;

    /** 是否为返回账号上下文。 */
    public boolean toAccount() {
        return plan.memberId() == null;
    }
}
