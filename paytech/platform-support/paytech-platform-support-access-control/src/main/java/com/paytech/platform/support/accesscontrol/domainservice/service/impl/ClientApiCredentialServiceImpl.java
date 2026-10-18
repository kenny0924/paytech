package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientApiCredentialChange;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ClientApiCredentialRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientApiCredentialService;

import lombok.AllArgsConstructor;

/** 只通过本表 Repository 写入；事务由调用方 Processor 持有。 */
@Service
@AllArgsConstructor
public class ClientApiCredentialServiceImpl implements ClientApiCredentialService {
    private final ClientApiCredentialRepository credentials;

    /** 保存新凭证与其 Scope 授权。 */
    @Override
    public void create(ClientApiCredentialChange change) {
        credentials.insert(change.getCreated(), change.getVerifierPhc(), change.getScopeIds());
    }

    /** 新凭证先写入；宽限为 0 撤销旧凭证，否则提前其到期时间。 */
    @Override
    public void rotate(ClientApiCredentialChange change) {
        credentials.insert(change.getCreated(), change.getVerifierPhc(), change.getScopeIds());
        if (change.getGraceMinutes() == 0) {
            credentials.revoke(change.getOriginal(), change.getNow());
            return;
        }
        credentials.shorten(change.getOriginal(), change.graceUntil(), change.getNow());
    }

    /** 撤销旧凭证。 */
    @Override
    public void revoke(ClientApiCredentialChange change) {
        credentials.revoke(change.getOriginal(), change.getNow());
    }
}
