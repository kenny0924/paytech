package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 存储桶启用检查结果（t_file_storage_bucket.last_check_result）。HTTP 字段使用 String。 */
public enum FileStorageCheckResult implements CodeEnum {
    /** 存在、版本控制已开启、默认加密已开启，探测对象写入、读取、匿名访问核对、删除均成功。 */
    OK("OK"),

    /** 存储桶不存在。 */
    NOT_FOUND("NOT_FOUND"),

    /** 未开启版本控制。 */
    VERSIONING_DISABLED("VERSIONING_DISABLED"),

    /** 未开启默认服务端加密。 */
    ENCRYPTION_DISABLED("ENCRYPTION_DISABLED"),

    /** 凭据缺少所需权限。 */
    ACCESS_DENIED("ACCESS_DENIED"),

    /** 存储不可连接或返回无法识别的结果。 */
    UNAVAILABLE("UNAVAILABLE"),

    /** 私有桶的探测对象可被不带凭据读取（桶允许匿名访问）。 */
    PUBLIC_ACCESS_ALLOWED("PUBLIC_ACCESS_ALLOWED"),

    /** 公开桶的探测对象经对外基础地址匿名读取失败或字节不一致（公开地址不可访问）。 */
    PUBLIC_READ_FAILED("PUBLIC_READ_FAILED");

    private final String code;

    FileStorageCheckResult(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }
}
