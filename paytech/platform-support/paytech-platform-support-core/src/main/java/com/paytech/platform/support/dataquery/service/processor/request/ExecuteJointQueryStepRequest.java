package com.paytech.platform.support.dataquery.service.processor.request;

import java.util.List;

import com.paytech.platform.support.dataquery.domain.JointQueryKey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 执行联合查询一步的内部请求；Body 字段约束已由入口校验。 */
@Getter
@AllArgsConstructor
public class ExecuteJointQueryStepRequest {

    /** 调用方服务名（X-Caller-Service，携带内部 Token 时为 Token sub）。 */
    private final String callerService;

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 层号。 */
    @PositiveOrZero
    private final int depth;

    /** 本层键值。 */
    @NotEmpty
    private final List<JointQueryKey> keys;

    /** 只执行这些模板；为 null 时执行本层全部参与模板。 */
    private final List<String> templateCodes;

    /** 已加载对象；为 null 表示没有。 */
    private final List<JointQueryKey> loadedSubjects;
}
