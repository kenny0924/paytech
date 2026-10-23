package com.paytech.platform.support.api.filestorage.request;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 在存储实例下登记 DRAFT 存储桶；Platform 不建桶、不改桶策略。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterStorageBucketRequestBody {

    /**
     * 存储桶名称
     * <li>S3 命名规则：3—63 位小写字母、数字、点与连字符，首尾为字母或数字；创建后不可修改。</li>
     */
    @NotBlank
    @Pattern(regexp = "[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")
    private String bucketName;

    /**
     * 访问级别
     * <li>PRIVATE 或 PUBLIC；登记后不可修改。检查时私有桶必须拒绝匿名读取，公开桶必须能经基础地址匿名读取。</li>
     */
    @NotBlank
    @Pattern(regexp = "PRIVATE|PUBLIC", message = "accessLevel 只能为 PRIVATE 或 PUBLIC")
    private String accessLevel;

    /**
     * 对外基础地址
     * <li>PUBLIC 必填：https:// 开头，不带查询串、片段或用户信息，不以 / 结尾；PRIVATE 必须为空。</li>
     * <li>只在桶为 DRAFT 时可修改。</li>
     */
    @Size(max = 255)
    private String publicBaseUrl;

    /**
     * 操作原因
     * <li>必填，最多 500 字符；保存时去除首尾空白，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
