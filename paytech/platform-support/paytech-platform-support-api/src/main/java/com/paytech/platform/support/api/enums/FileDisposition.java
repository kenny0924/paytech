package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 文件下载方式，对应下载地址响应头 Content-Disposition 的类型
 * <li>INLINE 只对扫描 AVAILABLE 且登记 MIME 为 application/pdf、image/png、image/jpeg 的文件生效，其他文件静默降为 ATTACHMENT。</li>
 */
public enum FileDisposition implements CodeEnum {
    /** 浏览器内直接打开。 */
    INLINE("INLINE"),

    /** 作为附件下载。 */
    ATTACHMENT("ATTACHMENT");

    private final String code;

    /** 固定发布代码，不依赖 Java 常量名。 */
    FileDisposition(String code) {
        this.code = code;
    }

    /** 返回稳定代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 按代码解析
     *
     * @param code 代码
     * @return 对应方式
     * @throws IllegalArgumentException 未知代码
     */
    public static FileDisposition fromCode(String code) {
        for (FileDisposition value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知文件下载方式");
    }
}
