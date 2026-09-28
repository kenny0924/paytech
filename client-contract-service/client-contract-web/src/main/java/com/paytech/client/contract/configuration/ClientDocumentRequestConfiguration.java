package com.paytech.client.contract.configuration;
import java.io.IOException;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import com.paytech.foundation.common.error.InvalidParameterException;
/** 材料命令使用精确路径和 JSON，移除不接受 Body。 */
@Configuration(proxyBeanMethods=false)
public class ClientDocumentRequestConfiguration implements WebMvcConfigurer {
    /** 不将未知 Query 解释为材料权限或筛选条件。 */
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /** C03 只接收两个唯一 Query，禁止隐含筛选和 GET Body。 */
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws IOException {
                response.setHeader("Cache-Control","no-store");
                for(var entry:request.getParameterMap().entrySet()) {
                    if(!java.util.Set.of("clientType","countryCode").contains(entry.getKey()) || entry.getValue().length!=1) {
                        throw new InvalidParameterException("目录查询参数无效");
                    }
                }
                if(request.getInputStream().read()!=-1) { throw new InvalidParameterException("目录查询不接受 Body"); }
                return true;
            }
        }).addPathPatterns("/client-onboarding/v1/form-options");
        registry.addInterceptor(new HandlerInterceptor() {
            /** 无论成功与失败都不缓存材料响应。 */
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws IOException {
                response.setHeader("Cache-Control","no-store");
                if (!request.getParameterMap().isEmpty()) { throw new InvalidParameterException("当前操作不接受 Query"); }
                if (java.util.Set.of("GET", "HEAD", "DELETE").contains(request.getMethod()) && request.getInputStream().read()!=-1) { throw new InvalidParameterException("当前操作不接受 Body"); }
                return true;
            }
        }).addPathPatterns("/client-onboarding/v1/start", "/client-onboarding/v1/applications/*/documents", "/client-onboarding/v1/applications/*/documents/*");
    }
}
