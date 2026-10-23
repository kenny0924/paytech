package com.paytech.platform.support.localization.domain;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 翻译工作台的一条：key、所属命名空间与目标语言翻译的组合查询结果。 */
@Getter
@Setter
public class TranslationEntryView {

    /** key ID。 */
    private Long keyId;

    /** 命名空间编码。 */
    private String namespaceCode;

    /** 完整 key。 */
    private String keyCode;

    /** 英文基准。 */
    private String sourceText;

    /** 开发者说明，可为 null。 */
    private String description;

    /** 英文基准修订号。 */
    private Integer sourceRevision;

    /** 翻译 ID；未翻译时为 null。 */
    private Long translationId;

    /** 翻译文案；未翻译时为 null。 */
    private String text;

    /** 翻译依据的基准修订号；未翻译时为 null。 */
    private Integer basedOnSourceRevision;

    /** 翻译版本；未翻译时为 null。 */
    private Integer translationVersion;

    /** 翻译最后修改人；未翻译时为 null。 */
    private Long updatedBy;

    /** 翻译最后修改时间；未翻译时为 null。 */
    private Instant updateTime;

    /** 是否基准已变更：有翻译且依据的修订号小于当前修订号。 */
    public boolean sourceChanged() {
        return basedOnSourceRevision != null && basedOnSourceRevision < sourceRevision;
    }
}
