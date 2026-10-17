package com.paytech.client.master.domainservice.repository;

/** Client 存卡开通数据访问；首期没有写入路径。 */
public interface ClientStoredCardSettingRepository {
    /** Client 是否已开通存卡；没有记录视为未开通。 */
    boolean isEnabled(long clientId);
}
