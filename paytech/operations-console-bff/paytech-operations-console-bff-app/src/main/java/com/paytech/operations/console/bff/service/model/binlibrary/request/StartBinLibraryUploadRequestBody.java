package com.paytech.operations.console.bff.service.model.binlibrary.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 开始上传一份 BIN 库 CSV；上传原因、类型与上传人由 BFF 固定，页面不能指定 */
@Getter
@Setter
public class StartBinLibraryUploadRequestBody {
    /**
     * 文件名
     * <li>必须以 .csv 结尾（不区分大小写），最多 255 个字符。</li>
     */
    @NotBlank(message = "文件名必填")
    @Size(max = 255, message = "文件名最多 255 个字符")
    @Pattern(regexp = "(?s).*\\.[cC][sS][vV]", message = "只接受 .csv 文件")
    private String fileName;

    /**
     * 文件字节数
     * <li>无前导零的正整数十进制字符串；上限 50 MiB 由 Platform 扫描策略 CARD_BIN_CSV 判定。</li>
     */
    @NotBlank(message = "文件大小必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "文件大小必须为正整数")
    private String sizeBytes;
}
