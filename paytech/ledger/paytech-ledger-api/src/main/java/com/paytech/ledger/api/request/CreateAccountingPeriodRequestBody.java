package com.paytech.ledger.api.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 在财务日历下创建会计期间请求。 */
@Getter
@Setter
public class CreateAccountingPeriodRequestBody {
    /**
     * 财务年度
     */
    @NotNull
    @Min(1900)
    @Max(9999)
    private Integer fiscalYear;

    /**
     * 年度内期号
     * <li>同一日历同一年度内唯一</li>
     */
    @NotNull
    @Min(1)
    @Max(999)
    private Integer periodNo;

    /**
     * 期间首个业务日期
     * <li>ISO 日期 yyyy-MM-dd；与同日历其他期间不得重叠</li>
     */
    @NotNull
    private LocalDate startDate;

    /**
     * 期间最后一个业务日期
     * <li>不早于开始日期</li>
     */
    @NotNull
    private LocalDate endDate;
}
