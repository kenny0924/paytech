package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.operations.console.bff.integration.PlatformDataQueryClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.dataquery.request.JointQueryStepRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.JointQueryGraphResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.JointQuerySettingsResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.JointQueryStepResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 联合查询页的浏览器入口，同路径简单转发 Platform
 * <li>路由由 Gateway 绑定 DATA_QUERY_EXECUTE 并经 PLT-I02 实时授权；Platform 再校验调用方登记与权限。</li>
 * <li>每层一次调用：visited、层数、累计执行数与总时长由页面保存，BFF 不聚合、不循环。结果行只记元数据日志，不作为业务执行依据。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/data-query/v1")
public class DataQueryJointController {

    private final PlatformDataQueryClient client;

    /** 入口键类型、分组与护栏。 */
    @GetMapping("/joint-query/settings")
    public JointQuerySettingsResponseBody getJointQuerySettings(OperatorContext context) {
        return client.getJointQuerySettings(context);
    }

    /** 从入口键类型出发的可达预览，不执行查询。 */
    @GetMapping("/joint-query/graph")
    public JointQueryGraphResponseBody getJointQueryGraph(OperatorContext context,
        @RequestParam("entryKeyType") @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String entryKeyType) {
        return client.getJointQueryGraph(context, entryKeyType);
    }

    /** 执行一层。 */
    @PostMapping("/joint-query/steps")
    public JointQueryStepResponseBody executeJointQueryStep(OperatorContext context,
        @Valid @RequestBody JointQueryStepRequestBody request) {
        return client.executeJointQueryStep(context, request);
    }
}
