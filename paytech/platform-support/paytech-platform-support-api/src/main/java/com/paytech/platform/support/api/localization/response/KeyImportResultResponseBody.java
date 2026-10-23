package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** 确认导入的结果。 */
@Getter
@Setter
public class KeyImportResultResponseBody {

    /** 新增 key 数。 */
    private int addedCount;

    /** 基准变更 key 数。 */
    private int sourceChangedCount;

    /** 废弃 key 数。 */
    private int deprecatedCount;

    /** 恢复 key 数。 */
    private int restoredCount;

    /** 只有说明变化的 key 数。 */
    private int descriptionChangedCount;

    /** 导入后的 key 集合修订号。 */
    private int keySetRevision;

    /** 导入后的文案修订号。 */
    private int contentRevision;
}
