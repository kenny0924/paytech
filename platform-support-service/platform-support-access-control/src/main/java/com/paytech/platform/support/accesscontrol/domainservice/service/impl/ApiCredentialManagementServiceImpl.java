package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.platform.support.accesscontrol.domain.apimanagement.RevokeApiCredentialContext;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiCredentialManagementRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ApiCredentialManagementService;

import lombok.AllArgsConstructor;

/** 仅执行本地凭证状态保存，不重新鉴权、不查询关系、不调用其他 Service。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ApiCredentialManagementServiceImpl implements ApiCredentialManagementService {

    private final ApiCredentialManagementRepository repository;

    /** 使用已校验 Context 执行条件更新；冲突由 Repository 行数检查报告。 */
    @Override
    public void revoke(RevokeApiCredentialContext context) {
        repository.revoke(context.getCredential(), context.getRevokeTime());
    }
}
