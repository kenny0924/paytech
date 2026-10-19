package com.paytech.commercial.domainservice.repository;

import java.util.List;

import com.paytech.commercial.core.dal.dataobject.AgreementDocumentRef;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;

/** t_agreement_document_ref 的本地仓储；不负责其他表的加载或事务。 */
public interface AgreementDocumentRefRepository {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement_document_ref 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.AgreementDocumentRef> findForCheckout(CommercialCheckoutScope scope);

    /**
     * 批量插入合同文件引用；空列表不访问数据库，影响行数与列表大小不符时抛出系统异常，唯一键冲突原样抛出。
     * <li>创建与更新时间截断到微秒后写回对象。</li>
     *
     * @param rows 已准备 ID 与全部字段的新行
     */
    void insertAll(List<AgreementDocumentRef> rows);

    /**
     * 按主键读取。
     *
     * @param agreementDocumentId 文件引用 ID
     * @return 合同文件引用，不存在为 null
     */
    AgreementDocumentRef findById(long agreementDocumentId);

    /**
     * 按唯一键（合同、用途、文件）读取。
     *
     * @param agreementId 合同 ID
     * @param documentType 文件用途
     * @param fileId 合同文件 ID
     * @return 合同文件引用，不存在为 null
     */
    AgreementDocumentRef findByUniqueKey(long agreementId, AgreementDocumentRefDocumentType documentType, long fileId);

    /**
     * 读取一份合同的全部文件引用。
     *
     * @param agreementId 合同 ID
     * @return 按 agreement_document_id 升序的合同文件引用
     */
    List<AgreementDocumentRef> findByAgreementId(long agreementId);
}
