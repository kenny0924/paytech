package com.paytech.commercial.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.AgreementOperatingEntity;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;

/** t_agreement_operating_entity 的持久化查询。 */
public interface AgreementOperatingEntityMapper {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement_operating_entity 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.AgreementOperatingEntity> findForCheckout(@Param("clientId") long clientId, @Param("entityNos") List<String> entityNos,
        @Param("localPartnerIds") List<Long> localPartnerIds);

    /** 批量插入完整记录，返回影响行数；rows 必须非空。 */
    int insertAll(@Param("rows") List<AgreementOperatingEntity> rows);

    /** 按主键读取，不存在返回 null。 */
    AgreementOperatingEntity selectById(@Param("agreementOperatingEntityId") long agreementOperatingEntityId);

    /** 按唯一键（合同、OE）读取，不存在返回 null。 */
    AgreementOperatingEntity selectByUniqueKey(@Param("agreementId") long agreementId, @Param("entityNo") String entityNo);

    /** 读取一份合同覆盖的全部 OE，按 agreement_operating_entity_id 升序。 */
    List<AgreementOperatingEntity> selectByAgreementId(@Param("agreementId") long agreementId);

    /** 读取一组合同覆盖的全部 OE，按 agreement_operating_entity_id 升序；agreementIds 必须非空。 */
    List<AgreementOperatingEntity> selectByAgreementIds(@Param("agreementIds") Collection<Long> agreementIds);

    /** 按主键删除一行，返回影响行数。 */
    int deleteById(@Param("agreementOperatingEntityId") long agreementOperatingEntityId);
}
