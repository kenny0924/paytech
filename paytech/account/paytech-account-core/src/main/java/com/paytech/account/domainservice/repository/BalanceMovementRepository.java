package com.paytech.account.domainservice.repository;

import java.util.List;

import com.paytech.account.api.enums.MovementSourceType;
import com.paytech.account.core.dal.dataobject.BalanceMovement;

/** t_balance_movement：余额桶移动命令记录读写。 */
public interface BalanceMovementRepository {

    /** 插入移动；同一来源已被并发登记时以并发冲突失败。 */
    void insert(BalanceMovement movement);

    /** 按来源读取，不存在返回空。 */
    BalanceMovement findBySource(MovementSourceType sourceType, String sourceRef);

    /** 按主键读取一组移动；空集合返回空列表。 */
    List<BalanceMovement> findByIds(List<Long> ids);
}
