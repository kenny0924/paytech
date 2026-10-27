package com.paytech.platform.support.referencedata.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCurrency;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 修改币种的本地保存上下文。 */
@Getter
@AllArgsConstructor
public class UpdateCurrencySpecContext {

    /** 已锁定并核对版本的币种。 */
    private final ReferenceCurrency currency;

    /** 目标属性。 */
    private final CurrencyAttributes attributes;

    /** 本次 UTC 微秒时间。 */
    private final Instant updateTime;

    /** 全部属性都未变化时不写库、不递增版本。 */
    public boolean unchanged() {
        return attributes.sameAs(CurrencyAttributes.of(currency));
    }

    /**
     * 启用过（非 DRAFT）的币种被要求修改的冻结字段
     *
     * @return 请求字段名；DRAFT 或没有修改标准属性时为空
     */
    public List<String> frozenFieldChanges() {
        if (currency.getStatus() == CurrencySpecStatus.DRAFT) {
            return List.of();
        }
        return attributes.changedStandardFields(CurrencyAttributes.of(currency));
    }

    /** logo 改为另一个非空地址时需要核对文件。 */
    public boolean logoReplaced() {
        return attributes.getLogoFileAddress() != null
            && !attributes.getLogoFileAddress().equals(currency.getLogoFileAddress());
    }
}
