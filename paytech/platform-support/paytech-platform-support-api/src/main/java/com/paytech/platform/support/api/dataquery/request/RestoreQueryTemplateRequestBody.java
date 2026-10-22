package com.paytech.platform.support.api.dataquery.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 以某个历史版本的内容生成新版本；按当前规则重新校验，记录 restoredFromVersionNo。 */
@Getter
@Setter
public class RestoreQueryTemplateRequestBody {

    /** 被恢复的历史版本号，正整数。 */
    @NotNull
    @Positive
    private Integer versionNo;

    /**
     * 调用方读取的模板原版本
     * <li>正整数；版本不符返回 409。</li>
     */
    @NotNull
    @Positive
    private Integer expectedVersion;

    /** 变更说明，可选，最多 512 字符；缺省为“恢复自版本 N”。 */
    @Size(max = 512)
    private String changeNote;
}
