package com.paytech.reconciliation.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.reconciliation.api.enums.ReconClassification;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ResolutionType;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 异常处置的可用范围与内容摘要（设计 F3、technique §2.3 处置应用规则）
 * <li>处置方式按异常类型限定；归类码按来源对象限定（F3 表），归类码只选科目，不改变入账路径。</li>
 * <li>ACCEPT_TIMING_DIFFERENCE 在交易层只用于 COST_DEVIATION、DUPLICATE_SETTLEMENT、PLATFORM_ITEM_UNSETTLED；银行层异常均可接受（不动金额）。</li>
 */
public final class ReconResolutionRules {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ReconResolutionRules() {
    }

    /** 异常类型可用的处置方式。 */
    public static List<ResolutionType> allowedTypes(ReconExceptionType type) {
        return switch (type) {
            case UNEXPLAINED_ADJUSTMENT -> List.of(ResolutionType.CLASSIFY);
            case CHANNEL_LINE_UNMATCHED, AMBIGUOUS_REFERENCE, AMOUNT_MISMATCH -> List.of(ResolutionType.RE_MATCH,
                ResolutionType.CLASSIFY);
            case DUPLICATE_SETTLEMENT, PLATFORM_ITEM_UNSETTLED, BANK_AMOUNT_MISMATCH -> List.of(
                ResolutionType.ACCEPT_TIMING_DIFFERENCE, ResolutionType.CLASSIFY);
            case BANK_LINE_UNMATCHED -> List.of(ResolutionType.RE_MATCH, ResolutionType.ACCEPT_TIMING_DIFFERENCE,
                ResolutionType.CLASSIFY);
            default -> List.of(ResolutionType.ACCEPT_TIMING_DIFFERENCE);
        };
    }

    /** 来源对象可用的归类码（F3 表）。 */
    public static List<ReconClassification> allowedCodes(ReconExceptionSourceType sourceType) {
        return switch (sourceType) {
            case CHANNEL_STATEMENT_LINE -> List.of(ReconClassification.CHANNEL_FEE_OTHER,
                ReconClassification.CHANNEL_RESERVE_HOLD, ReconClassification.CHANNEL_RESERVE_RELEASE,
                ReconClassification.RECON_DIFFERENCE);
            case PLATFORM_TXN -> List.of(ReconClassification.RECON_DIFFERENCE);
            case EXPECTED_BANK_MOVEMENT -> List.of(ReconClassification.BANK_FEE, ReconClassification.RECON_DIFFERENCE);
            case BANK_STATEMENT_LINE -> List.of(ReconClassification.BANK_FEE, ReconClassification.BANK_INTEREST,
                ReconClassification.INTERNAL_TRANSFER, ReconClassification.UNALLOCATED_RECEIPT,
                ReconClassification.PAYOUT_RETURN_PENDING, ReconClassification.RECON_DIFFERENCE);
            default -> List.of();
        };
    }

    /** 归类是否单独发 ReconAdjustmentApproved：来源为平台流水、预期银行流水、银行流水（账单行随对平事件带出）。 */
    public static boolean publishesAdjustment(ReconExceptionSourceType sourceType) {
        return sourceType == ReconExceptionSourceType.PLATFORM_TXN
            || sourceType == ReconExceptionSourceType.EXPECTED_BANK_MOVEMENT
            || sourceType == ReconExceptionSourceType.BANK_STATEMENT_LINE;
    }

    /**
     * 处置内容摘要：异常 ID、处置方式与全部输入的 JSON（固定字段顺序）的 SHA-256 小写十六进制
     *
     * @param resolution 已填好输入的处置
     * @return 64 位摘要
     */
    public static String contentHash(ReconResolution resolution) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("exceptionId", resolution.getExceptionId().toString());
        node.put("resolutionType", resolution.getResolutionType().getCode());
        node.put("classificationCode", resolution.getClassificationCode() == null ? null
            : resolution.getClassificationCode().getCode());
        node.put("amount", plain(resolution.getAmount()));
        node.put("rematchTargetType", resolution.getRematchTargetType() == null ? null
            : resolution.getRematchTargetType().getCode());
        node.put("rematchTargetId", resolution.getRematchTargetId() == null ? null
            : resolution.getRematchTargetId().toString());
        node.put("rematchAmount", plain(resolution.getRematchAmount()));
        node.put("note", resolution.getNote());
        return sha256(JSON.writeValueAsString(node));
    }

    /** 去掉尾随零的十进制文本；为空时为空。 */
    static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    /**
     * UTF-8 文本的 SHA-256 小写十六进制
     *
     * @param text 文本
     * @return 64 位摘要
     */
    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException missing) {
            throw new ErrorException("运行环境缺少 SHA-256", missing);
        }
    }
}
