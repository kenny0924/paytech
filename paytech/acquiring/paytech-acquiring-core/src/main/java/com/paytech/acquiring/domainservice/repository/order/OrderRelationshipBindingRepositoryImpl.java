package com.paytech.acquiring.domainservice.repository.order;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.acquiring.core.dal.dataobject.order.OrderRelationshipBinding;
import com.paytech.acquiring.core.dal.mapper.order.OrderRelationshipBindingMapper;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** OrderRelationshipBinding 的 MyBatis 持久化。 */
@Repository
@AllArgsConstructor
public class OrderRelationshipBindingRepositoryImpl implements OrderRelationshipBindingRepository {
    private final OrderRelationshipBindingMapper mapper;

    /** 固定 Mock 解析没有快照时不写；插入后核对行数，失败由调用方事务整体回滚，唯一键冲突原样抛出。 */
    @Override
    public void insertAll(List<OrderRelationshipBinding> bindings) {
        if (bindings.isEmpty()) {
            return;
        }
        if (mapper.insertAll(bindings) != bindings.size()) {
            throw new ErrorException("插入 OrderRelationshipBinding 受影响行数异常");
        }
    }
}
