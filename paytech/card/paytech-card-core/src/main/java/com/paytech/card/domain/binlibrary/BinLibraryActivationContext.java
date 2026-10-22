package com.paytech.card.domain.binlibrary;

import com.paytech.card.api.enums.BinLibraryActivationType;
import com.paytech.card.core.dal.dataobject.CardBinLibraryActivation;
import com.paytech.card.core.dal.dataobject.CardBinLibraryVersion;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次激活或回切：目标版本、被替换的原生效版本与本次激活记录，在同一本地事务保存。 */
@Getter
@AllArgsConstructor
public class BinLibraryActivationContext {
    /** 目标版本（VALID 或 RETIRED）。 */
    private final CardBinLibraryVersion target;

    /** 原生效版本；此前没有生效版本时为 null。 */
    private final CardBinLibraryVersion previous;

    /** 本次激活记录。 */
    private final CardBinLibraryActivation activation;

    /** 原生效版本号；没有时为 null。 */
    public Integer previousVersionNo() {
        return previous == null ? null : previous.getVersionNo();
    }

    /** 激活类型。 */
    public BinLibraryActivationType activationType() {
        return activation.getActivationType();
    }
}
