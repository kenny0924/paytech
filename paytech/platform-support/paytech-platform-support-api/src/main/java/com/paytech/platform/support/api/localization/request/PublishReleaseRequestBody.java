package com.paytech.platform.support.api.localization.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 发布当前文案。 */
@Getter
@Setter
public class PublishReleaseRequestBody {

    /** 发布说明，最长 512 字符，可为空。 */
    @Size(max = 512)
    private String releaseNote;
}
