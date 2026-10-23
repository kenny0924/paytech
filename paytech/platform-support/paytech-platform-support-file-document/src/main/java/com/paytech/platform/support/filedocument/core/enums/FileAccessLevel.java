package com.paytech.platform.support.filedocument.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 文件、上传、存储桶与写入路由的访问级别；Wire 使用 String Code。 */
public enum FileAccessLevel implements CodeEnum {

    /** 私有：只经业务授权签发的短时地址下载。 */
    PRIVATE("PRIVATE"),

    /** 公开：位于公开桶，检查通过后经对外基础地址长期可读。 */
    PUBLIC("PUBLIC");

    private final String code;

    FileAccessLevel(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 按 Code 解析；空值按 PRIVATE
     *
     * @param code 调用方声明的访问级别，可空
     * @return 访问级别
     * @throws IllegalArgumentException 未知 Code
     */
    public static FileAccessLevel fromNullableCode(String code) {
        if (code == null || code.isEmpty()) {
            return PRIVATE;
        }
        for (FileAccessLevel level : values()) {
            if (level.code.equals(code)) {
                return level;
            }
        }
        throw new IllegalArgumentException("unknown access level");
    }
}
