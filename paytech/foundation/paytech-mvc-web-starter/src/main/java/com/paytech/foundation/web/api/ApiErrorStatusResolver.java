package com.paytech.foundation.web.api;

import org.springframework.http.HttpStatus;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 为调用方明确需要区分的稳定错误码提供 HTTP 状态，统一错误体仍由共享异常处理器创建。
 *
 * @author Codex
 */
@FunctionalInterface
public interface ApiErrorStatusResolver {

    /**
     * 解析错误状态；明确失败只接受 4xx，系统错误只接受 5xx，其他返回值回退默认状态。
     * 公共并发冲突的 409、依赖响应异常的 502、服务不可用的 503、框架参数校验和未知异常不经过该扩展。
     *
     * @param errorCode 稳定错误码
     * @param defaultStatus 共享异常处理器的默认错误状态
     * @return 对应错误状态；未定义时返回默认状态
     */
    HttpStatus resolve(CodeMessageEnum errorCode, HttpStatus defaultStatus);
}
