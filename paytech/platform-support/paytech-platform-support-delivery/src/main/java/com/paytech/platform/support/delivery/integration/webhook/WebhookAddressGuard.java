package com.paytech.platform.support.delivery.integration.webhook;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.delivery.core.configuration.WebhookProperties;
import com.paytech.platform.support.delivery.core.enums.WebhookFailureCode;

/**
 * 端点地址的 SSRF 防护：解析主机名，全部地址都必须是公网地址或在部署放行的网段内
 * <li>发送时由 HttpClient 的 DNS 解析接口调用，连接只使用本次校验过的地址，避免 DNS rebinding</li>
 * <li>拒绝：0/8、10/8、100.64/10、127/8、169.254/16、172.16/12、192.0.0/24、192.168/16、198.18/15、224/4、240/4，
 * IPv6 的未指定、回环、fc00::/7、fe80::/10、ff00::/8；IPv4 映射地址按 IPv4 判断</li>
 */
@Component
@ConditionalOnProperty(name = "webhook.enabled", havingValue = "true")
public class WebhookAddressGuard {

    private static final List<Cidr> BLOCKED = List.of(cidr("0.0.0.0/8"), cidr("10.0.0.0/8"),
        cidr("100.64.0.0/10"), cidr("127.0.0.0/8"), cidr("169.254.0.0/16"), cidr("172.16.0.0/12"),
        cidr("192.0.0.0/24"), cidr("192.168.0.0/16"), cidr("198.18.0.0/15"), cidr("224.0.0.0/4"),
        cidr("240.0.0.0/4"), cidr("::/128"), cidr("::1/128"), cidr("fc00::/7"), cidr("fe80::/10"),
        cidr("ff00::/8"));

    private final List<Cidr> trusted;

    /** 启动时解析放行网段，格式错误拒绝启动。 */
    public WebhookAddressGuard(WebhookProperties properties) {
        this.trusted = properties.getTrustedNetworks().stream().map(WebhookAddressGuard::cidr).toList();
    }

    /**
     * 解析主机名并校验全部地址
     *
     * @param host 端点主机名或地址字面量
     * @return 可连接的地址
     * @throws WebhookAddressException 无法解析（DNS_FAILED）或任一地址被禁止（BLOCKED_ADDRESS）
     */
    public InetAddress[] resolve(String host) throws WebhookAddressException {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException | SecurityException unresolved) {
            throw new WebhookAddressException(WebhookFailureCode.DNS_FAILED);
        }
        for (InetAddress address : addresses) {
            if (!allowed(address)) {
                throw new WebhookAddressException(WebhookFailureCode.BLOCKED_ADDRESS);
            }
        }
        return addresses;
    }

    /** 放行网段优先；否则不能落在任何禁止网段。 */
    boolean allowed(InetAddress address) {
        InetAddress effective = unmapped(address);
        if (trusted.stream().anyMatch(range -> range.contains(effective))) {
            return true;
        }
        return BLOCKED.stream().noneMatch(range -> range.contains(effective));
    }

    /** IPv4 映射的 IPv6 地址按其 IPv4 判断。 */
    static InetAddress unmapped(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (!(address instanceof Inet6Address) || !isMapped(bytes)) {
            return address;
        }
        try {
            return InetAddress.getByAddress(java.util.Arrays.copyOfRange(bytes, 12, 16));
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
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    /** 解析 CIDR；格式错误抛 IllegalArgumentException 使启动失败。 */
    static Cidr cidr(String value) {
        int slash = value.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("webhook 网段必须是 CIDR：" + value);
        }
        try {
            InetAddress network = InetAddress.getByName(value.substring(0, slash));
            int prefix = Integer.parseInt(value.substring(slash + 1));
            if (prefix < 0 || prefix > network.getAddress().length * 8) {
                throw new IllegalArgumentException("webhook 网段前缀不合法：" + value);
            }
            return new Cidr(network.getAddress(), prefix);
        } catch (UnknownHostException | NumberFormatException invalid) {
            throw new IllegalArgumentException("webhook 网段不合法：" + value, invalid);
        }
    }

    /** 一个网段；只与同族地址比较。 */
    record Cidr(byte[] network, int prefix) {

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
