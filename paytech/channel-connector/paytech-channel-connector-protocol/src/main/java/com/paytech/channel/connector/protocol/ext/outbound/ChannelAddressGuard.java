package com.paytech.channel.connector.protocol.ext.outbound;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.protocol.core.configuration.ExternalApiExecutionProperties;

/**
 * 渠道出站地址的 SSRF 防护（参照 Platform WebhookAddressGuard 的网段规则，不依赖 Platform）
 * <li>拦截：0/8、10/8、100.64/10、127/8、169.254/16、172.16/12、192.0.0/24、192.168/16、198.18/15、224/4、240/4，
 * IPv6 的未指定、回环、fc00::/7、fe80::/10、ff00::/8；IPv4 映射地址按 IPv4 判断；部署配置的 trusted-networks 网段优先放行。</li>
 * <li>保存出站地址时只判断 IP 字面量与 localhost（含 *.localhost，一律拦截，放行网段只按 IP 匹配），不做 DNS 解析；
 * 执行时解析主机名，全部地址都允许才放行。</li>
 * <li>执行时的解析结果进入 JVM 的 InetAddress 正向缓存，JDK HttpClient 随后连接同一主机名时取用该缓存，因此连接使用的地址来自本次校验的集合；
 * 缓存在两次查找之间恰好过期属于已知残余窗口，由网络层出口控制另行收敛。</li>
 */
@Component
public class ChannelAddressGuard {

    private static final List<Cidr> BLOCKED = List.of(cidr("0.0.0.0/8"), cidr("10.0.0.0/8"),
        cidr("100.64.0.0/10"), cidr("127.0.0.0/8"), cidr("169.254.0.0/16"), cidr("172.16.0.0/12"),
        cidr("192.0.0.0/24"), cidr("192.168.0.0/16"), cidr("198.18.0.0/15"), cidr("224.0.0.0/4"),
        cidr("240.0.0.0/4"), cidr("::/128"), cidr("::1/128"), cidr("fc00::/7"), cidr("fe80::/10"),
        cidr("ff00::/8"));

    /** IPv4 点分十进制字面量。 */
    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private final List<Cidr> trusted;

    /** 启动时解析放行网段，格式错误拒绝启动。 */
    public ChannelAddressGuard(ExternalApiExecutionProperties properties) {
        this.trusted = properties.getTrustedNetworks().stream().map(ChannelAddressGuard::cidr).toList();
    }

    /** 执行时的判定结果。 */
    public enum Verdict {
        /** 全部解析地址都允许连接。 */
        ALLOWED,

        /** 至少一个解析地址落在拦截网段且不在放行网段。 */
        BLOCKED,

        /** 主机名无法解析；交由 HTTP 客户端按网络失败处理。 */
        UNRESOLVED
    }

    /**
     * 保存出站地址时的静态判断：不做 DNS 解析
     *
     * @param host URI 中的主机部分（IPv6 字面量可带方括号）
     * @return 被拦截时返回安全的中文原因；允许返回 null
     */
    public String savingProblem(String host) {
        if (localhost(host)) {
            return "出站地址不能指向 localhost";
        }
        InetAddress literal = literal(host);
        if (literal == null) {
            return null;
        }
        return allowed(literal) ? null : "出站地址不能指向回环、私网、链路本地、保留或组播地址（部署放行网段除外）";
    }

    /**
     * 执行时解析主机名并校验全部地址
     *
     * @param host URI 中的主机部分
     * @return 判定结果
     */
    public Verdict check(String host) {
        InetAddress[] addresses;
        try {
            InetAddress literal = literal(host);
            addresses = literal != null ? new InetAddress[] {literal} : InetAddress.getAllByName(host);
        } catch (UnknownHostException | SecurityException unresolved) {
            return Verdict.UNRESOLVED;
        }
        return Arrays.stream(addresses).allMatch(this::allowed) ? Verdict.ALLOWED : Verdict.BLOCKED;
    }

    /** 放行网段优先；否则不能落在任何拦截网段。 */
    boolean allowed(InetAddress address) {
        InetAddress effective = unmapped(address);
        if (trusted.stream().anyMatch(range -> range.contains(effective))) {
            return true;
        }
        return BLOCKED.stream().noneMatch(range -> range.contains(effective));
    }

    /** 主机名为 localhost 或 *.localhost。 */
    static boolean localhost(String host) {
        String value = host == null ? "" : host.toLowerCase(Locale.ROOT);
        return value.equals("localhost") || value.endsWith(".localhost");
    }

    /** IP 字面量对应的地址，不触发 DNS；普通主机名返回 null。 */
    static InetAddress literal(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        String value = host.toLowerCase(Locale.ROOT);
        try {
            if (value.startsWith("[") && value.endsWith("]")) {
                return InetAddress.getByName(value.substring(1, value.length() - 1));
            }
            if (IPV4_LITERAL.matcher(value).matches()) {
                return ipv4(value);
            }
            if (value.contains(":")) {
                return InetAddress.getByName(value);
            }
        } catch (UnknownHostException invalid) {
            return null;
        }
        return null;
    }

    /** 点分十进制按字节构造地址，不触发 DNS；任一段超过 255 视为主机名之外的非法值，返回 null。 */
    static InetAddress ipv4(String value) throws UnknownHostException {
        String[] parts = value.split("\\.");
        byte[] bytes = new byte[4];
        for (int index = 0; index < 4; index++) {
            int octet = Integer.parseInt(parts[index]);
            if (octet > 255) {
                return null;
            }
            bytes[index] = (byte)octet;
        }
        return InetAddress.getByAddress(bytes);
    }

    /** IPv4 映射的 IPv6 地址按其 IPv4 判断。 */
    static InetAddress unmapped(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (!(address instanceof Inet6Address) || !isMapped(bytes)) {
            return address;
        }
        try {
            return InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16));
        } catch (UnknownHostException impossible) {
            return address;
        }
    }

    /** ::ffff:0:0/96。 */
    static boolean isMapped(byte[] bytes) {
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte)0xff && bytes[11] == (byte)0xff;
    }

    /** 解析 CIDR；格式错误抛 IllegalArgumentException 使启动失败。 */
    static Cidr cidr(String value) {
        int slash = value.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("出站地址放行网段必须是 CIDR：" + value);
        }
        try {
            InetAddress network = InetAddress.getByName(value.substring(0, slash));
            int prefix = Integer.parseInt(value.substring(slash + 1));
            if (prefix < 0 || prefix > network.getAddress().length * 8) {
                throw new IllegalArgumentException("出站地址放行网段前缀不合法：" + value);
            }
            return new Cidr(network.getAddress(), prefix);
        } catch (UnknownHostException | NumberFormatException invalid) {
            throw new IllegalArgumentException("出站地址放行网段不合法：" + value, invalid);
        }
    }

    /** 一个网段；只与同族地址比较。 */
    static final class Cidr {
        private final byte[] network;
        private final int prefix;

        /** 网段地址与前缀长度。 */
        Cidr(byte[] network, int prefix) {
            this.network = network;
            this.prefix = prefix;
        }

        /** 地址的前 prefix 位与网段相同。 */
        boolean contains(InetAddress address) {
            byte[] bytes = address.getAddress();
            if (bytes.length != network.length || (address instanceof Inet4Address) != (network.length == 4)) {
                return false;
            }
            for (int bit = 0; bit < prefix; bit++) {
                int mask = 0x80 >>> (bit % 8);
                if ((bytes[bit / 8] & mask) != (network[bit / 8] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}
