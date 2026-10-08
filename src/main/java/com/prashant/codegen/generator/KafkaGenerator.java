package com.prashant.codegen.generator;

import com.prashant.codegen.model.KafkaSpec;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class KafkaGenerator {

    public String GenerateKafkaProducer(
            KafkaSpec kafkaSpec,
            Configuration cfg
    ) throws IOException, TemplateException {

        validateKafkaSpec(kafkaSpec);

        Map<String, Object> data = new HashMap<>();

        data.put(
                "bootstrapServersEnv",
                kafkaSpec.getBootstrapServersEnv()
        );

        data.put(
                "topics",
                kafkaSpec.getTopics()
        );

        var template = cfg.getTemplate("producer.py.ftl");

        StringWriter out = new StringWriter();

        template.process(data, out);

        return out.toString();
    }

    public String GenerateKafkaConsumer(
            KafkaSpec kafkaSpec,
            Configuration cfg
    ) throws IOException, TemplateException {

        validateKafkaSpec(kafkaSpec);

        Map<String, Object> data = new HashMap<>();

        data.put(
                "bootstrapServersEnv",
                kafkaSpec.getBootstrapServersEnv()
        );

        data.put(
                "consumers",
                kafkaSpec.getConsumers()
        );

        var template = cfg.getTemplate("consumer.py.ftl");

        StringWriter out = new StringWriter();

        template.process(data, out);

        return out.toString();
    }

    private void validateKafkaSpec(KafkaSpec kafkaSpec) {

        if (kafkaSpec == null) {
            throw new IllegalArgumentException(
                    "Kafka configuration cannot be null"
            );
        }

        if (kafkaSpec.getBootstrapServersEnv() == null ||
                kafkaSpec.getBootstrapServersEnv().isBlank()) {

            throw new IllegalArgumentException(
                    "Kafka bootstrapServersEnv is required. " +
                            "Example: KAFKA_BOOTSTRAP_SERVERS"
            );
        }
    }
}