package com.paytech.client.master.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.client.master.api.enums.AcquiringQualification;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.OperatingEntity;
import com.paytech.client.master.domain.entityadmin.OperatingEntityAdminView;

/** t_operating_entity 的本地仓储；不负责其他表的加载或事务。 */
public interface OperatingEntityRepository {
    /** 读取本 Client Checkout 范围内的 operating_entity 记录，保留历史和未生效事实供业务判断。 */
    List<OperatingEntity> findForCheckout(long clientId);

    /** 按编号读取 OE；不存在时返回 null。 */
    OperatingEntity findByEntityNo(String entityNo);

    /** 写入收单资质；OE 不存在时抛出系统异常并回滚（调用方已在锁内确认存在）。 */
    void updateAcquiringQualification(String entityNo, AcquiringQualification qualification, Instant now);

    /** 读取一个 OE 的运营组合行；不存在时返回 null。 */
    OperatingEntityAdminView findAdminView(String entityNo);

    /** 分页读取运营组合行；筛选参数为 null 表示不限。 */
    List<OperatingEntityAdminView> pageForAdmin(String status, String sourceType, String country, int limit, int offset);

    /** 与分页相同筛选条件下的总数。 */
    long countForAdmin(String status, String sourceType, String country);
}
