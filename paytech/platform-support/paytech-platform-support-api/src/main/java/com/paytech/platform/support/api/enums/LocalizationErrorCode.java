package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 多语言管理中调用方需要区别处理的失败原因（技术方案 3.8）。
 * <p>
 * 消费者：operations-console-bff 原样转发，operations-console-web 按 Code 处理：覆盖率不足展示缺口清单，导入预览过期提示重新预览，
 * 文案已变提示重新发布，导入文件未检查完提示稍后重试，命名空间不存在列出缺失项并提供创建入口，ICU 文案不合法按 errors 逐条定位到输入框并显示带字符位置的原因。其余失败使用公共 {@code BUSINESS_CHECK_FAILED}、
 * {@code INVALID_REQUEST}、{@code VALIDATION_ERROR}、{@code CONCURRENT_REQUEST}。
 */
public enum LocalizationErrorCode implements CodeMessageEnum {

    /** 必须覆盖语言的翻译覆盖率低于应用阈值，HTTP 422；Problem errors 列出缺口（最多 200 条）与总数。 */
    I18N_COVERAGE_INSUFFICIENT("I18N_COVERAGE_INSUFFICIENT", "翻译覆盖率未达到发布要求"),

    /** 预览之后应用的 key 集合已被其他导入修改，HTTP 409。 */
    I18N_IMPORT_PREVIEW_STALE("I18N_IMPORT_PREVIEW_STALE", "导入预览已过期，请重新预览"),

    /** 生成语言包期间文案被修改，HTTP 409；已上传的语言包不会被引用。 */
    I18N_CONTENT_CHANGED("I18N_CONTENT_CHANGED", "发布期间文案已变化，请重新发布"),

    /** 导入文件引用了尚未创建的命名空间，HTTP 422；Problem errors 列出缺失的命名空间。 */
    I18N_NAMESPACE_NOT_FOUND("I18N_NAMESPACE_NOT_FOUND", "导入文件包含尚未创建的命名空间"),

    /**
     * 翻译未通过 ICU MessageFormat 校验（语法、参数与英文基准一致、选择类参数须有 other），HTTP 422
     * <li>批量保存与翻译导入使用；Problem errors 的 field 为请求字段路径（translations[i].text 或 namespaces.{命名空间}.{key}），
     * message 为带字符位置的中文原因，最多 200 条。</li>
     */
    I18N_MESSAGE_INVALID("I18N_MESSAGE_INVALID", "翻译文案未通过 ICU 校验"),

    /**
     * 导入文件仍在检查（扫描状态为 PENDING_SCAN 或 SCANNING），HTTP 409
     * <li>key 导入预览、确认与翻译导入使用；前端提示稍后重试。检查被拒绝或隔离的文件仍返回 400 BUSINESS_CHECK_FAILED。</li>
     */
    I18N_IMPORT_FILE_NOT_READY("I18N_IMPORT_FILE_NOT_READY", "导入文件仍在检查，请稍后重试");

    private final String code;

    private final String message;

    /** 绑定稳定代码与安全提示。 */
    LocalizationErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 返回稳定代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 返回安全文案。 */
    @Override
    public String getMessage() {
        return message;
    }
}
