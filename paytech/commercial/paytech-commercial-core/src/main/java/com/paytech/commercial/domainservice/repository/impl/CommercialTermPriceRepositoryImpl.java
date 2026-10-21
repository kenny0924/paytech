package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.mapper.CommercialTermPriceMapper;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domainservice.repository.CommercialTermPriceRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 CommercialTermPriceMapper 读取 t_commercial_term_price。 */
@AllArgsConstructor
public class CommercialTermPriceRepositoryImpl implements CommercialTermPriceRepository {
    private final CommercialTermPriceMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_price 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<CheckoutRecords.CommercialTermPrice> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }

    /** 空列表不访问数据库；截断时间精度后批量插入并核对影响行数。 */
    @Override
    public void insertAll(List<CommercialTermPrice> rows) {
        if (rows.isEmpty()) {
            return;
        }
        for (CommercialTermPrice row : rows) {
            row.setCreateTime(micros(row.getCreateTime()));
            row.setUpdateTime(micros(row.getUpdateTime()));
        }
        int inserted = mapper.insertAll(rows);
        if (inserted != rows.size()) {
            throw new ErrorException("商业条款价格插入行数不符: 期望 " + rows.size() + ", 实际 " + inserted);
        }
    }

    /** 按主键读取。 */
    @Override
    public CommercialTermPrice findById(long termPriceId) {
        return mapper.selectById(termPriceId);
    }

    /** 按唯一键（版本、OE、币种、画像）读取。 */
    @Override
    public CommercialTermPrice findByUniqueKey(long termVersionId, String entityNo, String currency, String pricingProfileCode) {
        return mapper.selectByUniqueKey(termVersionId, entityNo, currency, pricingProfileCode);
    }

    /** 读取一个版本的全部价格。 */
    @Override
    public List<CommercialTermPrice> findByVersionId(long termVersionId) {
        return List.copyOf(mapper.selectByVersionId(termVersionId));
    }

    /** 删除一个版本的全部价格，用于 DRAFT 内整体替换；组件与档位由调用方先删除。 */
    @Override
    public int deleteByVersionId(long termVersionId) {
        return mapper.deleteByVersionId(termVersionId);
    }

    /** 按每条价格的版本 ID 分组计数。 */
    @Override
    public Map<Long, Integer> countByVersionIds(Collection<Long> termVersionIds) {
        if (termVersionIds.isEmpty()) {
            return Map.of();
        }
        return mapper.selectVersionIdsOfPrices(termVersionIds).stream()
            .collect(Collectors.toMap(Function.identity(), versionId -> 1, Integer::sum));
    }

    /** 空集合不访问数据库。 */
    @Override
    public List<CommercialTermPrice> findByIds(Collection<Long> termPriceIds) {
        if (termPriceIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.selectByIds(termPriceIds));
    }

    /** 空集合不访问数据库。 */
    @Override
    public List<CommercialTermPrice> findByVersionIds(Collection<Long> termVersionIds) {
        if (termVersionIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.selectByVersionIds(termVersionIds));
    }
}
