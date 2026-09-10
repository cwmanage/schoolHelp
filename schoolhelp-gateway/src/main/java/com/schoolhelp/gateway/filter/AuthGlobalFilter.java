package com.schoolhelp.gateway.filter;

import com.schoolhelp.common.constant.CommonConstants;
import com.schoolhelp.common.util.JwtUtil;
import com.schoolhelp.gateway.config.AuthWhitelistProperties;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局鉴权过滤器：
 * 1. 白名单直接放行
 * 2. 其他请求校验 JWT，解析后写入 X-User-Id / X-Username / X-Role 头传给下游服务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final AuthWhitelistProperties whitelistProperties;
    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 白名单放行
        if (isWhitelist(path)) {
            return chain.filter(exchange);
        }

        // 2. 校验 JWT
        String auth = request.getHeaders().getFirst(CommonConstants.TOKEN_HEADER);
        if (auth == null || !auth.startsWith(CommonConstants.TOKEN_PREFIX)) {
            return unauthorized(exchange, "未登录或登录已过期");
        }
        String token = auth.substring(CommonConstants.TOKEN_PREFIX.length());
        Claims claims = JwtUtil.parse(token);
        if (claims == null) {
            return unauthorized(exchange, "登录凭证无效，请重新登录");
        }

        // 3. 写入下游用户头
        Long userId = claims.get("userId", Long.class);
        String username = claims.get("username", String.class);
        Integer role = claims.get("role", Integer.class);

        ServerHttpRequest mutated = request.mutate()
                .header(com.schoolhelp.common.util.UserContext.HEADER_USER_ID, String.valueOf(userId))
                .header(com.schoolhelp.common.util.UserContext.HEADER_USERNAME, username == null ? "" : username)
                .header(com.schoolhelp.common.util.UserContext.HEADER_ROLE, String.valueOf(role))
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isWhitelist(String path) {
        return whitelistProperties.getWhitelist().stream()
                .anyMatch(pattern -> matcher.match(pattern, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
