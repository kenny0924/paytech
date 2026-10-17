package com.paytech.risk.control.domain.list;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.regex.Pattern;

import com.paytech.foundation.common.error.InvalidParameterException;

/**
 * 主体键规范化与脱敏展示；名单成员与累计事件主体使用同一规则，保证二者可以相互命中。
 * <li>IP 不做 DNS 解析：IPv4 保留原值，IPv6 取 /64 网段前缀。</li>
 * <li>消费者引用只在 Client 命名空间内唯一，键为 clientId:consumerReference。</li>
 */
public final class RiskSubjectKeys {
    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern CONSUMER = Pattern.compile("[1-9][0-9]{0,18}:[\\x21-\\x7E]{1,128}");
    private static final Pattern ENTITY_NO = Pattern.compile("[A-Z0-9]{5}");
    private static final Pattern FINGERPRINT = Pattern.compile("[A-Za-z0-9_\\-+/=]{8,128}");
    private static final Pattern CARD_BIN = Pattern.compile("[0-9]{6,8}");
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    /** 纯函数工具。 */
    private RiskSubjectKeys() { }

    /**
     * 规范化主体键
     *
     * @param type 主体类型
     * @param raw 原值；CONSUMER 为 clientId:consumerReference
     * @return 规范化主体键
     *         <li>格式不合法或一期不支持的主体类型抛出参数错误，消息不回显原值。</li>
     */
    public static String normalize(RiskSubjectType type, String raw) {
        if (type == null || raw == null) {
            throw new InvalidParameterException("主体类型和主体取值必填");
        }
        return switch (type) {
            case CLIENT -> require(POSITIVE_ID, raw, type);
            case CONSUMER -> require(CONSUMER, raw, type);
            case OPERATING_ENTITY -> require(ENTITY_NO, raw, type);
            case CARD_FINGERPRINT -> require(FINGERPRINT, raw, type);
            case CARD_BIN -> require(CARD_BIN, raw, type);
            case IP_ADDRESS -> ipKey(raw);
            case EMAIL_TOKEN, PHONE_TOKEN -> throw new InvalidParameterException("一期不支持的主体类型: " + type.getCode());
        };
    }

    /** 组合消费者主体原值。 */
    public static String consumer(String clientId, String consumerReference) {
        return clientId + ":" + consumerReference;
    }

    /**
     * 受限主体键的展示形式
     *
     * @param type 主体类型
     * @param key 规范化主体键
     * @return IP 只保留网段前两段，卡指纹与消费者引用只保留前 6 位，其他原样返回
     */
    public static String mask(RiskSubjectType type, String key) {
        return switch (type) {
            case IP_ADDRESS -> maskIp(key);
            case CARD_FINGERPRINT, CONSUMER -> key.length() <= 6 ? "******" : key.substring(0, 6) + "******";
            default -> key;
        };
    }

    /** 按格式校验并原样返回。 */
    private static String require(Pattern pattern, String raw, RiskSubjectType type) {
        if (!pattern.matcher(raw).matches()) {
            throw new InvalidParameterException("主体取值格式无效: " + type.getCode());
        }
        return raw;
    }

    /** 解析 IP 字面量，不触发域名解析。 */
    private static String ipKey(String raw) {
        if (!IP_LITERAL.matcher(raw).matches()) {
            throw new InvalidParameterException("主体取值格式无效: IP_ADDRESS");
        }
        InetAddress address;
        try {
            address = InetAddress.ofLiteral(raw);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidParameterException("主体取值格式无效: IP_ADDRESS");
        }
        if (address instanceof Inet4Address) {
            return address.getHostAddress();
        }
        return ipv6Prefix((Inet6Address)address);
    }

    /** IPv6 取前 64 位，以固定小写十六进制分组表示。 */
    private static String ipv6Prefix(Inet6Address address) {
        byte[] bytes = address.getAddress();
        StringBuilder prefix = new StringBuilder();
        for (int index = 0; index < 8; index += 2) {
            prefix.append(String.format("%x", ((bytes[index] & 0xff) << 8) | (bytes[index + 1] & 0xff))).append(':');
        }
        return prefix.append(":/64").toString();
    }

    /** IPv4 保留前两段，IPv6 保留前两组。 */
    private static String maskIp(String key) {
        String separator = key.contains(":") ? ":" : ".";
        String[] parts = key.split(Pattern.quote(separator));
        return parts.length < 2 ? "******" : parts[0] + separator + parts[1] + separator + "*";
    }
}
