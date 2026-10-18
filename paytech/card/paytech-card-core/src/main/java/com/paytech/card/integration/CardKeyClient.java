package com.paytech.card.integration;

/**
 * 本卡数据加密密钥（DEK）的托管端口：DEK 存 Vault，Payment 库只存 Vault Token（2026-10-15 Vault 卡 DEK 决定）。
 * <li>访问外部服务，只能在数据库事务外由 Card Processor 调用。</li>
 */
public interface CardKeyClient {
    /**
     * 把本卡 DEK 交 Vault 托管
     *
     * @param paymentCardId 卡 ID，构成 Vault 幂等键；同一张卡重放返回原 Token
     * @param dataKey 本卡 DEK
     * @return Vault REFERENCE Token
     */
    String protect(long paymentCardId, byte[] dataKey);

    /**
     * 取回本卡 DEK
     *
     * @param dekVaultToken 卡中的 Vault Token
     * @return DEK 字节，调用方用完后清零
     */
    byte[] reveal(String dekVaultToken);
}
