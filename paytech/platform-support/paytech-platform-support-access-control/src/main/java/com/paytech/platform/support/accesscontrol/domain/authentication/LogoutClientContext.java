package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientSession;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientRefresh;
import com.paytech.platform.support.accesscontrol.domain.authentication.LogoutClientContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.LogoutProof;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 本次当前会话退出的完整本库事实；事务失败后整体丢弃。 */
@Getter
@AllArgsConstructor
public class LogoutClientContext {
    /** 已计算的秘密证明。 */
    private final LogoutProof proof;
    /** 本次会话对象。 */
    private final ClientSession session;
    /** 本次刷新代对象。 */
    private final ClientRefresh refresh;
    /** 本次数据库操作时刻。 */
    private final Instant now;
}
