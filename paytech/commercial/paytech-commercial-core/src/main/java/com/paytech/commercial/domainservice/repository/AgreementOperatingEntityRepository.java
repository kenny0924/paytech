package com.paytech.commercial.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.AgreementOperatingEntity;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.domain.CommercialCheckoutScope;

/** t_agreement_operating_entity 的本地仓储；不负责其他表的加载或事务。 */
public interface AgreementOperatingEntityRepository {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement_operating_entity 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.AgreementOperatingEntity> findForCheckout(CommercialCheckoutScope scope);

    /**
     * 批量插入合同覆盖 OE；空列表不访问数据库，影响行数与列表大小不符时抛出系统异常，唯一键冲突原样抛出。
     * <li>创建与更新时间截断到微秒后写回对象。</li>
     *
     * @param rows 已准备 ID 与全部字段的新行
     */
    void insertAll(List<AgreementOperatingEntity> rows);

    /**
     * 按主键读取。
     *
     * @param agreementOperatingEntityId 覆盖关系 ID
     * @return 合同覆盖 OE，不存在为 null
     */
    AgreementOperatingEntity findById(long agreementOperatingEntityId);

    /**
     * 按唯一键（合同、OE）读取。
     *
     * @param agreementId 合同 ID
     * @param entityNo 准确 OE
     * @return 合同覆盖 OE，不存在为 null
     */
    AgreementOperatingEntity findByUniqueKey(long agreementId, String entityNo);

    /**
     * 读取一份合同覆盖的全部 OE。
     *
     * @param agreementId 合同 ID
     * @return 按 agreement_operating_entity_id 升序的合同覆盖 OE
     */
    List<AgreementOperatingEntity> findByAgreementId(long agreementId);

    /**
     * 读取一组合同覆盖的全部 OE；空集合不访问数据库。
     *
     * @param agreementIds 合同 ID
     * @return 按 agreement_operating_entity_id 升序的合同覆盖 OE
     */
    List<AgreementOperatingEntity> findByAgreementIds(Collection<Long> agreementIds);

    /**
     * 删除一行合同覆盖 OE；行已不存在时抛出并发冲突。
     *
     * @param agreementOperatingEntity 已读取的行
     */
    void delete(AgreementOperatingEntity agreementOperatingEntity);
}
