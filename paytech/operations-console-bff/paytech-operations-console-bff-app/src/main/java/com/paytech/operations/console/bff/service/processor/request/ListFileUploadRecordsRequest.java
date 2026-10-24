package com.paytech.operations.console.bff.service.processor.request;

import java.time.Instant;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 文件上传页「最近上传」的内部请求；时间范围缺省为截至请求时间的近 7 天。 */
@Getter
@AllArgsConstructor
public class ListFileUploadRecordsRequest {

    /** 入站边界已校验的操作人身份 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /** 登记时间起点（含）；与 createdTo 同时缺省或同时提供 */
    private final Instant createdFrom;

    /** 登记时间终点（不含）；与 createdFrom 同时缺省或同时提供 */
    private final Instant createdTo;

    /** true 只看当前会话账号的上传；缺省为 false */
    private final Boolean mine;

    /** 页码；为空默认 1 */
    private final Integer page;

    /** 每页条数；为空默认 10，只允许 10、20、50 */
    private final Integer size;

    /** 请求时间，用于计算缺省时间范围 */
    @NotNull(message = "请求时间不能为空")
    private final Instant requestTime;
}
