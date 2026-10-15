package com.paytech.commerce.domainservice.repository.customer;

/** Client 存卡开通数据访问。 */
public interface ClientStoredCardSettingRepository {
    /** Client 是否已开通存卡；没有记录视为未开通。 */
    boolean isEnabled(long clientId);
}
