package com.paytech.platform.support.accesscontrol.domainservice.service;

import com.paytech.platform.support.accesscontrol.domain.apimanagement.RevokeApiCredentialContext;

/** 凭证管理的本地保存能力，查询及用例编排由 Owner Processor 负责。 */
public interface ApiCredentialManagementService {

    /** 在调用方事务中撤销已校验的凭证，成功后同步 Context 中的原对象。 */
    void revoke(RevokeApiCredentialContext context);
}
