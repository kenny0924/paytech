package com.paytech.acquiring.domainservice.repository.customer;

import com.paytech.acquiring.core.dal.dataobject.customer.CheckoutNetworkEvidence;
import com.paytech.acquiring.core.dal.mapper.customer.CheckoutNetworkEvidenceMapper;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import org.springframework.stereotype.Repository;

import lombok.AllArgsConstructor;

/** 仅访问所属表，写入失败交给调用方事务回滚。 */
@Repository
@AllArgsConstructor
public class CheckoutNetworkEvidenceRepositoryImpl implements CheckoutNetworkEvidenceRepository {
    private final CheckoutNetworkEvidenceMapper mapper;

    /** 只读取证据归属字段，不加载 IP、User-Agent 或代理链。 */
    @Override
    public CheckoutNetworkEvidence findBindingById(long evidenceId) {
        return mapper.findBindingById(evidenceId);
    }

    /** 保存服务端采集的法律接受证据，不包含卡信息。 */
    @Override
    public void insert(CheckoutNetworkEvidence evidence) {
        if (mapper.insert(evidence) != 1) {
            throw new ConcurrentRequestException("法律接受网络证据保存失败");
        }
    }
}
