package com.paytech.client.master.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.LocalPartner;
import com.paytech.client.master.domain.entityadmin.LocalPartnerAdminView;

/** t_local_partner 的持久化查询。 */
public interface LocalPartnerMapper {
    /** 读取本 Client Checkout 范围内的 local_partner 记录，保留历史和未生效事实供业务判断。 */
    List<LocalPartner> findForCheckout(@Param("clientId") long clientId);

    /** 按 ID 读取 Local Partner；不存在时返回 null。 */
    LocalPartner findById(@Param("localPartnerId") long localPartnerId);

    /** 读取一个 Local Partner 的运营组合行（不含角色）；不存在时返回 null。 */
    LocalPartnerAdminView findAdminView(@Param("localPartnerId") long localPartnerId);

    /** 按筛选条件分页读取运营组合行（不含角色），按 localPartnerId 升序；筛选参数为 null 表示不限。 */
    List<LocalPartnerAdminView> pageForAdmin(@Param("status") String status, @Param("role") String role,
        @Param("limit") int limit, @Param("offset") int offset);

    /** 与分页相同筛选条件下的总数。 */
    long countForAdmin(@Param("status") String status, @Param("role") String role);
}
