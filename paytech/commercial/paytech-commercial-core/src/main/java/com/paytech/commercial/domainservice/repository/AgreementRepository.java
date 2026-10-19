package com.paytech.commercial.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.enums.AgreementStatus;

/** t_agreement 的本地仓储；不负责其他表的加载或事务。 */
public interface AgreementRepository {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.Agreement> findForCheckout(CommercialCheckoutScope scope);

    /**
     * 插入合同；影响行数不为 1 时抛出系统异常，唯一键冲突原样抛出。
     * <li>创建与更新时间截断到微秒后写回对象。</li>
     *
     * @param agreement 已准备 ID 与全部字段的新行
     */
    void insert(Agreement agreement);

    /**
     * 按主键读取。
     *
     * @param agreementId 合同 ID
     * @return 合同，不存在为 null
     */
    Agreement findById(long agreementId);

    /**
     * 按合同编号（uk_agreement_no）读取。
     *
     * @param agreementNo 合同编号
     * @return 合同，不存在为 null
     */
    Agreement findByAgreementNo(String agreementNo);

    /**
     * 读取一个 Client 的全部合同（含历史）。
     *
     * @param clientId Client ID
     * @return 按 agreement_id 升序的合同
     */
    List<Agreement> findByClientId(long clientId);

    /**
     * 读取一个 Local Partner 的全部合同（含历史）。
     *
     * @param localPartnerId Local Partner ID
     * @return 按 agreement_id 升序的合同
     */
    List<Agreement> findByLocalPartnerId(long localPartnerId);

    /**
     * 以对象当前状态为条件迁移状态；未命中抛出并发冲突，成功后同步原对象的状态与更新时间。
     *
     * @param agreement 已读取的行
     * @param status 目标状态
     * @param now 本次更新时间
     */
    void updateStatus(Agreement agreement, AgreementStatus status, Instant now);

    /**
     * 以对象当前状态为条件推进生命周期：状态与三个时间整体写入，不变的时间传对象原值；未命中抛出并发冲突，成功后同步原对象。
     * <li>用于签署（SIGNED + signedTime）、激活（ACTIVE + activatedTime）与终止 / 到期（effectiveTo）。</li>
     *
     * @param agreement 已读取的合同
     * @param status 目标状态
     * @param effectiveTo 生效终点（不含），可空
     * @param signedTime 签署时刻，可空
     * @param activatedTime 激活时刻，可空
     * @param now 本次更新时间
     */
    void updateLifecycle(Agreement agreement, AgreementStatus status, Instant effectiveTo, Instant signedTime,
        Instant activatedTime, Instant now);
}
