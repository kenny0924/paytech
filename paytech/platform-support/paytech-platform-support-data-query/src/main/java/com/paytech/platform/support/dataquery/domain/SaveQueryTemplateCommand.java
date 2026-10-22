package com.paytech.platform.support.dataquery.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本把内容保存为新版本并立即生效；修改与恢复共用。 */
@Getter
@AllArgsConstructor
public final class SaveQueryTemplateCommand {

    /** 模板 ID。 */
    private final long templateId;

    /** 调用方读取的模板原版本。 */
    private final int expectedVersion;

    /** 预先分配的版本行 ID。 */
    private final long templateVersionId;

    /** 模板名称。 */
    private final String templateName;

    /** 说明，可为 null。 */
    private final String description;

    /** 内容，已通过保存校验。 */
    private final QueryTemplateDraft content;

    /** 恢复来源版本号；修改时为 null。 */
    private final Integer restoredFromVersionNo;

    /** 变更说明，可为 null。 */
    private final String changeNote;

    /** 操作人账号 ID。 */
    private final long actorAccountId;

    /** 本次写入时间。 */
    private final Instant now;
}
