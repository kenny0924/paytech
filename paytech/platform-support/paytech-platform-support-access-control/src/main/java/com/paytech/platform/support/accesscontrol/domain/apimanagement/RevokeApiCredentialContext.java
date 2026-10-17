package com.paytech.platform.support.accesscontrol.domain.apimanagement;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiCredentialManagement;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已通过关系与版本校验的本地撤销上下文，持有锁定的原始数据库对象。 */
@Getter
@AllArgsConstructor
public class RevokeApiCredentialContext {

    /** 本次锁定并校验的凭证，保存后在原对象上同步状态与版本。 */
    private final ApiCredentialManagement credential;

    /** 冻结的 UTC 微秒撤销时刻。 */
    private final Instant revokeTime;
}
