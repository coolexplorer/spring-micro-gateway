package io.coolexplorer.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coolexplorer.gateway.dto.ErrorResponse;
import io.coolexplorer.gateway.enums.ErrorCode;
import io.coolexplorer.gateway.message.JwtTokenMessage;
import io.coolexplorer.gateway.service.JwtTokenMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class AuthenticationGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthenticationGatewayFilterFactory.Config> {
    public static final String AUTH_HEADER = "Authorization";
    public static final String AUTH_PREFIX = "Bearer ";
    private final JwtTokenMessageService jwtTokenMessageService;
    private final ObjectMapper objectMapper;
    private final MessageSourceAccessor errorMessageSourceAccessor;

    public AuthenticationGatewayFilterFactory(
            JwtTokenMessageService jwtTokenMessageService,
            ObjectMapper objectMapper,
            MessageSourceAccessor errorMessageSourceAccessor) {
        super(Config.class);
        this.jwtTokenMessageService = jwtTokenMessageService;
        this.objectMapper = objectMapper;
        this.errorMessageSourceAccessor = errorMessageSourceAccessor;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return super.shortcutFieldOrder();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            if (!containsAuthHeader(request)) {
                try {
                    return onError(response, ErrorCode.AUTH_HEADER_NOT_FOUND);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }

            String jwtToken = resolveToken(request);
            try {
                if (!sendJwtTokenValidation(jwtToken)) {
                    return onError(response, ErrorCode.JWT_TOKEN_INVALID);
                }
            } catch (ExecutionException | InterruptedException | JsonProcessingException | TimeoutException e) {
                e.printStackTrace();
            }

            return chain.filter(exchange);
        });
    }

    private boolean containsAuthHeader(ServerHttpRequest request) {
        return request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION);
    }

    private Mono<Void> onError(ServerHttpResponse response, ErrorCode errorCode) throws JsonProcessingException {
        ErrorResponse errorResponse = new ErrorResponse()
                .setCode(errorCode)
                .setDescription(errorMessageSourceAccessor.getMessage(errorCode.getMessageKey()));

        response.setStatusCode(HttpStatus.BAD_REQUEST);
        DataBuffer buffer = response.bufferFactory().wrap(objectMapper.writeValueAsString(errorResponse).getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private boolean sendJwtTokenValidation(String jwtToken) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException {
        JwtTokenMessage.ValidateMessage message = new JwtTokenMessage.ValidateMessage()
                .setJwtToken(jwtToken);
        return jwtTokenMessageService.validateJwtToken(message);
    }

    public String resolveToken(ServerHttpRequest request) {
        return request.getHeaders().getOrEmpty(AUTH_HEADER).get(0).replace(AUTH_PREFIX, "");
    }

    public static class Config {
    }
}
