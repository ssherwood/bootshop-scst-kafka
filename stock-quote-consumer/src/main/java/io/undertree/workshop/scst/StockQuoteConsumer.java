package io.undertree.workshop.scst;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.binder.kafka.utils.DlqPartitionFunction;
import org.springframework.cloud.stream.config.ListenerContainerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.messaging.Message;
import org.springframework.util.backoff.FixedBackOff;

import java.util.function.Consumer;

@Slf4j
@SpringBootApplication
public class StockQuoteConsumer {

    public static void main(String[] args) {
        SpringApplication.run(StockQuoteConsumer.class, args);
    }

    @Bean
    Consumer<Message<StockQuote>> simpleStockQuote() {
        return msg -> {
            if ("AAPL".equals(msg.getPayload().getSymbol())) {
                // generate a fake error to show the DLQ handling for business logic
                throw new IllegalStateException("Something is wrong with AAPL!");
            } else {
                log.info(msg.toString());
            }
        };
    }

    @Bean
    public DlqPartitionFunction partitionFunction() {
        return (group, record, ex) -> record.partition();
    }

    @Bean
    public NewTopic stockQuoteDlt() {
        return TopicBuilder.name("stock-quote-consumer-logger.dlt")
                .partitions(5)
                .replicas(3)
                .build();
    }

    // refs:
    // https://github.com/spring-cloud/spring-cloud-stream/issues/2002
    // https://stackoverflow.com/questions/62270955/understanding-spring-cloud-stream-kafka-and-spring-retry
    @Bean
    public ListenerContainerCustomizer<AbstractMessageListenerContainer<?, ?>> customizer(KafkaOperations<?, ?> template) {
        return (container, dest, group) ->
                container.setErrorHandler(new SeekToCurrentErrorHandler(
                        new DeadLetterPublishingRecoverer(template,
                                (record, exception) -> {
                                    log.error(String.format("An error occurred with record: %s", record), exception);
                                    return new TopicPartition(group + ".dlt", record.partition());
                                }),
                        new FixedBackOff(1500, 3)));
    }
}

@Data
@Jacksonized
@Builder
@AllArgsConstructor(staticName = "of")
class StockQuote {
    private final String timestamp;
    private final String symbol;
    private final String price;
}