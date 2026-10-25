package com.paytech.client.master.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.api.enums.AcquiringQualification;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.OperatingEntity;
import com.paytech.client.master.domain.entityadmin.OperatingEntityAdminView;

/** t_operating_entity 的持久化查询与收单资质更新。 */
public interface OperatingEntityMapper {
    /** 读取本 Client Checkout 范围内的 operating_entity 记录，保留历史和未生效事实供业务判断。 */
    List<OperatingEntity> findForCheckout(@Param("clientId") long clientId);

    /** 按编号读取 OE；不存在时返回 null。 */
    OperatingEntity findByEntityNo(@Param("entityNo") String entityNo);

    /** 写入收单资质，返回影响行数。 */
    int updateAcquiringQualification(@Param("entityNo") String entityNo,
        @Param("qualification") AcquiringQualification qualification, @Param("now") Instant now);

    /** 读取一个 OE 的运营组合行；不存在时返回 null。 */
    OperatingEntityAdminView findAdminView(@Param("entityNo") String entityNo);

    /** 按筛选条件分页读取运营组合行，按 entityNo 升序；筛选参数为 null 表示不限。 */
    List<OperatingEntityAdminView> pageForAdmin(@Param("status") String status, @Param("sourceType") String sourceType,
        @Param("country") String country, @Param("limit") int limit, @Param("offset") int offset);

    /** 与分页相同筛选条件下的总数。 */
    long countForAdmin(@Param("status") String status, @Param("sourceType") String sourceType,
        @Param("country") String country);
}
