package com.paytech.client.console.bff.ext.rest;
import java.net.URI;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.*;
import com.paytech.foundation.common.error.FailException;
import com.paytech.client.console.bff.domain.enums.ClientBffErrorCode;
/** 同源 Cookie 写请求必须携带精确 Origin，来源不由代理 Header 决定。 */
@Component
public class ClientRequestInterceptor implements HandlerInterceptor {
    private final String origin;
    public ClientRequestInterceptor(@Value("${client.console-origin}") String origin) {
        URI uri=URI.create(origin);
        if(!"https".equals(uri.getScheme()) || uri.getHost()==null || !uri.getPath().isEmpty()
            || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) {
            throw new IllegalArgumentException("Client Console 必须配置精确 HTTPS Origin");
        }
        this.origin=origin;
    }
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
        response.setHeader("Cache-Control","no-store");
        response.setHeader("Pragma","no-cache");
        response.setHeader("Referrer-Policy","no-referrer");
        if(!(handler instanceof HandlerMethod)) { return true; }
        boolean read="GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
        var origins=Collections.list(request.getHeaders("Origin"));
        if(!(read && origins.isEmpty()) && (origins.size()!=1 || !origin.equals(origins.getFirst()))) {
            throw new FailException(ClientBffErrorCode.ORIGIN_REJECTED);
        }
        boolean emptyCommand=request.getRequestURI().startsWith("/client-onboarding/") && "DELETE".equals(request.getMethod())
            || request.getRequestURI().startsWith("/onboarding-files/") && (request.getRequestURI().endsWith("/completion") || request.getRequestURI().endsWith("/download"));
        if(!read && !emptyCommand) {
            try {
                if(request.getContentType()==null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.getContentType()))) {
                    throw new IllegalArgumentException();
                }
            } catch(IllegalArgumentException failure) {
                throw new FailException(ClientBffErrorCode.UNSUPPORTED_MEDIA_TYPE);
            }
        }
        return true;
    }
}
