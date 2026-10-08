package com.prashant.codegen.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KafkaTopicSpec {
    private String name;
    private int partitions;
    private Map<String,String> schema;

}
