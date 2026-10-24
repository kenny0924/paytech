package com.paytech.platform.support.api.files.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 调用方在某一访问级别开始上传时生效的写入限制；与开始上传使用同一份路由解析与扫描策略修订
 * <li>configured=false 时只返回 accessLevel，其余字段为空：没有可用路由、桶或实例，或扫描策略没有唯一的有效修订。</li>
 * <li>不含存储凭据。</li>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadConstraintsResponseBody {

    /** 是否能开始上传。 */
    private boolean configured;

    /** 访问级别：PRIVATE 或 PUBLIC，回显请求（缺省为 PRIVATE）。 */
    private String accessLevel;

    /**
     * 命中的写入路由的来源系统
     * <li>调用方自己的服务名表示精确路由；{@code *} 表示该级别的默认路由。</li>
     */
    private String routeSourceSystem;

    /** 目标存储实例代码。 */
    private String instanceCode;

    /** 目标存储桶名称。 */
    private String bucketName;

    /**
     * 公开桶的对外基础地址
     * <li>私有桶或公开桶未登记基础地址时为空。</li>
     */
    private String publicBaseUrl;

    /** 扫描策略代码。 */
    private String scanPolicyCode;

    /** 扫描策略当前生效的修订号。 */
    private Integer scanPolicyRevisionNo;

    /**
     * 允许声明的媒体类型
     * <li>按策略登记的顺序；开始上传时声明的 mimeType 必须在其中。</li>
     */
    private List<String> allowedMimeTypes;

    /** 单个文件的最大字节数，十进制字符串。 */
    private String maxSizeBytes;

    /** 上传授权的有效秒数。 */
    private Integer maxUploadSeconds;
}
