package io.coolexplorer.gateway.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coolexplorer.gateway.message.JwtTokenMessage;
import io.coolexplorer.gateway.service.JwtTokenMessageService;
import io.coolexplorer.gateway.topic.JwtTokenTopic;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.kafka.test.assertj.KafkaConditions.key;

@Slf4j
@Tag("embedded-kafka-test")
@SpringBootTest
@ExtendWith(SpringExtension.class)
@EmbeddedKafka
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.config.location=classpath:application-test.yaml")
public class JwtTokenMessageServiceImplTest {
    @Autowired
    public ObjectMapper objectMapper;

    @Autowired
    public JwtTokenMessageService jwtTokenMessageService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker = new EmbeddedKafkaBroker(
            2,
            true,
            2,
            JwtTokenTopic.TOPIC_VALIDATE_JWT_TOKEN,
            JwtTokenTopic.TOPIC_VALIDATE_RESULT_JWT_TOKEN
    );

    private KafkaMessageListenerContainer<String, String> container;

    private BlockingQueue<ConsumerRecord<String, String>> records;

    @BeforeEach
    void setUp() {
    }

    private void configEmbeddedKafkaConsumer(String topic) {
        Map<String, Object> consumerProperties = new HashMap<>(KafkaTestUtils.consumerProps("gateway", "false", embeddedKafkaBroker));

        DefaultKafkaConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProperties);

        ContainerProperties containerProperties = new ContainerProperties(topic);
        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);

        records = new LinkedBlockingDeque<>();

        container.setupMessageListener((MessageListener<String, String>) record -> {
            LOGGER.debug("test-listener received message={}", record.value());
            records.add(record);
        });
        container.start();

        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
    }

    private Producer<String, Object> configEmbeddedKafkaProducer() {
        Map<String, Object> producerProperties = new HashMap<>(KafkaTestUtils.producerProps(embeddedKafkaBroker));
        return new DefaultKafkaProducerFactory<>(producerProperties, new StringSerializer(), new JsonSerializer<>()).createProducer();
    }

    @Nested
    @DisplayName("JwtToken Validate Message Test")
    class JwtTokenValidateRequestMessageTest {
        @Test
        @DisplayName("Success")
        @Disabled("Cannot make the replying message with Embedded kafka")
        void testRequestMessageForValidatingJwtToken() throws JsonProcessingException, ExecutionException, InterruptedException, TimeoutException {
            configEmbeddedKafkaConsumer(JwtTokenTopic.TOPIC_VALIDATE_JWT_TOKEN);

            JwtTokenMessage.ValidateMessage validateMessage = new JwtTokenMessage.ValidateMessage();
            validateMessage.setJwtToken("test-token");
            String expectedMessage = objectMapper.writeValueAsString(validateMessage);

            JwtTokenMessage.ValidateResultMessage resultMessage = new JwtTokenMessage.ValidateResultMessage();
            resultMessage.setJwtToken("test-token");
            resultMessage.setResult(true);
            String replyMessage = objectMapper.writeValueAsString(resultMessage);

            Producer<String, Object> producer = configEmbeddedKafkaProducer();
            ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(JwtTokenTopic.TOPIC_VALIDATE_RESULT_JWT_TOKEN, replyMessage);
            producerRecord.headers().add(new RecordHeader(KafkaHeaders.REPLY_TOPIC, JwtTokenTopic.TOPIC_VALIDATE_RESULT_JWT_TOKEN.getBytes()));
            producer.send(producerRecord);
            producer.flush();

            jwtTokenMessageService.validateJwtToken(validateMessage);

            ConsumerRecord<String, String> record = records.poll(10, TimeUnit.SECONDS);

            assertThat(record).isNotNull();
            assertThat(record.value()).isEqualTo(expectedMessage);
            assertThat(record).has(key(null));

            producer.close();
        }
    }
}
