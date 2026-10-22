package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.operations.console.bff.integration.PlatformDataQueryClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.dataquery.request.ExecuteDataQueryRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.DataQueryResultResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 按模板编码执行只读查询的浏览器入口，同路径简单转发 Platform
 * <li>路由由 Gateway 绑定 DATA_QUERY_EXECUTE 并经 PLT-I02 实时授权；Platform 再校验调用方白名单与权限，可执行任意 ACTIVE 模板。</li>
 * <li>参数值与结果行只记元数据日志；结果不作为业务执行依据。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/data-query/v1")
public class DataQueryExecutionController {

    private final PlatformDataQueryClient client;

    /** 执行模板当前版本。 */
    @PostMapping("/templates/{templateCode}/executions")
    public DataQueryResultResponseBody executeDataQuery(OperatorContext context,
        @PathVariable("templateCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String templateCode,
        @Valid @RequestBody ExecuteDataQueryRequestBody request) {
        return client.executeDataQuery(context, templateCode, request);
    }
}
