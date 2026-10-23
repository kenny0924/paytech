package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** 批量保存后一条翻译的新版本。 */
@Getter
@Setter
public class SavedTranslation {

    /** 完整 key。 */
    private String keyCode;

    /** 保存后的翻译版本。 */
    private int version;
}
