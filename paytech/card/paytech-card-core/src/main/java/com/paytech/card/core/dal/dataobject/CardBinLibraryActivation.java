package com.paytech.card.core.dal.dataobject;

import java.time.Instant;

import com.paytech.card.api.enums.BinLibraryActivationType;

import lombok.Getter;
import lombok.Setter;

/** t_card_bin_library_activation 的完整持久化映射：一次激活或回切的追加记录。 */
@Getter
@Setter
public class CardBinLibraryActivation {
    /** 激活记录标识。 */
    private Long activationId;

    /** 本次被激活的版本。 */
    private Long versionId;

    /** 被替换的原生效版本；此前没有生效版本时为 null。 */
    private Long previousVersionId;

    /** 激活类型。 */
    private BinLibraryActivationType activationType;

    /** 操作人 Operations Account ID；种子激活为 null。 */
    private Long operatorAccountId;

    /** 激活时间。 */
    private Instant createTime;

    /** 更新时间，等于激活时间。 */
    private Instant updateTime;
}
