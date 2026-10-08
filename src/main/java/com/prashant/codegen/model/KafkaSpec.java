package com.prashant.codegen.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KafkaSpec {
    private String bootstrapServersEnv;
    private List<KafkaTopicSpec> topics;
    private List<KafkaConsumerSpec> consumers;
}
