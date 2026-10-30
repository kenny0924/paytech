package com.paytech.payout.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.payout.core.dal.dataobject.BeneficiaryAccountEvent;

/** t_beneficiary_account_event：收款账户事件的 MyBatis 映射；SQL 位于 mapper/payout/BeneficiaryAccountEventMapper.xml。 */
@Mapper
public interface BeneficiaryAccountEventMapper {
    /** 插入一行；主键由应用分配。 */
    int insert(BeneficiaryAccountEvent record);

    /** 账户的全部事件，按事件序号升序。 */
    List<BeneficiaryAccountEvent> selectByAccount(@Param("beneficiaryAccountId") Long beneficiaryAccountId);

    /** 账户内的一条事件，不存在返回 null。 */
    BeneficiaryAccountEvent selectByEventNo(@Param("beneficiaryAccountId") Long beneficiaryAccountId,
        @Param("eventNo") Integer eventNo);

    /** 账户当前最大事件序号；没有事件返回 0。 */
    int selectMaxEventNo(@Param("beneficiaryAccountId") Long beneficiaryAccountId);
}
