package com.paytech.risk.configuration;
import java.io.IOException;
import java.util.Set;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import com.paytech.foundation.common.error.InvalidParameterException;
/** 私网材料查询保持有界参数和空 Body，无缓存许可。 */
@Configuration(proxyBeanMethods=false)
public class MaterialRequirementsRequestConfiguration implements WebMvcConfigurer {
    /** 仅绑定批准的只读合同路径。 */
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /** 只允许两个准确参数，拒绝重复/未知字段。 */
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws IOException {
                response.setHeader("Cache-Control","no-store");
                for (var item:request.getParameterMap().entrySet())
                    if (!Set.of("qualificationType","countryCode").contains(item.getKey()) || item.getValue().length!=1 || item.getValue()[0].isBlank())
                        throw new InvalidParameterException("材料查询参数未知、重复或为空");
                if (request.getInputStream().read()!=-1) throw new InvalidParameterException("材料查询不接受 Body");
                return true;
            }
        }).addPathPatterns("/internal/due-diligence/material-requirements");
    }
}
