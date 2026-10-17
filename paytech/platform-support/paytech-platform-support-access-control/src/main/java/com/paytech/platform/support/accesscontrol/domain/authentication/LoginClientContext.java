package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerAccount;
import com.paytech.platform.support.accesscontrol.domain.authentication.LoginClientContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.LoginPlan;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 本次密码登录的完整本库事实；事务失败后整体丢弃。 */
@Getter
@AllArgsConstructor
public class LoginClientContext {
    /** 事务外准备结果与预期版本。 */
    private final LoginPlan plan;
    /** 本次账号对象。 */
    private final CustomerAccount account;
    /** 本次数据库操作时刻。 */
    private final Instant now;
}
