package com.paytech.commerce.domainservice.repository.customer;

import org.springframework.stereotype.Repository;

import com.paytech.commerce.core.dal.mapper.customer.ClientStoredCardSettingMapper;

import lombok.AllArgsConstructor;

/** Client 存卡开通数据访问。 */
@Repository
@AllArgsConstructor
public class ClientStoredCardSettingRepositoryImpl implements ClientStoredCardSettingRepository {
    private final ClientStoredCardSettingMapper mapper;

    @Override
    public boolean isEnabled(long clientId) {
        return Boolean.TRUE.equals(mapper.findEnabled(clientId));
    }
}
