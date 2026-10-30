package com.paytech.risk.decision.domainservice.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.paytech.risk.decision.core.dal.dataobject.ClientAllowedCurrencyTotal;
import com.paytech.risk.decision.core.dal.dataobject.ClientAllowedDailyTotal;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;

/** Client 日放行汇总的单表持久化与读取边界。 */
public interface ClientAllowedDailyTotalRepository {
    /** 在调用方事务内把本次放行累加到当日行；空集合不访问数据库。 */
    void addAll(List<ClientAllowedDailyTotal> increments);

    /** 合计 [fromDate, toDate] 内与币种相同的放行金额。 */
    BigDecimal sumAmount(long clientId, VelocityEventCode eventCode, String currency, LocalDate fromDate, LocalDate toDate);

    /** 按币种合计当日与 [fromDate, today] 的放行金额。 */
    List<ClientAllowedCurrencyTotal> totalsByCurrency(long clientId, VelocityEventCode eventCode, LocalDate today,
        LocalDate fromDate);
}
