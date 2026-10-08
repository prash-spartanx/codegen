<#-- Kafka Consumer Template -->
import os
import json
import logging
import threading
from confluent_kafka import Consumer, KafkaError

<#list consumers as consumer>
class ${consumer.handler}Consumer:
    def __init__(self):
        """
        Kafka Consumer for topic '${consumer.topic}'.
        Intent: ${consumer.intent}
        """
        self.running = False
        self.thread = None

        conf = {
            'bootstrap.servers': os.getenv("${bootstrapServersEnv}"),
            'group.id': "${consumer.groupId}",
            'auto.offset.reset': 'earliest'
        }

        self.consumer = Consumer(conf)
        self.consumer.subscribe(["${consumer.topic}"])
        logging.info(f"Consumer initialized for topic: ${consumer.topic}")

    def start(self):
        """Start background consumer thread."""
        if self.thread is None or not self.thread.is_alive():
            self.running = True
            self.thread = threading.Thread(target=self._consume_loop, daemon=True)
            self.thread.start()
            logging.info("Consumer thread started.")

    def stop(self):
        """Stop consumer thread and close connection."""
        self.running = False
        if self.thread:
            self.thread.join()
        if self.consumer:
            self.consumer.close()
        logging.info("Consumer thread stopped.")

    def _consume_loop(self):
        """Continuous loop to poll messages."""
        while self.running:
            msg = self.consumer.poll(timeout=1.0)
            if msg is None:
                continue
            if msg.error():
                if msg.error().code() == KafkaError._PARTITION_EOF:
                    continue
                else:
                    logging.error(f"Consumer error: {msg.error()}")
                    break

            try:
                message = json.loads(msg.value().decode('utf-8'))
                # Optional: filter based on payload fields
                # Example: if message.get("target") != "expected-service": continue
                self.handle(message)
            except Exception as e:
                logging.error(f"Error processing message: {e}")

    def handle(self, message: dict):
        """
        Handler stub for intent '${consumer.intent}'.
        Replace with actual business logic.
        """
        logging.info(f"Received message: {message}")
        # TODO: implement handler logic here
</#list>
