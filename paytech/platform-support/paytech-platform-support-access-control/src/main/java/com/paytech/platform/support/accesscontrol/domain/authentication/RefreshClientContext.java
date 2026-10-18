package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientSession;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientRefresh;
import com.paytech.platform.support.accesscontrol.domain.authentication.RefreshClientContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.RefreshPlan;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 本次刷新令牌轮换的完整本库事实；事务失败后整体丢弃。 */
@Getter
@AllArgsConstructor
public class RefreshClientContext {
    /** 事务外准备结果与预期版本。 */
    private final RefreshPlan plan;
    /** 本次账号对象。 */
    private final CustomerAccount account;
    /** 本次会话对象。 */
    private final ClientSession session;
    /** 本次刷新代对象。 */
    private final ClientRefresh refresh;
    /** 本次数据库操作时刻。 */
    private final Instant now;
}
