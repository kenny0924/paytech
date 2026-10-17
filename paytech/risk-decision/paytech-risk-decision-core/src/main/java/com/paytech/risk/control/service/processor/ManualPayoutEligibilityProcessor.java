package com.paytech.risk.control.service.processor;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.control.service.processor.request.ManualPayoutEligibilityRequest;
import com.paytech.risk.control.service.processor.response.ManualPayoutEligibilityResponse;

/**
 * RCO-I09 Manual Payout 资格评估
 * <li>先按合同校验检查点/来源矩阵、角色、创建类版本、ID 范围与 inputHash，输入错误返回 400。</li>
 * <li>正式运行 Release 与 Assessment/Evaluation 执行、持久化链尚未接入，合法请求一律返回 503 并 Fail-Closed，不产生任何 ALLOW。</li>
 */
@Component
public class ManualPayoutEligibilityProcessor
    extends AbstractProcessor<ManualPayoutEligibilityRequest, ManualPayoutEligibilityResponse> {
    /** 标准输入的 Schema 标识，参与 inputHash。 */
    static final String INPUT_SCHEMA = "CODE-MOR:RISK:SETTLEMENT_ELIGIBILITY_INPUT:1";

    /** 每个检查点允许的来源事实类型。 */
    static final Map<String, String> SOURCE_TYPES = Map.of("PAYOUT_CREATION", "PAYOUT", "PAYOUT_FINAL_APPROVAL",
        "PAYOUT", "PAYOUT_EXECUTION_CREATION", "PAYOUT_EXECUTION", "PAYOUT_EXTERNAL_SUBMISSION", "PAYOUT_EXECUTION");

    /** 创建类检查点，来源事实版本必须为 1。 */
    static final Set<String> CREATION_CHECKPOINTS = Set.of("PAYOUT_CREATION", "PAYOUT_EXECUTION_CREATION");

    /** 允许的出款角色。 */
    static final Set<String> ROLE_TYPES = Set.of("CLIENT", "LOCAL_PARTNER");

    /** signed BIGINT 上限。 */
    static final BigInteger MAX_SIGNED_BIGINT = BigInteger.valueOf(Long.MAX_VALUE);

    /** 只依赖输入的合同校验，在评估前一次完成。 */
    @Override
    protected void validate(ManualPayoutEligibilityRequest request) {
        String expectedSourceType = SOURCE_TYPES.get(request.getCheckpointType());
        if (expectedSourceType == null || !expectedSourceType.equals(request.getSourceFactType())) {
            throw new InvalidParameterException("出款检查点与来源事实类型不匹配");
        }
        if (CREATION_CHECKPOINTS.contains(request.getCheckpointType()) && request.getSourceFactVersion() != 1) {
            throw new InvalidParameterException("创建类检查点的来源事实版本必须为 1");
        }
        if (!ROLE_TYPES.contains(request.getRoleType())) {
            throw new InvalidParameterException("出款角色只允许 CLIENT 或 LOCAL_PARTNER");
        }
        requireSignedBigint(request.getRoleId(), "角色标识");
        requireSignedBigint(request.getOperatingEntityId(), "经营主体标识");
        requireSignedBigint(request.getSourceFactId(), "来源事实标识");
        if (!sha256(canonicalInput(request)).equals(request.getInputHash())) {
            throw new InvalidParameterException("出款评估 input_hash 与标准输入不一致");
        }
    }

    @Override
    protected ManualPayoutEligibilityResponse processRequest(ManualPayoutEligibilityRequest request) {
        throw new ServiceUnavailableException(new IllegalStateException("Manual Payout 风控评估尚未就绪"));
    }

    /** 十进制 ID 不得超出 signed BIGINT。 */
    void requireSignedBigint(String value, String label) {
        if (new BigInteger(value).compareTo(MAX_SIGNED_BIGINT) > 0) {
            throw new InvalidParameterException(label + "超出 signed BIGINT 范围");
        }
    }

    /**
     * 按键名字典序拼接标准输入 JSON
     * <li>各字段值已由入口格式约束限定为大写编码、数字或十进制金额，无需 JSON 转义。</li>
     */
    String canonicalInput(ManualPayoutEligibilityRequest request) {
        return "{\"bankCountry\":\"" + request.getBankCountry() + "\",\"checkpointType\":\""
            + request.getCheckpointType() + "\",\"currency\":\"" + request.getCurrency() + "\",\"grossAmount\":\""
            + request.getGrossAmount().toPlainString() + "\",\"operatingEntityId\":\"" + request.getOperatingEntityId()
            + "\",\"roleId\":\"" + request.getRoleId() + "\",\"roleType\":\"" + request.getRoleType()
            + "\",\"schema\":\"" + INPUT_SCHEMA + "\",\"sourceFactId\":\"" + request.getSourceFactId()
            + "\",\"sourceFactType\":\"" + request.getSourceFactType() + "\",\"sourceFactVersion\":"
            + request.getSourceFactVersion() + "}";
    }

    /** 计算小写十六进制 SHA-256。 */
    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException failure) {
            throw new ErrorException("JDK 缺少 SHA-256", failure);
        }
    }
}
