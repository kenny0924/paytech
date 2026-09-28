package com.paytech.risk.api.evaluation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Manual Payout 风控资格检查的内部 HTTP 模型。
 */
public final class ManualPayoutEligibilityModels {

    private ManualPayoutEligibilityModels() {}

    public record EvaluateRequestBody(
        @NotBlank @Size(max = 128) String commandKey,
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") @Size(max = 40) String checkpointType,
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") @Size(max = 32) String roleType,
        @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") String roleId,
        @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") String operatingEntityId,
        @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,15})(\\.[0-9]{0,7}[1-9])?$") @DecimalMin(value = "0", inclusive = false) String grossAmount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String bankCountry,
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") @Size(max = 32) String sourceFactType,
        @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") String sourceFactId,
        @NotNull @Min(1) @Max(2147483647) Integer sourceFactVersion,
        @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String inputHash) {
    }

    public record EvaluateResponseBody(
        long assessmentId,
        long evaluationId,
        String decision,
        List<String> reasonCodes,
        List<String> matchedControls,
        String requiredAmount,
        String requiredCurrency,
        String controlSetHash) {
    }
}
