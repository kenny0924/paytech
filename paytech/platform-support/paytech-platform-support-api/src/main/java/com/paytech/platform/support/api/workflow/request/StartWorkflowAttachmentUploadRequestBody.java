package com.paytech.platform.support.api.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 开始一次工作流附件上传
 * <li>来源系统固定为 platform-support、上传原因固定为 WORKFLOW_ATTACHMENT、上传人为当前操作人，调用方不能指定；
 * 存储目标与扫描策略由文件写入路由决定。</li>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StartWorkflowAttachmentUploadRequestBody {

    /**
     * 文件名
     * <li>最多 255 个 Unicode 字符；服务器清除路径与控制字符后作为展示名，不能为空、"." 或 ".."。</li>
     */
    @NotBlank
    @Size(max = 255)
    private String filename;

    /**
     * 声明的规范 MIME
     * <li>小写 type/subtype，须在写入路由扫描策略的允许名单内；实际字节在完成时独立识别，提交时按字段或动作规则再校验。</li>
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

    /** 不记录可能包含个人信息的文件名。 */
    @Override
    public String toString() {
        return "StartWorkflowAttachmentUploadRequestBody[REDACTED]";
    }
}
