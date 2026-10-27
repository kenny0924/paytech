package com.paytech.client.master.domain.onboarding.pack;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.paytech.client.master.core.dal.dataobject.IdentifierType;
import com.paytech.client.master.domain.onboarding.ClientIdentifierNormalizer;
import com.paytech.foundation.common.error.ErrorException;

/**
 * 法律标识的规范化与校验位算法注册表（design 10.0.1a 第 2 类插件位）
 * <li>算法按 t_identifier_type.normalization_rule / checksum_rule 的代码查表；代码中没有按国家或标识类型取值的分支。</li>
 * <li>未登记的算法代码属于目录配置错误，失败关闭。新算法（如 KR_BRN、CPF MOD11）= 在此登记一项实现。</li>
 */
public final class IdentifierRules {
    /** 号码格式不符。 */
    public static final String FORMAT_INVALID = "IDENTIFIER_FORMAT_INVALID";
    /** 校验位不正确。 */
    public static final String CHECKSUM_FAILED = "IDENTIFIER_CHECKSUM_FAILED";

    /** 埃及国民身份证第 8—9 位省代码的公开取值（含境外出生 88）。 */
    private static final Set<String> EGYPT_GOVERNORATES = Set.of("01", "02", "03", "04", "11", "12", "13", "14", "15", "16", "17",
        "18", "19", "21", "22", "23", "24", "25", "26", "27", "28", "29", "31", "32", "33", "34", "35", "88");

    /** 校验位算法：输入规范化后号码，返回是否通过。 */
    private static final Map<String, Predicate<String>> CHECKSUMS = Map.of(
        "NONE", value -> true,
        "LUHN", IdentifierRules::luhn,
        "EG_NID", IdentifierRules::egyptNationalId);

    private IdentifierRules() {
    }

    /**
     * 按标识类型的规范化规则处理原始号码
     *
     * @param type 标识类型
     * @param raw 原始号码
     * @return 规范化号码；字符不合法时为 null
     */
    public static String normalize(IdentifierType type, String raw) {
        if (!"STANDARD".equals(type.getNormalizationRule())) {
            throw new ErrorException("未登记的标识规范化规则: " + type.getNormalizationRule());
        }
        return ClientIdentifierNormalizer.normalize(raw);
    }

    /**
     * 格式与校验位检查
     *
     * @param type 标识类型
     * @param normalized 规范化后号码
     * @return 通过为 null；否则为 IDENTIFIER_FORMAT_INVALID 或 IDENTIFIER_CHECKSUM_FAILED
     */
    public static String check(IdentifierType type, String normalized) {
        Predicate<String> checksum = CHECKSUMS.get(type.getChecksumRule());
        if (checksum == null) {
            throw new ErrorException("未登记的标识校验位规则: " + type.getChecksumRule());
        }
        if (type.getFormatRegex() != null && !normalized.matches(type.getFormatRegex())) {
            return FORMAT_INVALID;
        }
        return checksum.test(normalized) ? null : CHECKSUM_FAILED;
    }

    /** 展示掩码：只保留末 4 位。 */
    public static String mask(String normalized) {
        return normalized.length() <= 4 ? "****" : "****" + normalized.substring(normalized.length() - 4);
    }

    /** Luhn（mod 10）：沙特国民身份证与 Iqama。 */
    static boolean luhn(String value) {
        if (!value.matches("[0-9]{2,64}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < value.length(); i++) {
            int digit = value.charAt(value.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                digit = digit * 2 > 9 ? digit * 2 - 9 : digit * 2;
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }

    /**
     * 埃及国民身份证结构：世纪位（2=1900 年代、3=2000 年代）+ YYMMDD 有效日期 + 省代码 + 序列 + 校验位
     * <li>官方校验位算法未公开，只校验结构；与出生日期是否一致留给审核（design 9.3）。</li>
     */
    static boolean egyptNationalId(String value) {
        if (!value.matches("[23][0-9]{13}")) {
            return false;
        }
        int century = value.charAt(0) == '2' ? 1900 : 2000;
        try {
            LocalDate.of(century + Integer.parseInt(value.substring(1, 3)), Integer.parseInt(value.substring(3, 5)),
                Integer.parseInt(value.substring(5, 7)));
        } catch (DateTimeException invalid) {
            return false;
        }
        return EGYPT_GOVERNORATES.contains(value.substring(7, 9));
    }
}
