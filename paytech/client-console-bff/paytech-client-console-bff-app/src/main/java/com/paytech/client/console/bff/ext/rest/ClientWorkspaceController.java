package com.paytech.client.console.bff.ext.rest;

import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.console.bff.service.model.ClientWorkspaceModels.WorkspaceHomeResponseBody;
import com.paytech.client.console.bff.service.processor.ClientWorkspaceHomeProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工作区页面的薄适配；只读聚合，不接受浏览器提交的客户或租户标识。 */
@RestController
@lombok.AllArgsConstructor
@RequestMapping("/client-workspace/v1")
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientWorkspaceController {

    private final ClientWorkspaceHomeProcessor home;

    /** W02 工作区首页，只在租户上下文可用。 */
    @GetMapping("/home")
    public WorkspaceHomeResponseBody home(ClientActor actor) {
        return home.process(actor);
    }
}
