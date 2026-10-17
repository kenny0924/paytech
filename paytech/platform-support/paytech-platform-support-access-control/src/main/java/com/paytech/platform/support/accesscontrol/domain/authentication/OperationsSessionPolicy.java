package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.time.Instant;

import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSession;
import com.paytech.platform.support.accesscontrol.domain.enums.*;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.api.enums.OperationsContextType;
import com.paytech.platform.support.api.enums.OperationsErrorCode;

/** 对当前数据库事实执行会话失效、凭据版本和 Tenant 边界规则。 */
public final class OperationsSessionPolicy {
    private OperationsSessionPolicy() {}

    public static void requireValid(OperationsAccount account, OperationsPassword password, OperationsSession session, OperationsMember member, Instant now) {
        if (account == null || session == null || account.status() != OperationsAccountStatus.ACTIVE
            || session.accountId() != account.id() || session.status() != OperationsSessionStatus.ACTIVE
            || !now.isBefore(session.idleExpireTime()) || !now.isBefore(session.absoluteExpireTime())
            || session.accountEpoch() != account.authEpoch()
            || session.credentialVersion() != account.credentialVersion() || password == null
            || password.accountId() != account.id() || password.status() != OperationsCredentialStatus.ACTIVE
            || password.credentialVersion() != account.credentialVersion()) {
            invalid();
        }
        if (account.type() == OperationsAccountType.BOOTSTRAP_ADMIN) {
            if (session.contextType() != OperationsContextType.BOOTSTRAP || session.tenantId() != null
                || session.memberId() != null) {
                invalid();
            }
            return;
        }
        if (member == null || session.contextType() != OperationsContextType.TENANT || session.memberId() == null
            || session.tenantId() == null || member.accountId() != account.id() || member.id() != session.memberId()
            || member.tenantId() != session.tenantId() || !isActive(member)) {
            invalid();
        }
    }

    public static boolean isActive(OperationsMember member) {
        return member != null && member.status() == OperationsMemberStatus.ACTIVE
            && member.ownerStatus() == OperationsTenantOwnerStatus.ACTIVE
            && member.securityStatus() == OperationsTenantSecurityStatus.ACTIVE;
    }

    private static void invalid() {
        throw new FailException(OperationsErrorCode.OPERATIONS_SESSION_INVALID);
    }
}
