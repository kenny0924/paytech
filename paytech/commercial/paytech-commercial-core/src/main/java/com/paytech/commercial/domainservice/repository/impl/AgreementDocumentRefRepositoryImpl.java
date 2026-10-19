package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.util.List;

import com.paytech.commercial.core.dal.dataobject.AgreementDocumentRef;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.mapper.AgreementDocumentRefMapper;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;
import com.paytech.commercial.domainservice.repository.AgreementDocumentRefRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 AgreementDocumentRefMapper 读取 t_agreement_document_ref。 */
@AllArgsConstructor
public class AgreementDocumentRefRepositoryImpl implements AgreementDocumentRefRepository {
    private final AgreementDocumentRefMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement_document_ref 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<CheckoutRecords.AgreementDocumentRef> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }

    /** 空列表不访问数据库；截断时间精度后批量插入并核对影响行数。 */
    @Override
    public void insertAll(List<AgreementDocumentRef> rows) {
        if (rows.isEmpty()) {
            return;
        }
        for (AgreementDocumentRef row : rows) {
            row.setCreateTime(micros(row.getCreateTime()));
            row.setUpdateTime(micros(row.getUpdateTime()));
        }
        int inserted = mapper.insertAll(rows);
        if (inserted != rows.size()) {
            throw new ErrorException("合同文件引用插入行数不符: 期望 " + rows.size() + ", 实际 " + inserted);
        }
    }

    /** 按主键读取。 */
    @Override
    public AgreementDocumentRef findById(long agreementDocumentId) {
        return mapper.selectById(agreementDocumentId);
    }

    /** 按唯一键（合同、用途、文件）读取。 */
    @Override
    public AgreementDocumentRef findByUniqueKey(long agreementId, AgreementDocumentRefDocumentType documentType, long fileId) {
        return mapper.selectByUniqueKey(agreementId, documentType, fileId);
    }

    /** 读取一份合同的全部文件引用。 */
    @Override
    public List<AgreementDocumentRef> findByAgreementId(long agreementId) {
        return List.copyOf(mapper.selectByAgreementId(agreementId));
    }
}
