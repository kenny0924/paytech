package com.paytech.platform.support.accesscontrol.domain.apicredential;

import java.util.List;

import lombok.Getter;

/** 本次认证的一致性查询结果，仅在 Access Control 内部使用，不保存原始凭证。 */
@Getter
public final class ApiCredentialValidationContext {

    /** 含 PHC 的凭证及租户查询投影，不再复制同义持久化对象。 */
    private final ApiCredentialCandidateView candidate;

    /** 已校验并去重的同一快照有效授权。 */
    private final List<String> scopeCodes;

    /** 一次构造完整快照，数据库事务结束后用于 PHC 校验和身份组装。 */
    public ApiCredentialValidationContext(ApiCredentialCandidateView candidate, List<String> scopeCodes) {
        this.candidate = candidate;
        this.scopeCodes = List.copyOf(scopeCodes);
    }
}
