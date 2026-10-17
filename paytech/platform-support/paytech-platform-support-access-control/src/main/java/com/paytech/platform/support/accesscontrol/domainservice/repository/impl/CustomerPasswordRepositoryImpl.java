package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerPassword;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.CustomerRegistrationMapper;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.CustomerAuthenticationMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.CustomerPasswordRepository;
import org.springframework.stereotype.Repository;
import lombok.AllArgsConstructor;

/** CustomerPassword 的单表持久化实现。 */
@Repository
@AllArgsConstructor
public class CustomerPasswordRepositoryImpl implements CustomerPasswordRepository {
    private final CustomerRegistrationMapper registration;
    private final CustomerAuthenticationMapper authentication;

    /** 读取同一 CUSTOMER 账号的密码凭据。 */
    @Override
    public CustomerPassword password(long accountId) {
        return authentication.password(accountId);
    }

    /** 保存初始 Argon2id verifier，不保存密码明文。 */
    @Override
    public void insertPassword(CustomerPassword row, Instant now) {
        changed(registration.insertPassword(row.id(), row.accountId(), row.verifier(), now));
    }

    /** 按当前凭据版本替换密码 verifier。 */
    @Override
    public void resetPassword(CustomerPassword row, String verifier, Instant now) {
        changed(registration.resetPassword(row.accountId(), row.credentialVersion(), verifier, now));
        row.verifier(verifier);
        row.algorithmVersion(19);
        row.memoryKib(19456);
        row.iterations(2);
        row.parallelism(1);
        row.credentialVersion(row.credentialVersion() + 1);
        row.forceChange(false);
    }

    /** 条件更新必须准确修改一行；失败时不更新内存对象。 */
    void changed(int count) {
        if (count != 1) { throw new ConcurrentRequestException("认证状态已变化，请重新操作"); }
    }
}
