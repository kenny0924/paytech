package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.time.Instant;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.mapper.AgreementMapper;
import com.paytech.commercial.domain.AgreementListQuery;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 AgreementMapper 读取 t_agreement。 */
@AllArgsConstructor
public class AgreementRepositoryImpl implements AgreementRepository {
    private final AgreementMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<CheckoutRecords.Agreement> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }

    /** 截断时间精度后插入并核对影响行数。 */
    @Override
    public void insert(Agreement agreement) {
        agreement.setCreateTime(micros(agreement.getCreateTime()));
        agreement.setUpdateTime(micros(agreement.getUpdateTime()));
        if (mapper.insert(agreement) != 1) {
            throw new ErrorException("合同插入行数不符: " + agreement.getAgreementId());
        }
    }

    /** 按主键读取。 */
    @Override
    public Agreement findById(long agreementId) {
        return mapper.selectById(agreementId);
    }

    /** 按合同编号（uk_agreement_no）读取。 */
    @Override
    public Agreement findByAgreementNo(String agreementNo) {
        return mapper.selectByAgreementNo(agreementNo);
    }

    /** 读取一个 Client 的全部合同（含历史）。 */
    @Override
    public List<Agreement> findByClientId(long clientId) {
        return List.copyOf(mapper.selectByClientId(clientId));
    }

    /** 读取一个 Local Partner 的全部合同（含历史）。 */
    @Override
    public List<Agreement> findByLocalPartnerId(long localPartnerId) {
        return List.copyOf(mapper.selectByLocalPartnerId(localPartnerId));
    }

    /** 条件迁移，成功后同步原对象。 */
    @Override
    public void updateStatus(Agreement agreement, AgreementStatus status, Instant now) {
        Instant time = micros(now);
        if (mapper.updateStatus(agreement, status, time) != 1) {
            throw new ConcurrentRequestException("合同状态已变化: " + agreement.getAgreementId());
        }
        agreement.setStatus(status);
        agreement.setUpdateTime(time);
    }

    /** 条件推进，成功后同步原对象。 */
    @Override
    public void updateLifecycle(Agreement agreement, AgreementStatus status, Instant effectiveTo, Instant signedTime,
        Instant activatedTime, Instant now) {
        Instant to = micros(effectiveTo);
        Instant signed = micros(signedTime);
        Instant activated = micros(activatedTime);
        Instant time = micros(now);
        if (mapper.updateLifecycle(agreement, status, to, signed, activated, time) != 1) {
            throw new ConcurrentRequestException("合同状态已变化: " + agreement.getAgreementNo());
        }
        agreement.setStatus(status);
        agreement.setEffectiveTo(to);
        agreement.setSignedTime(signed);
        agreement.setActivatedTime(activated);
        agreement.setUpdateTime(time);
    }

    /** 一页合同；合同编号前缀先转义 LIKE 通配符。 */
    @Override
    public List<Agreement> findPage(AgreementListQuery query) {
        return List.copyOf(mapper.selectPage(query, likePrefix(query.getAgreementNoPrefix()), query.getPaging().offset(),
            query.getPaging().size()));
    }

    /** 相同条件的总数。 */
    @Override
    public long count(AgreementListQuery query) {
        return mapper.countPage(query, likePrefix(query.getAgreementNoPrefix()));
    }

    /** 把字面前缀中的 LIKE 转义符与通配符（\ % _）转义为普通字符；空返回 null。 */
    static String likePrefix(String prefix) {
        if (prefix == null) {
            return null;
        }
        return prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
