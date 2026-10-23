package com.paytech.platform.support.service.processor.filestorage.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 新建检查策略、以 ACTIVE 为模板新建修订或按原版本修改草稿
 * <li>新建策略与修改草稿必须提供允许类型与最大字节数；以模板新建修订时两者为空。</li>
 */
@Getter
@AllArgsConstructor
public class SaveScanPolicyRequest {

    /** Gateway 传递的可信操作人账号。 */
    @NotBlank(message = "操作人必填")
    private final String actor;

    /** Gateway 传递的可信会话标识。 */
    @NotBlank(message = "会话必填")
    private final String session;

    /** 策略代码。 */
    @NotBlank(message = "策略代码必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "策略代码必须为大写代码")
    private final String policyCode;

    /** 修改的修订号；新建时为 0。 */
    private final int revisionNo;

    /** 允许的规范 MIME；以模板新建修订时为空。 */
    private final List<String> allowedMimeTypes;

    /** 最大字节数；以模板新建修订时为 0。 */
    private final long maxSizeBytes;

    /** 修改时调用方读取的原版本；新建为 0。 */
    private final int expectedVersion;

    /** 已去除首尾空白的操作原因。 */
    @NotBlank(message = "原因必填")
    private final String reason;
}
