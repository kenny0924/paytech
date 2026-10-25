package com.paytech.client.master.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import com.paytech.client.master.api.enums.AcquiringQualification;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.OperatingEntity;
import com.paytech.client.master.core.dal.mapper.OperatingEntityMapper;
import com.paytech.client.master.domain.entityadmin.OperatingEntityAdminView;
import com.paytech.client.master.domainservice.repository.OperatingEntityRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 OperatingEntityMapper 读写 t_operating_entity。 */
@AllArgsConstructor
public class OperatingEntityRepositoryImpl implements OperatingEntityRepository {
    private final OperatingEntityMapper mapper;

    /** 读取本 Client Checkout 范围内的 operating_entity 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<OperatingEntity> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }

    /** 按编号读取 OE；不存在时返回 null。 */
    @Override
    public OperatingEntity findByEntityNo(String entityNo) {
        return mapper.findByEntityNo(entityNo);
    }

    /** 影响行数必须为 1；OE 不允许删除，缺行说明数据不一致。 */
    @Override
    public void updateAcquiringQualification(String entityNo, AcquiringQualification qualification, Instant now) {
        if (mapper.updateAcquiringQualification(entityNo, qualification, now) != 1) {
            throw new ErrorException("经营主体收单资质写入未命中: " + entityNo);
        }
    }

    /** 读取一个 OE 的运营组合行；不存在时返回 null。 */
    @Override
    public OperatingEntityAdminView findAdminView(String entityNo) {
        return mapper.findAdminView(entityNo);
    }

    /** 分页读取运营组合行。 */
    @Override
    public List<OperatingEntityAdminView> pageForAdmin(String status, String sourceType, String country, int limit, int offset) {
        return List.copyOf(mapper.pageForAdmin(status, sourceType, country, limit, offset));
    }

    /** 与分页相同筛选条件下的总数。 */
    @Override
    public long countForAdmin(String status, String sourceType, String country) {
        return mapper.countForAdmin(status, sourceType, country);
    }
}
