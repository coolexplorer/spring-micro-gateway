package io.coolexplorer.gateway.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coolexplorer.gateway.message.JwtTokenMessage;
import io.coolexplorer.gateway.service.JwtTokenMessageService;
import io.coolexplorer.gateway.topic.JwtTokenTopic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.requestreply.RequestReplyFuture;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
@Service
public class JwtTokenMessageServiceImpl implements JwtTokenMessageService {
    private final ReplyingKafkaTemplate<String, Object, String> jwtTokenReplyingKafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Boolean validateJwtToken(JwtTokenMessage.ValidateMessage message) throws ExecutionException, InterruptedException, TimeoutException, JsonProcessingException {
        LOGGER.debug("topic = {}, payload = {}", JwtTokenTopic.TOPIC_VALIDATE_JWT_TOKEN, message);

        ProducerRecord<String, Object> record = new ProducerRecord<>(JwtTokenTopic.TOPIC_VALIDATE_JWT_TOKEN, message);
        RequestReplyFuture<String, Object, String> replyFuture = jwtTokenReplyingKafkaTemplate.sendAndReceive(record);

        SendResult<String, Object> sendResult = replyFuture.getSendFuture().get(10, TimeUnit.SECONDS);

        LOGGER.debug("SendResult : {}", sendResult.toString());
        ConsumerRecord<String, String> consumerRecord = replyFuture.get(10, TimeUnit.SECONDS);

        JwtTokenMessage.ValidateResultMessage resultMessage = objectMapper.readValue(consumerRecord.value(), JwtTokenMessage.ValidateResultMessage.class);
        return resultMessage.getResult();
    }
}
