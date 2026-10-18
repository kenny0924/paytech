package com.paytech.acquiring.domainservice.repository.order;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.acquiring.core.dal.dataobject.customer.FrozenCheckoutRecord;
import com.paytech.acquiring.core.dal.mapper.customer.CustomerCheckoutMapper;

import lombok.AllArgsConstructor;

import org.springframework.stereotype.Repository;

/** 仅写入订单法律事实表。 */
@Repository
@AllArgsConstructor
public class OrderLegalRepositoryImpl implements OrderLegalRepository {
    private final CustomerCheckoutMapper mapper;

    /** 原子写入由上层 Service 管理。 */
    @Override
    public void insert(FrozenCheckoutRecord row) {
        if (mapper.insertLegal(row) != 1) {
            throw new ErrorException("准备事实未成功保存");
        }
    }
}
