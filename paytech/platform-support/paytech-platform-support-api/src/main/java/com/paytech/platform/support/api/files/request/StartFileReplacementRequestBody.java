package com.paytech.platform.support.api.files.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 开始替换一个已有文件的内容；访问级别、上传原因、存储位置与对象名沿用原文件，调用方不能指定。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StartFileReplacementRequestBody {

    /**
     * 新内容的展示文件名
     * <li>最多 255 个 Unicode 字符；服务器清除路径与控制字符后不能为空、"." 或 ".."。</li>
     */
    @NotBlank
    private String filename;

    /**
     * 新内容声明的规范 MIME
     * <li>小写 type/subtype，必须在原文件扫描策略当前 ACTIVE 修订的允许名单内；实际字节在完成时独立识别。</li>
     */
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*")
    private String mimeType;

    /**
     * 新内容声明字节数
     * <li>无前导零的正整数十进制字符串，不得超过扫描策略上限。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String sizeBytes;

    /**
     * 上传人账号 ID
     * <li>可空；调用方有人工操作人时传入，Platform 不核对真实性。无前导零的正整数十进制字符串。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String uploaderAccountId;

    /**
     * 访问级别：不允许传入
     * <li>替换不能改变访问级别，沿用原文件的值；传入任何值返回 422。</li>
     */
    @Null(message = "替换不能修改访问级别")
    private String accessLevel;

    /**
     * 上传原因代码：不允许传入
     * <li>替换沿用原文件的上传原因；传入任何值返回 422。</li>
     */
    @Null(message = "替换不能修改上传原因")
    private String reasonCode;

    /** 不记录可能包含个人信息的文件名。 */
    @Override
    public String toString() {
        return "StartFileReplacementRequestBody[REDACTED]";
    }
}
