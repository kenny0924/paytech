package com.paytech.platform.support.filedocument.domain.file;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 文件记录列表的筛选条件；创建时间范围左闭右开且必填，其他条件为空不限制。 */
@Getter
@AllArgsConstructor
public class FileRecordQuery {

    /** 创建时间起点（含）。 */
    private final Instant createdFrom;

    /** 创建时间终点（不含）。 */
    private final Instant createdTo;

    /** 文件 ID。 */
    private final Long fileId;

    /** 来源系统。 */
    private final String sourceSystem;

    /** 上传原因代码。 */
    private final String reasonCode;

    /** 访问级别。 */
    private final FileAccessLevel accessLevel;

    /** 当前版本扫描状态。 */
    private final FileScanStatus scanStatus;

    /** true 只看有检查中替换版本的文件，false 只看没有的。 */
    private final Boolean hasPendingReplacement;

    /** 首次上传的上传人账号。 */
    private final Long uploaderAccountId;
}
