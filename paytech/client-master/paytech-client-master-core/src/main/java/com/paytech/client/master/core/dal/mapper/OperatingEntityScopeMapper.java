package com.paytech.client.master.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.OperatingEntityScope;

/** t_operating_entity_scope 的持久化查询。 */
public interface OperatingEntityScopeMapper {
    /** 读取本 Client Checkout 范围内的 operating_entity_scope 记录，保留历史和未生效事实供业务判断。 */
    List<OperatingEntityScope> findForCheckout(@Param("clientId") long clientId);

    /** 读取一个 OE 的全部准入范围，按 scopeId 升序。 */
    List<OperatingEntityScope> findByEntityNo(@Param("entityNo") String entityNo);
}
