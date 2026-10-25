package com.paytech.client.master.domainservice.repository;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.LocalPartner;
import com.paytech.client.master.domain.entityadmin.LocalPartnerAdminView;

/** t_local_partner 的本地仓储；不负责其他表的加载或事务。 */
public interface LocalPartnerRepository {
    /** 读取本 Client Checkout 范围内的 local_partner 记录，保留历史和未生效事实供业务判断。 */
    List<LocalPartner> findForCheckout(long clientId);

    /** 按 ID 读取 Local Partner；不存在时返回 null。 */
    LocalPartner findById(long localPartnerId);

    /** 读取一个 Local Partner 的运营组合行（不含角色）；不存在时返回 null。 */
    LocalPartnerAdminView findAdminView(long localPartnerId);

    /** 分页读取运营组合行（不含角色）；筛选参数为 null 表示不限。 */
    List<LocalPartnerAdminView> pageForAdmin(String status, String role, int limit, int offset);

    /** 与分页相同筛选条件下的总数。 */
    long countForAdmin(String status, String role);
}
