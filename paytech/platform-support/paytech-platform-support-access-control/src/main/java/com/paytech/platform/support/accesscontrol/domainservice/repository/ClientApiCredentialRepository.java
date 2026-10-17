package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientApiCredential;

/** 工作区自助 API 凭证（t_api_credential 及其 Scope 授权行）的持久化合同；不返回 PHC 校验值。 */
public interface ClientApiCredentialRepository {
    /** 本租户凭证（含当前有效 Scope），按创建倒序；status 为空时不过滤。 */
    List<ClientApiCredential> credentials(long tenantId, String status, Instant asOf);

    /** 本租户指定凭证（含当前有效 Scope）；不属于本租户为空。 */
    ClientApiCredential credential(long tenantId, long credentialId, Instant asOf);

    /** 在调用方事务中锁定本租户指定凭证（含当前有效 Scope）；不属于本租户为空。 */
    ClientApiCredential lock(long tenantId, long credentialId, Instant asOf);

    /** 本租户未撤销且未到期的凭证数量。 */
    long activeCount(long tenantId, Instant asOf);

    /** 目录中实际存在的 Scope 编码。 */
    List<String> catalogCodes(Collection<String> codes);

    /** 插入新凭证及其 Scope 授权；scopeIds 与凭证 scopeCodes 一一对应。 */
    void insert(ClientApiCredential credential, String verifierPhc, List<Long> scopeIds);

    /** 按版本撤销，成功后同步原对象状态与版本。 */
    void revoke(ClientApiCredential credential, Instant now);

    /** 按版本把到期时间提前，成功后同步原对象到期时间与版本。 */
    void shorten(ClientApiCredential credential, Instant expiresTime, Instant now);
}
