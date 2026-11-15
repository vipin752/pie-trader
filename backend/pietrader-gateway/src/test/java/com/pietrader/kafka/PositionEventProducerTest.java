package com.pietrader.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PositionEventProducer — Java→Python Feedback Loop Tests")
class PositionEventProducerTest {

    @Mock KafkaTemplate<String, String> kafkaTemplate;
    @InjectMocks PositionEventProducer producer;

    @BeforeEach
    void setUp() throws Exception {
        var f = PositionEventProducer.class.getDeclaredField("objectMapper");
        f.setAccessible(true);
        f.set(producer, new ObjectMapper());
    }

    @Test @DisplayName("publishOpen: sends to pie.position.events with OPEN status")
    void publishOpen_sendsToCorrectTopic() {
        producer.publishOpen("NIFTY", "23000 PE", "DOWN", "ORD123");

        verify(kafkaTemplate).send(
            eq("pie.position.events"),
            eq("NIFTY"),
            argThat(json -> json.contains("\"status\":\"OPEN\"")
                         && json.contains("\"symbol\":\"NIFTY\"")
                         && json.contains("\"strike\":\"23000 PE\"")
                         && json.contains("\"orderId\":\"ORD123\""))
        );
    }

    @Test @DisplayName("publishClosed: sends to pie.position.events with CLOSED status")
    void publishClosed_sendsToCorrectTopic() {
        producer.publishClosed("NIFTY", 1500.0);

        verify(kafkaTemplate).send(
            eq("pie.position.events"),
            eq("NIFTY"),
            argThat(json -> json.contains("\"status\":\"CLOSED\"")
                         && json.contains("\"symbol\":\"NIFTY\""))
        );
    }

    @Test @DisplayName("publishOpen: key is the symbol (for Kafka partitioning)")
    void publishOpen_keyIsSymbol() {
        producer.publishOpen("BANKNIFTY", "52000 CE", "UP", "ORD456");
        verify(kafkaTemplate).send(eq("pie.position.events"), eq("BANKNIFTY"), anyString());
    }

    @Test @DisplayName("publishOpen does not throw on serialization")
    void publishOpen_noThrow() {
        assertThatCode(() -> producer.publishOpen("NIFTY", "23000 PE", "DOWN", "P123"))
            .doesNotThrowAnyException();
    }

    @Test @DisplayName("publishClosed does not throw on serialization")
    void publishClosed_noThrow() {
        assertThatCode(() -> producer.publishClosed("NIFTY", -800.0))
            .doesNotThrowAnyException();
    }
}
