package com.paytech.platform.support.referencedata.service.processor.response;

import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCurrency;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 币种规则写入结果：本次保存后的身份、状态与版本。 */
@Getter
@AllArgsConstructor
public class CurrencySpecMutation {

    /** 币种规则 Snowflake 主键。 */
    private final long id;

    /** ISO 4217 代码。 */
    private final String currency;

    /** 保存后的状态。 */
    private final CurrencySpecStatus status;

    /** 保存后的版本；无变化修改为原版本。 */
    private final int version;

    /** 由本次 Context 中已同步的 DataObject 构造。 */
    public static CurrencySpecMutation of(ReferenceCurrency spec) {
        return new CurrencySpecMutation(spec.getId(), spec.getCurrencyCode(), spec.getStatus(), spec.getVersion());
    }
}
