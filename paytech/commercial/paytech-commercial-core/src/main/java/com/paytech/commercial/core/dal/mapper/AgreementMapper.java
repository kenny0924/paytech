package com.paytech.commercial.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.domain.AgreementListQuery;
import com.paytech.commercial.domain.enums.AgreementStatus;

/** t_agreement 的持久化查询。 */
public interface AgreementMapper {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.Agreement> findForCheckout(@Param("clientId") long clientId, @Param("entityNos") List<String> entityNos,
        @Param("localPartnerIds") List<Long> localPartnerIds);

    /** 插入一行完整记录，返回影响行数。 */
    int insert(Agreement agreement);

    /** 按主键读取，不存在返回 null。 */
    Agreement selectById(@Param("agreementId") long agreementId);

    /** 按合同编号（uk_agreement_no）读取，不存在返回 null。 */
    Agreement selectByAgreementNo(@Param("agreementNo") String agreementNo);

    /** 读取一个 Client 的全部合同（含历史），按 agreement_id 升序。 */
    List<Agreement> selectByClientId(@Param("clientId") long clientId);

    /** 读取一个 Local Partner 的全部合同（含历史），按 agreement_id 升序。 */
    List<Agreement> selectByLocalPartnerId(@Param("localPartnerId") long localPartnerId);

    /** 仅当状态仍为 row.status 时迁移状态，返回影响行数。 */
    int updateStatus(@Param("row") Agreement row, @Param("status") AgreementStatus status, @Param("now") Instant now);

    /** 仅当状态仍为 row.status 时整体写入状态、生效终点、签署与激活时间，返回影响行数。 */
    int updateLifecycle(@Param("row") Agreement row, @Param("status") AgreementStatus status,
        @Param("effectiveTo") Instant effectiveTo, @Param("signedTime") Instant signedTime,
        @Param("activatedTime") Instant activatedTime, @Param("now") Instant now);

    /** 按列表条件读取一页合同，按创建时间、合同 ID 倒序；agreementNoPrefix 须已转义 LIKE 通配符。 */
    List<Agreement> selectPage(@Param("query") AgreementListQuery query, @Param("agreementNoPrefix") String agreementNoPrefix,
        @Param("offset") int offset, @Param("limit") int limit);

    /** 相同列表条件下的合同总数。 */
    long countPage(@Param("query") AgreementListQuery query, @Param("agreementNoPrefix") String agreementNoPrefix);
}
