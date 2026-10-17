package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSession;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsSessionPolicy;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsIdentityRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 当前 Session/Account/Member 的实时授权前置规则。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsSessionServiceImpl implements OperationsSessionService {
    private final Clock clock;
    private final OperationsAuditService audit;
    private final OperationsIdentityRepository repository;

    @Override
    public SessionContext requireCurrent(String accountId, String sessionId) {
        OperationsAccount account = repository.account(parseId(accountId));
        OperationsSession session = repository.session(parseId(sessionId));
        OperationsPassword password = account == null ? null : repository.password(account.id());
        OperationsMember member = account == null || account.type() == OperationsAccountType.BOOTSTRAP_ADMIN ? null
            : repository.member(account.id());
        OperationsSessionPolicy.requireValid(account, password, session, member, clock.instant());
        audit.identify(() -> new Identity(Long.toString(account.id()), session.tenantId()));
        return new SessionContext(account, password, member, session);
    }

    @Override
    public void requireOrdinary(SessionContext context) {
        if (context.password().forceChange()) {
            throw new BizCheckFailException("请先修改初始密码");
        }
        if (context.account().type() == OperationsAccountType.BOOTSTRAP_ADMIN) {
            throw new BizCheckFailException("初始化账号仅允许完成首次管理员设置");
        }
    }

    @Override
    public boolean isActive(OperationsMember member) {
        return OperationsSessionPolicy.isActive(member);
    }

    @Override
    public long parseId(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
                throw new NumberFormatException();
            }
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new InvalidParameterException("账号或会话标识必须为有效的正整数", exception);
        }
    }

}
