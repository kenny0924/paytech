package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

/** 仅包含已提交命令当次结果的稳定引用，适配既有 255 字符幂等存储。 */
public record ConfigurationMutation(String objectType, long objectId, String objectCode, String status, int version) {
    public ConfigurationMutation {
        if (objectId <= 0 || version <= 0 || objectType == null || objectCode == null || status == null
            || !objectType.matches("[A-Z_]{1,32}") || !objectCode.matches("[A-Za-z0-9_-]{1,128}")
            || !status.matches("ACTIVE|INACTIVE|DISABLED")) {
            throw new IllegalArgumentException("配置回执身份、状态或版本不合法");
        }
    }

    /** 写入完整身份，不依赖可变目录恢复旧回执。 */
    public String reference() {
        return objectType + "," + objectId + "," + objectCode + "," + status + "," + version;
    }

    /** 读取时核对当前命令的对象，损坏或串用的回执不能返回成功。 */
    public static ConfigurationMutation restore(String reference, String objectType, String objectCode) {
        ConfigurationMutation value = restore(reference);
        if (!value.objectType().equals(objectType) || !value.objectCode().equals(objectCode))
            throw new IllegalStateException("配置命令回执与当前对象不一致");
        return value;
    }

    /** 只用于新配置命令，禁止解释旧状态命令的引用。 */
    public static ConfigurationMutation restore(String reference) {
        String[] parts = reference.split(",", -1);
        if (parts.length != 5) throw new IllegalStateException("配置命令回执损坏");
        try {
            return new ConfigurationMutation(parts[0], Long.parseLong(parts[1]), parts[2], parts[3],
                Integer.parseInt(parts[4]));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("配置命令回执损坏", exception);
        }
    }
}
