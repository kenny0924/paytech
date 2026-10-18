package com.paytech.platform.support.api.onboardingfiles.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 完成上传的回执；首次与重复完成返回同一组值，不包含当前扫描状态。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UploadCompletionResponseBody {

    /**
     * 完成操作身份
     * <li>等于 uploadId（一次上传只产生一个文件）；保留字段以兼容既有合同。</li>
     */
    private String commandId;

    /** 原上传 ID，十进制字符串。 */
    private String uploadId;

    /** 该上传唯一产生的文件 ID，十进制字符串。 */
    private String fileId;

    /** 首次成功完成的时间（UTC）。 */
    private Instant completedAt;
}
