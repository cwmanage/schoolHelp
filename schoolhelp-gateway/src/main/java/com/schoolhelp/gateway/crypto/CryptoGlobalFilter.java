package com.schoolhelp.gateway.crypto;

import com.schoolhelp.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 前后端报文加密（v4 安全需求）：请求密文 → 网关解密 → 下游明文；下游明文 → 网关加密 → 前端解密。
 * F12/抓包只能看到密文，防止密码等敏感字段泄露。
 *
 * 规则：
 * - 仅 POST/PUT/PATCH 且 Content-Type 为 JSON 的请求做解密；multipart（文件上传）与无 body 方法跳过
 * - 请求体为 {"payload":"iv.cipher"} 结构才解密；**解密失败直接 400 拒绝**（防伪造乱码打穿下游）
 * - 明文请求（curl 调试/未升级客户端）原样透传
 * - 所有 application/json 响应统一加密返回（含 401/业务异常）；非 JSON（文件流）跳过
 * - 未配置密钥（本地开发）时本过滤器自动禁用
 */
@Component
public class CryptoGlobalFilter implements GlobalFilter, Ordered {

    @Value("${SCHOOLHELP_CRYPTO_KEY:}")
    private String keyHex;

    private volatile CryptoUtil crypto;

    private CryptoUtil crypto() {
        if (crypto == null) {
            synchronized (this) {
                if (crypto == null) crypto = CryptoUtil.fromHexKey(keyHex);
            }
        }
        return crypto;
    }

    private boolean enabled() {
        return crypto() != null;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled()) return chain.filter(exchange);

        ServerHttpRequest request = exchange.getRequest();
        boolean hasBody = request.getMethod() == HttpMethod.POST
                || request.getMethod() == HttpMethod.PUT
                || request.getMethod() == HttpMethod.PATCH;
        MediaType ct = request.getHeaders().getContentType();
        boolean json = ct != null && ct.isCompatibleWith(MediaType.APPLICATION_JSON);
        boolean multipart = ct != null && ct.isCompatibleWith(MediaType.MULTIPART_FORM_DATA);

        // ---- 请求解密（仅 JSON body）----
        if (hasBody && json && !multipart) {
            return DataBufferUtils.join(request.getBody())
                    .map(this::toBytesAndRelease)
                    .defaultIfEmpty(new byte[0])
                    .flatMap(bodyBytes -> {
                        String bodyStr = bodyBytes.length == 0 ? "" : new String(bodyBytes, StandardCharsets.UTF_8);
                        if (CryptoUtil.isEncryptedBody(bodyStr)) {
                            String plain = crypto().decrypt(extractPayload(bodyStr));
                            if (plain == null) {
                                return reject(exchange, "请求报文加密格式非法");
                            }
                            ServerHttpRequest mutated = decorate(request, plain);
                            ServerWebExchange ex = exchange.mutate().request(mutated).build();
                            return chain.filter(withEncryptedResponse(ex));
                        }
                        // 明文请求透传（curl 调试/兼容），响应仍加密
                        ServerHttpRequest mutated = decorate(request, bodyStr);
                        ServerWebExchange ex = exchange.mutate().request(mutated).build();
                        return chain.filter(withEncryptedResponse(ex));
                    });
        }

        // ---- 无 body 方法（GET/DELETE）或 multipart：请求透传，响应仍加密 ----
        return chain.filter(withEncryptedResponse(exchange));
    }

    /** 响应加密装饰器：捕获下游 JSON 响应 → 加密 → 重写 Content-Length */
    private ServerWebExchange withEncryptedResponse(ServerWebExchange exchange) {
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                MediaType ct = getHeaders().getContentType();
                boolean json = ct == null || ct.isCompatibleWith(MediaType.APPLICATION_JSON);   // 默认 JSON（网关自身响应常无 CT）
                if (!json) return super.writeWith(body);

                return DataBufferUtils.join((org.reactivestreams.Publisher<DataBuffer>) body)
                        .map(this::toBytesAndRelease)
                        .defaultIfEmpty(new byte[0])
                        .flatMap(outBytes -> {
                            String plain = outBytes.length == 0 ? "" : new String(outBytes, StandardCharsets.UTF_8);
                            String encJson = "{\"payload\":\"" + crypto().encrypt(plain) + "\"}";
                            byte[] enc = encJson.getBytes(StandardCharsets.UTF_8);
                            getHeaders().setContentLength(enc.length);
                            getHeaders().remove("Content-Encoding");
                            DataBuffer buf = bufferFactory().wrap(enc);
                            return super.writeWith(Flux.just(buf));
                        });
            }

            private byte[] toBytesAndRelease(DataBuffer buf) {
                byte[] out = new byte[buf.readableByteCount()];
                buf.read(out);
                DataBufferUtils.release(buf);
                return out;
            }
        };
        return exchange.mutate().response(decorated).build();
    }

    private ServerHttpRequest decorate(ServerHttpRequest request, String body) {
        var factory = new DefaultDataBufferFactory();
        return new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.just(factory.wrap(body.getBytes(StandardCharsets.UTF_8)));
            }
        };
    }

    private String extractPayload(String bodyStr) {
        // 简单提取 {"payload":"..."} 的值（密文体不含引号，正则安全）
        String key = "\"payload\":\"";
        int s = bodyStr.indexOf(key);
        if (s < 0) return null;
        int e = bodyStr.indexOf('"', s + key.length());
        return e < 0 ? null : bodyStr.substring(s + key.length(), e);
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(org.springframework.http.HttpStatus.BAD_REQUEST);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"code\":400,\"message\":\"" + message + "\",\"success\":false}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private byte[] toBytesAndRelease(DataBuffer buf) {
        byte[] out = new byte[buf.readableByteCount()];
        buf.read(out);
        DataBufferUtils.release(buf);
        return out;
    }

    @Override
    public int getOrder() {
        return -200;   // 在鉴权过滤器（-100）之前完成解密/加密包装
    }
}
