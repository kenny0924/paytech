package com.paytech.platform.support.dataquery.service.processor.request;

import java.util.List;

import com.paytech.platform.support.dataquery.domain.JointQueryKey;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模块内调用方执行联合查询一步的请求；不含会话与 HTTP 调用方，访问控制由调用方负责。 */
@Getter
@AllArgsConstructor
public class RunJointQueryStepRequest {

    /** 层号，从 0 开始；上限由服务端按 maxDepth 校验。 */
    @Min(0)
    private final int depth;

    /** 本层键值，原样来自调用方；校验与规范化在 Processor 内完成。 */
    @NotEmpty
    private final List<JointQueryKey> keys;

    /** 只执行这些模板；为 null 时执行本层全部参与模板，不参与或不存在的编码忽略。 */
    private final List<String> templateCodes;

    /** 已加载对象，原样来自调用方；为 null 表示没有。校验与规范化在 Processor 内完成。 */
    private final List<JointQueryKey> loadedSubjects;

    /** 操作人账号 ID，注入为 __operator.accountId；可为 null。 */
    private final Long operatorAccountId;
}
