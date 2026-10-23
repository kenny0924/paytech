package com.paytech.platform.support.api.filestorage.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 一条文件检查结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileScanRecord {

    /** 被检查的版本号。 */
    private int versionNo;

    /** 冻结的检查策略代码。 */
    private String scanPolicyCode;

    /** 冻结的检查策略修订号。 */
    private int revisionNo;

    /** 检查器代码。 */
    private String scannerCode;

    /** 检查类型。 */
    private String scanType;

    /** 结果代码，例如 FORMAT_VALID。 */
    private String resultCode;

    /** 尝试序号。 */
    private int attemptNo;

    /** 检查完成时间（UTC）。 */
    private Instant scannedTime;
}
