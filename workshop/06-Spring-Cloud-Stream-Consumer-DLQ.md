# 06. Lab - SCSt: Stock Quote Consumer DLQ/DLT

## Goal(s)

In the real world things go wrong, and the Consumer may fail to read, parse or handle all messages correctly.  We need
to know how to configure and manage these kinds of failures.  SCSt provides a Dead-Letter Queue abstraction for all
streaming platforms (even if they don't technically use a Queue terminology).

In this lab we will establish a DLQ configuration for the binder that handles common exception situations and will wrap
and publish the message to this DLQ.

## Enhancing the SCSt Configuration

Instead of using the default Kafka binder settings, let's make a more specific configuration for the specific binding.  
This is a better approach as it will allow for multiple bindings to be configured differently.

```yaml
spring.cloud.stream:
  bindings:
    simpleStockQuote-in-0:
      group: ${spring.application.name}-logger
      destination: stock-quotes
      consumer:
        use-native-decoding: true
  kafka.bindings:
    simpleStockQuote-in-0:
      consumer:
        #reset-offsets: true
        start-offset: earliest
        configuration:
          key.deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
          value.deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
  kafka.binder:
    brokers: kafka1.test.local:9192, kafka2.test.local:9292, kafka3.test.local:9392
    auto-add-partitions: true

spring.kafka.consumer.properties:
  spring.json.trusted.packages: "io.undertree.workshop.scst"
  spring.deserializer.key.delegate.class: org.apache.kafka.common.serialization.StringDeserializer
  spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
```

In addition to breaking out the configuration we've also customized the key / value serializer to use the Spring
`ErrorHandlingDeserializer` (as in the previous workshop).  Then we configure the delegate deserializers.  This will
give us the ability to introduce a custom handler for the class of serialization errors that might occur if the message
cannot be parsed.

Run the application and verify the configuration is still working.

## Error Handling

There are two main categories of errors that we want to consider as a consumer: errors that occur outside the function
(either before the invocation or after) and errors that occur inside the method (usually some type of code or even a 
business logic exception).

SCSt gives us an abstraction that can handle the later case, meaning a way of configuring a Dead-Letter Queue for any
exceptions thrown from our code.  SCSt however does not handle the former issues and we'll have to fall back to Spring's
Kafka adapter to handle those (we did that in the first workshop).

Since this workshop is focused on SCSt, let's start there.  Add this additional configuration block to the Kafka
bindings:

```yaml
  kafka.bindings:
    simpleStockQuote-in-0:
      consumer:
        #reset-offsets: true
        start-offset: earliest
        configuration:
          key.deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
          value.deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
        max-attempts: 1
        enable-dlq: true
        dlq-name: stock-quotes-consumer-logger.dlq
        dlq-partitions: 5
        dlq-producer-properties:
          acks: all
          compression.type: snappy
          configuration:
            key.serializer: org.apache.kafka.common.serialization.StringSerializer
            value.serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

This will enable a DLQ for exceptions thrown from the consumer function.  To see this working, make a modification to
Consumer to throw an exception anytime it sees the `AAPL` symbol:

```java
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
```

Note: we've intentionally converted the consumer from a Flux to be non-reactive.  This is because it is very hard to
throw an exception from a Flux without breaking the stream.  Since we're just simulating failed conditions, this
approach just keeps it simple.

Also, we've signaled to SCSt that the DLQ should have 5 partitions (keeping with the topic we are consuming from), this
configuration requires that we add a custom @Bean to handle partition assignment:

```java
    @Bean
    public DlqPartitionFunction partitionFunction() {
        return (group, record, ex) -> record.partition();
    }
```

Here we are just assigning the un-processed message to the same partition that it came from.

With this we should be able to start the Consumer and Supplier and see the exception and validate that the `AAPL` stock
quote goes to the DLQ (presumable at some point we'll have a reprocessing service).

To validate that the messages are arriving, use `kafkakat`:

```shell
$ kafkacat -b kafka1.test.local:9192 -C -t stock-quotes-consumer-logger.dlq
```

## SerDes Errors

Since the SCSt Kafka binder implementation does not capture serdes errors, we need to set up our own customized solution
and configure it.

First, let's use the Topic Builder to create the topic we're going to use for this class of errors:

```java
    @Bean
    public NewTopic stockQuoteDlt() {
        return TopicBuilder.name("stock-quote-consumer-logger.dlt")
                .partitions(5)
                .replicas(3)
                .build();
    }
```

We want to use a different topic name than the other errors since these are messages that couldn't be parsed and may
contain anything.  We'll let this partition configuration mirror the main stock-quote topic in partition count and
replicas.

Then we need to create a `ListenerContainerCustomizer` which will let us setup a customized SeekToCurrentErrorHandler:

```java
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
```

This is similar to our initial Spring Kafka workshop.  With the `DeadLetterPublishingRecoverer` applied the final piece
of the puzzle is to configure the bootstrap-servers:

```yaml
spring.kafka:
  bootstrap-servers: kafka1.test.local:9192, kafka2.test.local:9292, kafka3.test.local:9392
```

This is now effectively a duplicate configuration since we already had a bootstrap-servers list in the Kafka binder.
This highlights that we're working outside the standard SCSt configuration and are functionally working directly with
the Spring Kafka library.

Our final yaml configuration should look something like this:

```yaml
spring.application.name: "stock-quote-consumer"

spring.cloud.stream:
  bindings:
    simpleStockQuote-in-0:
      group: ${spring.application.name}-logger
      destination: stock-quotes
      consumer:
        use-native-decoding: true
  kafka.bindings:
    simpleStockQuote-in-0:
      consumer:
        #reset-offsets: true
        start-offset: earliest
        configuration:
          key.deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
          value.deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
        max-attempts: 1
        enable-dlq: true
        dlq-name: stock-quotes-consumer-logger.dlq
        dlq-partitions: 5
        dlq-producer-properties:
          acks: all
          compression.type: snappy
          configuration:
            key.serializer: org.apache.kafka.common.serialization.StringSerializer
            value.serializer: org.springframework.kafka.support.serializer.JsonSerializer
  kafka.binder:
    #brokers: kafka1.test.local:9192, kafka2.test.local:9292, kafka3.test.local:9392
    auto-add-partitions: true
    auto-create-topics: true

spring.kafka:
  bootstrap-servers: kafka1.test.local:9192, kafka2.test.local:9292, kafka3.test.local:9392

spring.kafka.consumer.properties:
  spring.json.trusted.packages: "io.undertree.workshop.scst"
  spring.deserializer.key.delegate.class: org.apache.kafka.common.serialization.StringDeserializer
  spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
```

With this verified, re-run the Consumer and confirm that it is still receiving messages from the stock-quotes topic,
and occasionally publishing the `AAPL` stock error to the DLQ.  One last step is to publish a poison message into the
stream and see what happens:

Set up a console tab to view the DLT topic:

```shell
$ kafkacat -b kafka1.test.local:9192 -C -f "key=[%k] -> value=[%s] with %h\n" -t stock-quote-consumer-logger.dlt
```

Submit some junk messages with:

```shell
$ kafkacat -b kafka1.test.local:9192 -P -t stock-quotes
```

You should see a stack trace exception, but the consumer should continue to run and accept additional "good" messages.
You should also have noticed that the new topic received the poison message.  Notice the headers also include the stack
trace of the original exception!

# Extra Credit

- See if you can restore the Flux behavior but preserve the failure DLQ publishing on the `AAPL` stock.

