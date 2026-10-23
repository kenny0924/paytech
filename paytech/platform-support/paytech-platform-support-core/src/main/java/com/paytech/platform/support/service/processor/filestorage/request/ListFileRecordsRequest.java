package com.paytech.platform.support.service.processor.filestorage.request;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 文件记录分页查询；创建时间范围左闭右开且必填，跨度不超过 31 天。 */
@Getter
@AllArgsConstructor
public class ListFileRecordsRequest {

    /** Gateway 传递的可信操作人账号。 */
    @NotBlank(message = "操作人必填")
    private final String actor;

    /** Gateway 传递的可信会话标识。 */
    @NotBlank(message = "会话必填")
    private final String session;

    /** 创建时间起点（含）。 */
    @NotNull(message = "createdFrom 必填")
    private final Instant createdFrom;

    /** 创建时间终点（不含）。 */
    @NotNull(message = "createdTo 必填")
    private final Instant createdTo;

    /** 文件 ID；为空不限制。 */
    @Positive(message = "文件 ID 必须为正整数")
    private final Long fileId;

    /** 来源系统；为空不限制。 */
    @Pattern(regexp = "\\*|[a-z][a-z0-9-]{0,63}", message = "来源系统必须为服务名")
    private final String sourceSystem;

    /** 上传原因代码；为空不限制。 */
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "上传原因必须为大写代码")
    private final String reasonCode;

    /** 访问级别；为空不限制。 */
    private final FileAccessLevel accessLevel;

    /** 当前版本扫描状态；为空不限制。 */
    private final FileScanStatus scanStatus;

    /** 是否有检查中的替换版本；为空不限制。 */
    private final Boolean hasPendingReplacement;

    /** 页码；为空默认 1。 */
    private final Integer page;

    /** 每页条数；为空默认 10。 */
    private final Integer size;
}
