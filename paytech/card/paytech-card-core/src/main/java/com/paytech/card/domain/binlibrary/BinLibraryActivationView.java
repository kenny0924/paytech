package com.paytech.card.domain.binlibrary;

import java.time.Instant;

import com.paytech.card.api.enums.BinLibraryActivationType;

import lombok.Getter;
import lombok.Setter;

/** 激活历史的一行：激活记录关联本次与被替换版本的版本号（激活记录、版本两张表的组合查询）。 */
@Getter
@Setter
public class BinLibraryActivationView {
    /** 激活类型。 */
    private BinLibraryActivationType activationType;

    /** 本次生效的版本号。 */
    private Integer versionNo;

    /** 被替换的原生效版本号；此前没有生效版本时为 null。 */
    private Integer previousVersionNo;

    /** 操作人 Operations Account ID；种子激活为 null。 */
    private Long operatorAccountId;

    /** 激活时间。 */
    private Instant activatedAt;
}
