package com.paytech.commercial.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.AgreementDocumentRef;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;

/** t_agreement_document_ref 的持久化查询。 */
public interface AgreementDocumentRefMapper {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement_document_ref 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.AgreementDocumentRef> findForCheckout(@Param("clientId") long clientId, @Param("entityNos") List<String> entityNos,
        @Param("localPartnerIds") List<Long> localPartnerIds);

    /** 批量插入完整记录，返回影响行数；rows 必须非空。 */
    int insertAll(@Param("rows") List<AgreementDocumentRef> rows);

    /** 按主键读取，不存在返回 null。 */
    AgreementDocumentRef selectById(@Param("agreementDocumentId") long agreementDocumentId);

    /** 按唯一键（合同、用途、文件）读取，不存在返回 null。 */
    AgreementDocumentRef selectByUniqueKey(@Param("agreementId") long agreementId, @Param("documentType") AgreementDocumentRefDocumentType documentType, @Param("fileId") long fileId);

    /** 读取一份合同的全部文件引用，按 agreement_document_id 升序。 */
    List<AgreementDocumentRef> selectByAgreementId(@Param("agreementId") long agreementId);
}
