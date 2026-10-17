package com.paytech.client.master.domainservice.service;

/** Client 级已存卡开关的只读能力；单表单行读取，不开事务。 */
public interface ClientStoredCardSettingQueryService {
    /**
     * 读取 Client 是否开通存卡与已存卡支付。
     *
     * @param clientId Client ID，必须为正数
     * @return 没有记录视为未开通
     */
    boolean isEnabled(long clientId);
}
