package com.paytech.risk.decision.core.dal.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.risk.decision.core.dal.dataobject.ClientAllowedCurrencyTotal;
import com.paytech.risk.decision.core.dal.dataobject.ClientAllowedDailyTotal;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;

/** Client 日放行汇总表映射。 */
public interface ClientAllowedDailyTotalMapper {
    /** 插入当日行，已有时把金额与笔数累加到原行。 */
    int upsertAdd(@Param("row") ClientAllowedDailyTotal row);

    /** 合计 [fromDate, toDate] 内与币种相同的放行金额；没有行时为 0。 */
    BigDecimal sumAmount(@Param("clientId") long clientId, @Param("eventCode") VelocityEventCode eventCode,
        @Param("currency") String currency, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /** 按币种合计当日与 [fromDate, today] 的放行金额，按币种排序。 */
    List<ClientAllowedCurrencyTotal> totalsByCurrency(@Param("clientId") long clientId,
        @Param("eventCode") VelocityEventCode eventCode, @Param("today") LocalDate today,
        @Param("fromDate") LocalDate fromDate);
}
