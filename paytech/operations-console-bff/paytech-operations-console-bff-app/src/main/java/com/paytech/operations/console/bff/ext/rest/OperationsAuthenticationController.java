package com.paytech.operations.console.bff.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.operations.console.bff.integration.PlatformOperationsClient;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.AccountCreatedResponseBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.ChangePasswordRequestBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.FirstAdministratorRequestBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.LoginRequestBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.RefreshRequestBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.SessionContextResponseBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.TokenGrantResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** 认证 HTTP 入口；Token 使用 JSON 传输，受保护操作由统一入站边界授权。 @author Codex */
@RestController
@AllArgsConstructor
@RequestMapping(OperationsAuthenticationPaths.ROOT)
public class OperationsAuthenticationController {
    private final PlatformOperationsClient client;

    @PostMapping("/login")
    public TokenGrantResponseBody login(@Valid @RequestBody LoginRequestBody request) {
        return client.login(request);
    }

    @PostMapping("/refresh")
    public TokenGrantResponseBody refresh(@Valid @RequestBody RefreshRequestBody request) {
        return client.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(OperatorContext operator, @Valid @RequestBody RefreshRequestBody request) {
        client.logout(operator, request.refreshToken());
    }

    @GetMapping("/context")
    public SessionContextResponseBody context(OperatorContext operator) {
        return client.context(operator);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(OperatorContext operator, @Valid @RequestBody ChangePasswordRequestBody request) {
        client.changePassword(operator, request);
    }

    @PostMapping("/bootstrap/administrator")
    public AccountCreatedResponseBody createFirstAdministrator(OperatorContext operator,
        @Valid @RequestBody FirstAdministratorRequestBody request) {
        return client.createFirstAdministrator(operator, request);
    }
}
