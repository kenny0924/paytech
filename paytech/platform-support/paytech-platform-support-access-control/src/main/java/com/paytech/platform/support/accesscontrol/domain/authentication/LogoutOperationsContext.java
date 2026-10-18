package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRefresh;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSession;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已完成关系校验的本库认证上下文；回滚后整体丢弃。 */
@Getter
@AllArgsConstructor
public class LogoutOperationsContext {
    /** 当前锁定会话。 */
    private final OperationsSession session;

    /** 已经证明持有的刷新代。 */
    private final OperationsRefresh refresh;

    /** 撤销时点。 */
    private final Instant now;

}
