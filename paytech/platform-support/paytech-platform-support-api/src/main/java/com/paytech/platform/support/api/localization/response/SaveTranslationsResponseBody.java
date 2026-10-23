package com.paytech.platform.support.api.localization.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 批量保存的结果。 */
@Getter
@Setter
public class SaveTranslationsResponseBody {

    /** 按请求顺序的保存结果。 */
    private List<SavedTranslation> translations;

    /** 保存后的文案修订号。 */
    private int contentRevision;
}
