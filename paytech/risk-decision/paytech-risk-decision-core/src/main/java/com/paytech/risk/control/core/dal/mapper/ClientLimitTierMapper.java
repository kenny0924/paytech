package com.paytech.risk.control.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.risk.control.core.dal.dataobject.ClientLimitTier;
import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.domain.limittier.LimitTierListFilter;
import com.paytech.risk.control.domain.limittier.LimitTierPlacement;

/** Client 当前档位表映射。 */
public interface ClientLimitTierMapper {
    /** 插入档位行；Client 已有档位时违反唯一索引。 */
    int insert(@Param("row") ClientLimitTier row);

    /** 按 Client 读取档位行。 */
    ClientLimitTier selectByClientId(@Param("clientId") long clientId);

    /** 以期望版本与档位把档位行改为批准后的新值。 */
    int updateTier(@Param("row") ClientLimitTier row, @Param("next") LimitTierPlacement next);

    /** 按筛选条件分页读取，更新时间倒序。 */
    List<ClientLimitTier> selectPage(@Param("filter") LimitTierListFilter filter, @Param("limit") int limit,
        @Param("offset") long offset);

    /** 统计同一条件下的档位行数。 */
    long count(@Param("filter") LimitTierListFilter filter);

    /** 统计指定档位的 Client 数。 */
    long countByTier(@Param("tier") LimitTier tier);

    /** 统计观察期已过目标天数的 OBSERVATION 行数。 */
    long countObservationDue(@Param("now") Instant now);
}
