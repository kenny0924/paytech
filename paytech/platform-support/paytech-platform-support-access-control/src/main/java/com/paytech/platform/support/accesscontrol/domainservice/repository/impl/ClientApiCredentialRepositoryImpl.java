package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientApiCredential;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientApiCredentialScopeCode;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.ClientApiCredentialMapper;
import com.paytech.platform.support.accesscontrol.domain.enums.ApiCredentialStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ClientApiCredentialRepository;

import lombok.AllArgsConstructor;

/** 只委托 Mapper；条件写入必须准确影响一行。 */
@Repository
@AllArgsConstructor
public class ClientApiCredentialRepositoryImpl implements ClientApiCredentialRepository {
    private final ClientApiCredentialMapper mapper;

    /** 一次批量回填 Scope，避免逐行查询。 */
    @Override
    public List<ClientApiCredential> credentials(long tenantId, String status, Instant asOf) {
        return withScopes(mapper.credentials(tenantId, status, asOf), asOf);
    }

    /** 读取并回填 Scope。 */
    @Override
    public ClientApiCredential credential(long tenantId, long credentialId, Instant asOf) {
        return single(mapper.credential(tenantId, credentialId), asOf);
    }

    /** 锁定并回填 Scope。 */
    @Override
    public ClientApiCredential lock(long tenantId, long credentialId, Instant asOf) {
        return single(mapper.lock(tenantId, credentialId), asOf);
    }

    /** 未撤销且未到期的数量。 */
    @Override
    public long activeCount(long tenantId, Instant asOf) {
        return mapper.activeCount(tenantId, asOf);
    }

    /** 空集合不查询。 */
    @Override
    public List<String> catalogCodes(Collection<String> codes) {
        return codes.isEmpty() ? List.of() : mapper.catalogCodes(codes);
    }

    /** 凭证与全部 Scope 行在调用方事务中写入。 */
    @Override
    public void insert(ClientApiCredential credential, String verifierPhc, List<Long> scopeIds) {
        requireOne(mapper.insert(credential, verifierPhc));
        for (int index = 0; index < scopeIds.size(); index++) {
            requireOne(mapper.insertScope(scopeIds.get(index), credential.getCredentialId(),
                credential.getScopeCodes().get(index), credential.getCreateTime()));
        }
    }

    /** 版本不符视为并发修改。 */
    @Override
    public void revoke(ClientApiCredential credential, Instant now) {
        requireOne(mapper.revoke(credential.getCredentialId(), credential.getVersion(), now));
        credential.setStoredStatus(ApiCredentialStatus.REVOKED);
        credential.setVersion(credential.getVersion() + 1);
    }

    /** 版本不符视为并发修改。 */
    @Override
    public void shorten(ClientApiCredential credential, Instant expiresTime, Instant now) {
        requireOne(mapper.shorten(credential.getCredentialId(), credential.getVersion(), expiresTime, now));
        credential.setExpiresTime(expiresTime);
        credential.setVersion(credential.getVersion() + 1);
    }

    /** 单行回填。 */
    private ClientApiCredential single(ClientApiCredential credential, Instant asOf) {
        return credential == null ? null : withScopes(List.of(credential), asOf).getFirst();
    }

    /** 按凭证分组回填当前有效 Scope。 */
    private List<ClientApiCredential> withScopes(List<ClientApiCredential> credentials, Instant asOf) {
        if (credentials.isEmpty()) {
            return credentials;
        }
        var codes = mapper.scopeCodes(credentials.stream().map(ClientApiCredential::getCredentialId).toList(), asOf)
            .stream().collect(Collectors.groupingBy(ClientApiCredentialScopeCode::getCredentialId,
                Collectors.mapping(ClientApiCredentialScopeCode::getScopeCode, Collectors.toList())));
        credentials.forEach(credential -> credential.setScopeCodes(codes.getOrDefault(credential.getCredentialId(), List.of())));
        return credentials;
    }

    /** 写入必须准确影响一行。 */
    private static void requireOne(int rows) {
        if (rows != 1) {
            throw new ConcurrentRequestException("凭证已被其他操作修改，请刷新后重试");
        }
    }
}
