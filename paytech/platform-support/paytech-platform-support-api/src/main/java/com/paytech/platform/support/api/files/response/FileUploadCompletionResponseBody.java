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

    /**
     * 文件地址 {@code /files/v1/{fileId}}
     * <li>业务数据保存此地址；格式与解析见 {@code FileAddress}。</li>
     */
    private String fileAddress;

    /** 文件登记时间（UTC）。 */
    private Instant completedAt;

    /**
     * 本次上传产生的版本号
     * <li>首次上传为 1；替换上传为该文件的新版本号，检查通过前不对外提供。</li>
     * <li>Platform 总是返回；声明为包装类型，便于先升级的消费方兼容尚未升级的 Platform。</li>
     */
    private Integer versionNo;

    /**
     * 访问级别
     * <li>PRIVATE 或 PUBLIC；替换沿用原文件的级别。</li>
     */
    private String accessLevel;
}
