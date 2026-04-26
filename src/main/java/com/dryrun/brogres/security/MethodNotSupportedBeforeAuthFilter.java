package com.dryrun.brogres.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * For routes registered in MVC, Spring Security’s {@code authenticated()} runs
 * before the {@link org.springframework.web.servlet.DispatcherServlet}. A wrong
 * HTTP method is therefore answered with 401 (unauthenticated) instead of 405
 * (method not allowed) when no token is present. This filter resolves the handler
 * mapping first and returns 405 in that case so the client is not sent to sign-in.
 */
public class MethodNotSupportedBeforeAuthFilter extends OncePerRequestFilter {

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    public MethodNotSupportedBeforeAuthFilter(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            requestMappingHandlerMapping.getHandler(request);
        } catch (HttpRequestMethodNotSupportedException e) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setContentType("application/json;charset=UTF-8");
            Set<HttpMethod> supported = e.getSupportedHttpMethods();
            if (supported != null && !supported.isEmpty()) {
                String allow = supported.stream()
                        .map(HttpMethod::name)
                        .collect(Collectors.joining(", "));
                response.setHeader("Allow", allow);
            }
            response.getWriter().write("{\"error\":\"method_not_allowed\"}");
            return;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        filterChain.doFilter(request, response);
    }
}
