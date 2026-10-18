package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.mapper.ClientStoredCardSettingMapper;
import com.paytech.client.master.domainservice.repository.ClientStoredCardSettingRepository;

import lombok.AllArgsConstructor;

/** 只通过 ClientStoredCardSettingMapper 读取 t_client_stored_card_setting。 */
@AllArgsConstructor
public class ClientStoredCardSettingRepositoryImpl implements ClientStoredCardSettingRepository {
    private final ClientStoredCardSettingMapper mapper;

    /** 没有记录或关闭时返回 false。 */
    @Override
    public boolean isEnabled(long clientId) {
        return Boolean.TRUE.equals(mapper.findEnabled(clientId));
    }
}
