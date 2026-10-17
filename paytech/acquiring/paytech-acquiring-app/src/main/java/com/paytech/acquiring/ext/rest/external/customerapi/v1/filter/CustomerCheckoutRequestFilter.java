package com.paytech.commerce.ext.rest.external.customerapi.v1.filter;

import com.paytech.foundation.common.error.InvalidParameterException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Customer 协议及受限 Body 边界。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 35)
public class CustomerCheckoutRequestFilter extends OncePerRequestFilter {
    /** 只匹配 Commerce 统一 Customer 前缀。 */
    protected boolean shouldNotFilter(HttpServletRequest r) {
        String p = r.getServletPath();
        return !p.startsWith("/customer-gateway-api/v1/");
    }

    /** 成功和异常都沿共享 Problem Writer；不记录或复制请求日志。 */
    protected void doFilterInternal(
            HttpServletRequest r, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = r.getServletPath();
        boolean get = "GET".equals(r.getMethod());
        boolean payment = path.endsWith("/payments") && "POST".equals(r.getMethod());
        int maximumBody = payment ? 8192 : 16384;
        if (payment
                && (r.getContentType() == null
                        || !r.getContentType()
                                .split(";", 2)[0]
                                .trim()
                                .equalsIgnoreCase("application/json"))) {
            // 在参数或 multipart 解析之前拒绝，避免含卡请求被容器缓冲到文件。
            throw new org.springframework.web.HttpMediaTypeNotSupportedException(
                    "支付请求只支持 application/json");
        }
        if (payment && r.getHeader("Content-Encoding") != null) {
            throw new InvalidParameterException("支付请求不接受 Content-Encoding");
        }
        var parameters = r.getParameterMap();
        if (get && path.matches("/customer-gateway-api/v1/checkouts/[^/]+")) {
            var values = parameters.get("includeConsumer");
            if (parameters.size() > (values == null ? 0 : 1)
                    || values != null
                            && (values.length != 1
                                    || !java.util.Set.of("true", "false").contains(values[0]))) {
                throw new InvalidParameterException("只允许一个 includeConsumer 布尔参数");
            }
        } else if (!parameters.isEmpty()) {
            throw new InvalidParameterException("该接口不接受 Query 参数");
        }
        if (get && r.getInputStream().read() != -1) {
            throw new InvalidParameterException("该接口不接受 Body");
        }
        if (!get) {
            if (r.getContentLengthLong() > maximumBody) {
                throw new InvalidParameterException("请求 Body 超过允许上限");
            }
            var limited =
                    new HttpServletRequestWrapper(r) {
                        /** 流式限制未知 Content-Length；不缓存到磁盘或日志。 */
                        public ServletInputStream getInputStream() throws IOException {
                            var input = super.getInputStream();
                            return new ServletInputStream() {
                                private int count;

                                public int read() throws IOException {
                                    int value = input.read();
                                    if (value != -1 && ++count > maximumBody) {
                                        throw new InvalidParameterException("请求 Body 超过允许上限");
                                    }
                                    return value;
                                }

                                public boolean isFinished() {
                                    return input.isFinished();
                                }

                                public boolean isReady() {
                                    return input.isReady();
                                }

                                public void setReadListener(ReadListener listener) {
                                    input.setReadListener(listener);
                                }
                            };
                        }
                    };
            chain.doFilter(limited, response);
        } else {
            chain.doFilter(r, response);
        }
    }
}
