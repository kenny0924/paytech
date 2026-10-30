package com.paytech.commercial.integration;

/** client-master Client 经营分类查询端口；只能在数据库事务外调用。 */
public interface ClientClassificationClient {
    /**
     * 读取 Client 已确认的主 MCC
     *
     * @param clientId Client ID
     * @return 主分类 MCC；分类未确认、没有经营资料或没有主分类时为 null
     *         <li>client-master 不可用、返回任何错误（含 Client 不存在 404）或响应不符合合同时抛出 OWNER_UNAVAILABLE（503）。</li>
     */
    String confirmedPrimaryMcc(long clientId);
}
