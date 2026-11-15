package com.pietrader.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
public class KafkaConfig {
    @Bean public NewTopic marketTicksTopic()     { return TopicBuilder.name("pie.market.ticks").partitions(3).replicas(1).build(); }
    @Bean public NewTopic analyticsResultsTopic(){ return TopicBuilder.name("pie.analytics.results").partitions(3).replicas(1).build(); }
    @Bean public NewTopic tradeEventsTopic()     { return TopicBuilder.name("pie.trade.events").partitions(3).replicas(1).build(); }
    @Bean public NewTopic positionEventsTopic()  { return TopicBuilder.name("pie.position.events").partitions(1).replicas(1).build(); }
    @Bean public NewTopic squareOffTopic()       { return TopicBuilder.name("pie.squareoff.signals").partitions(1).replicas(1).build(); }
    @Bean public NewTopic alertsTopic()          { return TopicBuilder.name("pie.alerts").partitions(1).replicas(1).build(); }
}
