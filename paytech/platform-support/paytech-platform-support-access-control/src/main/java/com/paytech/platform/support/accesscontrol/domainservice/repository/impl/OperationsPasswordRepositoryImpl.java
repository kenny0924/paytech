package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsCredentialStatus;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.OperationsIdentityMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsPasswordRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import org.springframework.stereotype.Repository;
import lombok.AllArgsConstructor;

/** 条件写成功后同步原对象；事务由完整用例控制。 */
@Repository
@AllArgsConstructor
public class OperationsPasswordRepositoryImpl implements OperationsPasswordRepository {
    private final OperationsIdentityMapper mapper;
    /** 读取当前 Argon2id 凭据。 */
    @Override
    public OperationsPassword password(long accountId) {
        return mapper.password(accountId);
    }

    /** 按原凭据版本替换密码。 */
    @Override
    public void changePassword(OperationsPassword password, String verifier, Instant now) {
        requireOne(mapper.changePassword(password.accountId(), password.credentialVersion(), verifier, now));
        password.verifier(verifier);
        password.algorithmVersion(19);
        password.memoryKib(19456);
        password.iterations(2);
        password.parallelism(1);
        password.credentialVersion(password.credentialVersion() + 1);
        password.forceChange(false);
    }

    /** 保存强制首次改密的初始凭据。 */
    @Override
    public void insert(OperationsPassword password, Instant now) {
        requireOne(mapper.insertPassword(password.id(), password.accountId(), password.verifier(), now));
    }

    /** 使当前凭据永久失效。 */
    @Override
    public void revoke(OperationsPassword password, Instant now) {
        requireOne(mapper.revokePassword(password.accountId(), now));
        password.status(OperationsCredentialStatus.REVOKED);
    }

    /** 条件写必须影响恰好一行，否则使整个本地事务回滚。 */
    void requireOne(int rows) {
        if (rows != 1) { throw new ConcurrentRequestException("认证事实已变化，请重新操作"); }
    }
}
