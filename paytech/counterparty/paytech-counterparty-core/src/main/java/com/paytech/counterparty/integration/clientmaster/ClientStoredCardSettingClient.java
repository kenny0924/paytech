package com.paytech.counterparty.integration.clientmaster;

/**
 * client-master 的 Client 已存卡开关端口（D42）：只在数据库事务外调用。
 * <li>依赖不可用时抛出 {@code ServiceUnavailableException}，协议不符时抛出上游协议异常。</li>
 */
public interface ClientStoredCardSettingClient {
    /**
     * Client 是否开通存卡与已存卡支付
     *
     * @param clientId Client ID
     * @return 是否开通；没有记录为 false
     */
    boolean storedCardEnabled(long clientId);
}
