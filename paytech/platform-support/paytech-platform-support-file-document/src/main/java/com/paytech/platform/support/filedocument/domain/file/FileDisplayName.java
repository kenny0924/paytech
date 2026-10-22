package com.paytech.platform.support.filedocument.domain.file;

/** 上传展示名的清理规则：入驻上传与内部文件接口共用。 */
public final class FileDisplayName {

    /** 原始文件名的最大 Unicode 字符数。 */
    public static final int MAX_CODE_POINTS = 255;

    private FileDisplayName() {
    }

    /**
     * 归一化展示名
     * <li>反斜杠视为路径分隔符，只保留最后一段；清除控制与格式字符后去除首尾空白。</li>
     *
     * @param filename 调用方提交的原始文件名
     * @return 清理后的展示名，可能为空串
     */
    public static String normalize(String filename) {
        String name = filename.replace('\\', '/');
        return name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cc}\\p{Cf}]", "").strip();
    }

    /** 原始文件名长度不超过上限。 */
    public static boolean withinLimit(String filename) {
        return filename.codePointCount(0, filename.length()) <= MAX_CODE_POINTS;
    }

    /** 清理后的展示名可用：非空且不是 "." 或 ".."。 */
    public static boolean usable(String displayName) {
        return !displayName.isBlank() && !".".equals(displayName) && !"..".equals(displayName);
    }
}
