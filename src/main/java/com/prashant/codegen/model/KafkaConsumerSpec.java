package com.prashant.codegen.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KafkaConsumerSpec {
    private String topic;
    private String groupId;
    private String handler;
    private String intent;
}
