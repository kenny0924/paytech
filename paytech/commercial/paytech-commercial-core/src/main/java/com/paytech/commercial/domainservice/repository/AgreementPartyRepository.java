package com.paytech.commercial.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.AgreementParty;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;

/** t_agreement_party 的本地仓储；不负责其他表的加载或事务。 */
public interface AgreementPartyRepository {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement_party 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.AgreementParty> findForCheckout(CommercialCheckoutScope scope);

    /** 被任一合同参与方引用的 Party ID；空集合不访问数据库。 */
    java.util.Set<Long> findReferencedPartyIds(java.util.Collection<Long> partyIds);

    /** 被任一合同参与方引用的资料 ID；空集合不访问数据库。 */
    java.util.Set<Long> findReferencedPartyProfileIds(java.util.Collection<Long> partyProfileIds);

    /**
     * 批量插入合同参与方；空列表不访问数据库，影响行数与列表大小不符时抛出系统异常，唯一键冲突原样抛出。
     * <li>创建与更新时间截断到微秒后写回对象。</li>
     *
     * @param rows 已准备 ID 与全部字段的新行
     */
    void insertAll(List<AgreementParty> rows);

    /**
     * 按主键读取。
     *
     * @param agreementPartyId Party 角色行 ID
     * @return 合同参与方，不存在为 null
     */
    AgreementParty findById(long agreementPartyId);

    /**
     * 按唯一键（合同、Party、角色）读取。
     *
     * @param agreementId 合同 ID
     * @param partyId 法律 Party ID
     * @param partyRole Party 角色
     * @return 合同参与方，不存在为 null
     */
    AgreementParty findByUniqueKey(long agreementId, long partyId, AgreementPartyPartyRole partyRole);

    /**
     * 读取一份合同的全部 Party 角色。
     *
     * @param agreementId 合同 ID
     * @return 按 agreement_party_id 升序的合同参与方
     */
    List<AgreementParty> findByAgreementId(long agreementId);

    /**
     * 按合同接受事件编号读取已登记的角色行，用于同引用重放判断。
     *
     * @param acceptanceReference 合同接受事件编号
     * @return 按 agreement_party_id 升序的合同参与方
     */
    List<AgreementParty> findByAcceptanceReference(String acceptanceReference);

    /**
     * 一次性登记最终签署结果：以 accepted_time 为空为条件写入 acceptance 携带的八个签署字段；已登记时抛出并发冲突，成功后同步原对象。
     * <li>acceptance 只读取 acceptanceMethod、acceptedByActorId、acceptedTime、evidenceDocumentId、acceptanceReference、
     * acceptanceRefSlot、acceptanceRecordedBy、acceptanceRecordedAt；同 Party 多角色由调用方在同一事务内逐行登记。</li>
     *
     * @param party 已读取的待签角色行
     * @param acceptance 签署字段载体
     * @param now 本次更新时间
     */
    void recordAcceptance(AgreementParty party, AgreementParty acceptance, Instant now);
}
