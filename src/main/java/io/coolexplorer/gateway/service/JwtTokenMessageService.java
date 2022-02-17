package io.coolexplorer.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.coolexplorer.gateway.message.JwtTokenMessage;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface JwtTokenMessageService {
    Boolean validateJwtToken(JwtTokenMessage.ValidateMessage validateMessage) throws ExecutionException, InterruptedException, TimeoutException, JsonProcessingException;
}
