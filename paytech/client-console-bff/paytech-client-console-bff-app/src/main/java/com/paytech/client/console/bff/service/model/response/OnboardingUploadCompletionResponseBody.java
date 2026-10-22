package com.paytech.client.console.bff.service.model.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 完成上传的回执；重复完成返回同一组值，不包含当前扫描状态。 */
@Getter
@AllArgsConstructor
public class OnboardingUploadCompletionResponseBody {

    /**
     * 完成操作身份
     * <li>等于 uploadId（一次上传只产生一个文件）；保留字段以兼容既有页面合同。</li>
     */
    private final String commandId;

    /** 原上传 ID，十进制字符串 */
    private final String uploadId;

    /** 该上传唯一产生的文件 ID，十进制字符串 */
    private final String fileId;

    /**
     * 文件地址 /files/v1/{fileId}
     * <li>关联材料（C12）时原样提交。</li>
     */
    private final String fileAddress;

    /** 文件登记时间（UTC） */
    private final Instant completedAt;
}
