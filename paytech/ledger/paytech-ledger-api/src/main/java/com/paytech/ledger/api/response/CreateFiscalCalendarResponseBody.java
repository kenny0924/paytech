package com.paytech.ledger.api.response;

import lombok.Getter;
import lombok.Setter;

/** 新建财务日历结果。 */
@Getter
@Setter
public class CreateFiscalCalendarResponseBody {
    /**
     * 新建财务日历主键
     */
    private String fiscalCalendarId;
}
