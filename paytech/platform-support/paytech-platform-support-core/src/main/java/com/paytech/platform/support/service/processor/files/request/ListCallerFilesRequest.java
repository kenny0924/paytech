package com.paytech.platform.support.service.processor.files.request;

import java.time.Instant;

import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 分页查询调用方自己来源的文件；来源系统取内部调用身份，调用方不能指定
 * <li>登记时间范围左闭右开且必填，跨度不超过 31 天；其他条件为空不限制。</li>
 */
@Getter
@AllArgsConstructor
public class ListCallerFilesRequest {

    /** 来源系统，即内部调用身份的服务名。 */
    @NotNull(message = "来源系统必填")
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}", message = "来源系统必须为服务名")
    private final String sourceSystem;

    /** 登记时间起点（含）。 */
    @NotNull(message = "createdFrom 必填")
    private final Instant createdFrom;

    /** 登记时间终点（不含）。 */
    @NotNull(message = "createdTo 必填")
    private final Instant createdTo;

    /** 上传原因代码；为空不限制。 */
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "上传原因必须为大写代码")
    private final String reasonCode;

    /** 访问级别；为空不限制。 */
    private final FileAccessLevel accessLevel;

    /** 首次上传的上传人账号；为空不限制。 */
    @Positive(message = "上传人账号 ID 必须为正整数")
    private final Long uploaderAccountId;

    /** 页码；为空默认 1。 */
    private final Integer page;

    /** 每页条数；为空默认 10，只允许 10、20、50。 */
    private final Integer size;
}
