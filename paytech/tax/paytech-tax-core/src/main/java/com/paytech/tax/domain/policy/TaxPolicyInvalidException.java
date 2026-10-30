package com.paytech.tax.domain.policy;

import java.io.Serial;

/**
 * 地区包内容不符合 Schema 版本 1 或规则不互斥
 * <li>只在 tax 内部流转：计算遇到它返回 BLOCKED(POLICY_INVALID)，不越过业务边界；消息只含字段路径，不含规则内容。</li>
 */
public class TaxPolicyInvalidException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 以违规路径构造
     *
     * @param path 违规字段路径，例如 treatments[1].result.rate
     */
    public TaxPolicyInvalidException(String path) {
        super("地区包内容不合法: " + path);
    }
}
