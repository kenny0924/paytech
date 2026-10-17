package com.paytech.client.master.domainservice.service.impl;

import com.paytech.client.master.domainservice.repository.ClientStoredCardSettingRepository;
import com.paytech.client.master.domainservice.service.ClientStoredCardSettingQueryService;

import lombok.AllArgsConstructor;

/** 直接委托单表仓储；数据库故障按公共异常边界返回 5xx。 */
@AllArgsConstructor
public class ClientStoredCardSettingQueryServiceImpl implements ClientStoredCardSettingQueryService {
    private final ClientStoredCardSettingRepository repository;

    /** 没有记录视为未开通。 */
    @Override
    public boolean isEnabled(long clientId) {
        return repository.isEnabled(clientId);
    }
}
