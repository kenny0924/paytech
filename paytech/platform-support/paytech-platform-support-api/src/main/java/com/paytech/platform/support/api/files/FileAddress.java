package com.paytech.platform.support.api.files;

import java.util.regex.Pattern;

/**
 * 文件服务签发的稳定逻辑地址 {@code /files/v1/{fileId}}
 * <li>业务数据保存地址而不是文件 ID；地址不含存储实例、桶或对象坐标，存储迁移后不变，也不是可直接访问的下载链接。</li>
 * <li>只能经内部文件接口查询元数据或换取临时下载地址；调用方用本类格式化与解析，不自行拼接。</li>
 * <li>纯 Java 不可变值类型，不依赖 Spring。</li>
 */
public final class FileAddress {

    /** 地址前缀。 */
    private static final String PREFIX = "/files/v1/";

    /** 合法地址：前缀加无前导零的正整数文件 ID。 */
    private static final Pattern FORMAT = Pattern.compile("^/files/v1/[1-9][0-9]{0,18}$");

    /** 文件 ID。 */
    private final long fileId;

    /** 只经工厂方法创建。 */
    private FileAddress(long fileId) {
        this.fileId = fileId;
    }

    /**
     * 由文件 ID 构造地址
     *
     * @param fileId 文件 ID，正整数
     * @return 地址
     * @throws IllegalArgumentException 文件 ID 不是正整数
     */
    public static FileAddress of(long fileId) {
        if (fileId <= 0) {
            throw new IllegalArgumentException("文件 ID 必须为正整数: " + fileId);
        }
        return new FileAddress(fileId);
    }

    /**
     * 解析地址
     * <li>只接受 {@code /files/v1/} 加无前导零的正整数文件 ID，不容忍空白、大小写变化、查询串或尾部斜杠。</li>
     *
     * @param value 地址文本
     * @return 地址
     * @throws IllegalArgumentException 格式不符或文件 ID 超出 long 范围
     */
    public static FileAddress parse(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("文件地址格式不合法");
        }
        try {
            return new FileAddress(Long.parseLong(value.substring(PREFIX.length())));
        } catch (NumberFormatException overflow) {
            throw new IllegalArgumentException("文件地址中的文件 ID 超出范围", overflow);
        }
    }

    /** 文件 ID。 */
    public long fileId() {
        return fileId;
    }

    /** 地址文本 {@code /files/v1/{fileId}}。 */
    public String value() {
        return PREFIX + fileId;
    }

    /** 按文件 ID 比较。 */
    @Override
    public boolean equals(Object other) {
        return other instanceof FileAddress address && address.fileId == fileId;
    }

    /** 与 equals 一致。 */
    @Override
    public int hashCode() {
        return Long.hashCode(fileId);
    }

    /** 地址文本。 */
    @Override
    public String toString() {
        return value();
    }
}
