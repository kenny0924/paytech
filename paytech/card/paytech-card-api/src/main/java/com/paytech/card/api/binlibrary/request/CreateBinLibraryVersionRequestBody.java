package com.paytech.card.api.binlibrary.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 用已上传文件创建 BIN 库版本请求 */
@Getter
@Setter
public class CreateBinLibraryVersionRequestBody {
    /**
     * Platform 文件服务的稳定文件地址
     * <li>格式 /files/v1/{fileId}；文件须由 operations-console-bff 以上传原因 CARD_BIN_LIBRARY_IMPORT 上传、识别为 text/csv。</li>
     * <li>同一文件只建一个版本，重复提交返回原版本。</li>
     * <li>轻量合同不依赖 Platform API，这里只做格式校验；card 入口再以 Platform FileAddress 解析，解析失败返回 400。</li>
     */
    @NotBlank(message = "文件地址必填")
    @Pattern(regexp = "/files/v1/[1-9][0-9]{0,18}", message = "文件地址格式应为 /files/v1/{fileId}")
    private String fileAddress;
}
