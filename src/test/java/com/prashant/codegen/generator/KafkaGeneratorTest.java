package com.prashant.codegen.generator;

import com.prashant.codegen.model.KafkaConsumerSpec;
import com.prashant.codegen.model.KafkaSpec;
import com.prashant.codegen.model.KafkaTopicSpec;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KafkaGeneratorTest {

    private KafkaGenerator kafkaGenerator;
    private Configuration cfg;

    @BeforeEach
    void setUp() throws Exception {
        // Configure FreeMarker to load templates from resources/templates
        cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setClassForTemplateLoading(this.getClass(), "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

        kafkaGenerator = new KafkaGenerator();
    }

    @Test
    void testGenerateKafkaProducer() throws Exception {
        // Arrange
        KafkaTopicSpec topic = new KafkaTopicSpec();
        topic.setName("orders");

        KafkaSpec spec = new KafkaSpec();
        spec.setBootstrapServersEnv("KAFKA_BOOTSTRAP");
        spec.setTopics(List.of(topic));

        // Act
        String result = kafkaGenerator.GenerateKafkaProducer(spec, cfg);

        // Assert
        assertNotNull(result, "Generated producer code should not be null");
        assertTrue(result.contains("orders"), "Producer code should contain topic name");
        assertTrue(result.contains("KAFKA_BOOTSTRAP"), "Producer code should contain bootstrap env");
        assertTrue(result.contains("def send_orders"), "Producer code should have send function for topic");
    }

    @Test
    void testGenerateKafkaConsumer() throws Exception {
        // Arrange
        KafkaConsumerSpec consumer = new KafkaConsumerSpec();
        consumer.setTopic("payments");
        consumer.setGroupId("payment-group");
        consumer.setHandler("PaymentHandler");
        consumer.setIntent("process payments");

        KafkaSpec spec = new KafkaSpec();
        spec.setBootstrapServersEnv("KAFKA_BOOTSTRAP");
        spec.setConsumers(List.of(consumer));

        // Act
        String result = kafkaGenerator.GenerateKafkaConsumer(spec, cfg);
        System.out.println(result);
        // Assert
        assertNotNull(result, "Generated consumer code should not be null");
        assertTrue(result.contains("payments"), "Consumer code should contain topic name");
        assertTrue(result.contains("payment-group"), "Consumer code should contain groupId");
        assertTrue(result.contains("PaymentHandlerConsumer"), "Consumer class should be generated with handler name");
        assertTrue(result.contains("process payments"), "Consumer code should include intent");
    }
}
