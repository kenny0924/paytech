package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.accesscontrol.domain.apicredential.ApiCredentialCandidateView;

/** API Credential 根的只读持久化端口；关联租户仅用于同 Owner 的认证投影。 */
public interface ApiCredentialRepository {

    /** 按区分大小写的前缀读取有界候选及所属租户，不把前缀解释为身份。 */
    List<ApiCredentialCandidateView> candidates(String keyPrefix, int limit);
}
