package io.coolexplorer.gateway.config;

import io.coolexplorer.gateway.topic.JwtTokenTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaJwtTokenConfig {
    private final String bootStrapAddresses;
    private final String groupId;

    public KafkaJwtTokenConfig(
            @Value("${kafka.bootstrap.addresses}")
                    String bootStrapAddresses,
            @Value("${kafka.consumer.groupId}")
                    String groupId) {
        this.bootStrapAddresses = bootStrapAddresses;
        this.groupId = groupId;
    }

    private Map<String, Object> produceProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootStrapAddresses);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return props;
    }

    private Map<String, Object> consumeProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootStrapAddresses);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    @Bean
    public ProducerFactory<String, Object> jwtTokenProducerFactory() {
        return new DefaultKafkaProducerFactory<>(produceProperties());
    }

    @Bean
    public ConsumerFactory<String, String> jwtTokenConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumeProperties());
    }

    @Bean
    public ReplyingKafkaTemplate<String, Object, String> jwtTokenReplyingKafkaTemplate(
            @Qualifier("jwtTokenProducerFactory") ProducerFactory<String, Object> pf,
            @Qualifier("jwtTokenReplyContainer") KafkaMessageListenerContainer<String, String> container) {
        return new ReplyingKafkaTemplate<>(pf, container);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaJwtTokenTemplate() {
        return new KafkaTemplate<>(jwtTokenProducerFactory());
    }

    @Bean
    public KafkaMessageListenerContainer<String, String> jwtTokenReplyContainer(
            @Qualifier("jwtTokenConsumerFactory") ConsumerFactory<String, String> cf
    ) {
        ContainerProperties containerProperties = new ContainerProperties(JwtTokenTopic.TOPIC_VALIDATE_RESULT_JWT_TOKEN);
        return new KafkaMessageListenerContainer<>(cf, containerProperties);
    }

    @Bean
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>> jwtTokenKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConsumerFactory(jwtTokenConsumerFactory());
        factory.setReplyTemplate(kafkaJwtTokenTemplate());
        return factory;
    }

}
