package com.paytech.client.master.integration;

/** 个人证件号的 Vault 保护端口（LEGAL_IDENTIFIER_V1，STABLE）；只能在数据库事务外调用。 */
public interface ClientIdentifierVaultClient {
    /**
     * 保护规范化后的证件号
     *
     * @param normalizedValue 已按标识类型规范化的号码；STABLE 模式下同值同 Token，Token 兼作唯一键比较值
     * @return STABLE Token
     *         <li>Vault 不可用或拒绝时抛出 ONBOARDING_DEPENDENCY_UNAVAILABLE，不保存任何标识。</li>
     */
    String protect(String normalizedValue);
}
