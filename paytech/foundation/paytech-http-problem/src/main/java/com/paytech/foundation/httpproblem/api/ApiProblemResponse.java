package com.paytech.foundation.httpproblem.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiProblemResponse(
    String type,
    String title,
    String detail,
    String code,
    String requestId,
    String timestamp,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ProblemFieldError> errors
) {
}
