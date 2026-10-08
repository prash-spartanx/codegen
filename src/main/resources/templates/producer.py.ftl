<#-- Kafka Producer Template -->

import os
import json

from confluent_kafka import Producer


# Configure the Kafka producer
bootstrap_servers = os.getenv("${bootstrapServersEnv}")

if not bootstrap_servers:
    raise RuntimeError(
        "${bootstrapServersEnv} environment variable is not configured"
    )

conf = {
    "bootstrap.servers": bootstrap_servers
}

producer = Producer(conf)


<#-- Generate one send function per topic -->
<#list topics as topic>

def send_${topic.name}(message: dict):
    """
    Send a JSON message to topic '${topic.name}'.

    Partitions: ${topic.partitions}

    Expected schema:
    <#list topic.schema?keys as field>
        - ${field}: ${topic.schema[field]}
    </#list>
    """

    # Validate required fields
    <#list topic.schema?keys as field>
    if "${field}" not in message:
        raise ValueError("Missing required field: ${field}")
    </#list>

    producer.produce(
        "${topic.name}",
        json.dumps(message).encode("utf-8")
    )

    producer.flush()

</#list>