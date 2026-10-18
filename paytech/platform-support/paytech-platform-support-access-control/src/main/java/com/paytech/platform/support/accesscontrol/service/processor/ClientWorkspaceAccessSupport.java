package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientWorkspaceAccess;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientSessionContextType;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ClientMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ClientPermissionRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;

import lombok.AllArgsConstructor;

/**
 * CLIENT 工作区自助用例共用的会话重证与权限判断
 * <li>工作区只取自当前有效的租户上下文会话；账号上下文或工作区不可访问统一返回 CLIENT_WORKSPACE_UNAVAILABLE。</li>
 * <li>权限按“成员有效角色授予 → 有效角色 → 有效角色权限 → 有效权限”实时判定，不缓存。</li>
 * <li>只读本库；调用方负责放在合适的事务快照内，与写入一起重证。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ClientWorkspaceAccessSupport {
    private final Clock clock;
    private final ClientMemberRepository members;
    private final ClientPermissionRepository permissions;
    private final PlatformTransactionManager transactionManager;
    private final CustomerAuthenticationService authentication;

    /** 重证当前租户上下文会话并解析工作区坐标。 */
    public ClientWorkspaceAccess workspace(long accountId, long sessionId) {
        var context = authentication.currentContext(accountId, sessionId);
        if (!ClientSessionContextType.TENANT.getCode().equals(context.contextType()) || context.memberId() == null
            || context.tenantId() == null) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE);
        }
        var member = members.member(context.memberId());
        if (member == null || member.tenantId() != context.tenantId() || member.accountId() != accountId) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE);
        }
        return new ClientWorkspaceAccess(accountId, sessionId, member.tenantId(), member.id(), member.clientId());
    }

    /** 返回给定权限码中已授予的部分，保持请求顺序并去重。 */
    public List<String> granted(ClientWorkspaceAccess access, Collection<String> codes) {
        var granted = permissions.granted(access.getMemberId(), access.getTenantId(), codes,
            clock.instant().truncatedTo(ChronoUnit.MICROS));
        return codes.stream().distinct().filter(granted::contains).toList();
    }

    /** 重证工作区并要求指定权限，未授予返回 CLIENT_PERMISSION_DENIED。 */
    public ClientWorkspaceAccess require(long accountId, long sessionId, String permission) {
        var access = workspace(accountId, sessionId);
        if (granted(access, List.of(permission)).isEmpty()) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_PERMISSION_DENIED);
        }
        return access;
    }

    /** 只读查询：会话重证、权限判断与多表读取同一可重复读快照。 */
    public TransactionTemplate readTransaction() {
        TransactionTemplate query = new TransactionTemplate(transactionManager);
        query.setReadOnly(true);
        query.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        query.setTimeout(15);
        return query;
    }

    /** 写入：读已提交、十五秒超时；会话与权限在同一事务内重证。 */
    public TransactionTemplate writeTransaction() {
        TransactionTemplate write = new TransactionTemplate(transactionManager);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        write.setTimeout(15);
        return write;
    }
}
