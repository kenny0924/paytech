package com.paytech.platform.support.filedocument.domain.file;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

import lombok.Getter;
import lombok.Setter;

/** 文件记录列表的一行：文件、当前版本及其上传、首次上传的组合。 */
@Getter
@Setter
public class FileRecordView {

    /** 文件 ID。 */
    private Long fileId;

    /** 当前版本上传时的展示名；非上传来源为空。 */
    private String displayName;

    /** 来源系统。 */
    private String sourceSystem;

    /** 上传原因代码。 */
    private String reasonCode;

    /** 访问级别。 */
    private FileAccessLevel accessLevel;

    /** 当前版本号。 */
    private Integer currentVersionNo;

    /** 检查中的替换版本号；没有为空。 */
    private Integer pendingVersionNo;

    /** 当前版本的扫描状态。 */
    private FileScanStatus scanStatus;

    /** 对象状态。 */
    private FileObjectStatus objectStatus;

    /** 当前版本的媒体类型。 */
    private String mimeType;

    /** 当前版本的字节数。 */
    private Long sizeBytes;

    /** 文件登记时间。 */
    private Instant createTime;

    /** 文件最后更新时间。 */
    private Instant updateTime;

    /** 展示名可能含个人信息。 */
    @Override
    public String toString() {
        return "FileRecordView[" + fileId + "]";
    }
}
