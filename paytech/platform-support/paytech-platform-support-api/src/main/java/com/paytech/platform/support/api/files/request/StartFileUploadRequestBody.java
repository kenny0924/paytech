package com.paytech.platform.support.api.files.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 开始一次内部上传；来源系统取调用身份，存储目标与扫描策略由写入路由决定，调用方不能指定。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StartFileUploadRequestBody {

    /**
     * 上传原因代码
     * <li>来源系统自定义，格式 [A-Z][A-Z0-9_]{0,63}，例如 WORKFLOW_ATTACHMENT；用于以后反查文件为什么上传。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String reasonCode;

    /**
     * 展示文件名
     * <li>最多 255 个 Unicode 字符；服务器清除路径与控制字符后不能为空、"." 或 ".."。</li>
     */
    @NotBlank
    private String filename;

    /**
     * 声明的规范 MIME
     * <li>小写 type/subtype，必须在路由扫描策略的允许名单内；实际字节在完成时独立识别。</li>
     */
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*")
    private String mimeType;

    /**
     * 声明字节数
     * <li>无前导零的正整数十进制字符串，不得超过扫描策略上限。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String sizeBytes;

    /**
     * 上传人账号 ID
     * <li>可空；调用方有人工操作人时传入，Platform 不核对真实性，原样记录并在查询时返回。无前导零的正整数十进制字符串。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String uploaderAccountId;

    /**
     * 访问级别
     * <li>{@code PRIVATE}（缺省）或 {@code PUBLIC}；PUBLIC 按来源系统的公开写入路由落到公开桶，检查通过后返回长期公开地址。</li>
     * <li>没有对应级别的写入路由返回 503，不回落到另一级别；上传后不可修改。</li>
     */
    @Pattern(regexp = "PRIVATE|PUBLIC", message = "accessLevel 只能为 PRIVATE 或 PUBLIC")
    private String accessLevel;

    /** 不记录可能包含个人信息的文件名。 */
    @Override
    public String toString() {
        return "StartFileUploadRequestBody[REDACTED]";
    }
}
