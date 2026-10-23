package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** 翻译导入的结果。 */
@Getter
@Setter
public class ImportTranslationsResponseBody {

    /** 新建或修改的翻译数。 */
    private int updatedCount;

    /** 与现有翻译相同、未写入的条数。 */
    private int unchangedCount;

    /** 值为空字符串、按约定不改动的条数。 */
    private int emptyCount;

    /** 导入后的文案修订号；没有写入时不变。 */
    private int contentRevision;
}
