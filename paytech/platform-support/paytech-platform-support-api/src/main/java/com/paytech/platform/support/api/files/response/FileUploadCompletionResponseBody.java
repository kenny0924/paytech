package com.paytech.platform.support.api.files.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 完成上传的回执；重复完成返回同一组值，不包含当前扫描状态。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadCompletionResponseBody {

    /** 上传 ID，十进制字符串。 */
    private String uploadId;

    /** 登记的文件 ID，十进制字符串。 */
    private String fileId;

    /** 文件登记时间（UTC）。 */
    private Instant completedAt;
}
