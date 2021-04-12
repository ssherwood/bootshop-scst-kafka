# 04. Lab - SCSt: Producer Advanced Topics

## Goal(s)

In our last lab we created a basic SCSt Producer using the Kafka Binder.  The defaults however don't provide for the
types of configuration options that we normally want to use with a Kafka cluster.  In this lab we will dig deeper into
SCSt configurations and additional options for exposing more capabilities.

## Partition Keys

In Kafka the data is partitioned by hashing the key and using a modulus function against the number of partitions.  This
default usually balances data well across the cluster but also helps ensure order guarantees on events having the same
key.  In our previous lab we noticed that there was no key at all and, even if we had more partitions, data would
randomly be assigned a partition.

One way to introduce a key is to wrap the Supplier response in a Message.  Let's modify the implementation to do that:

```java
    @PollableBean
    Supplier<Flux<Message<StockQuote>>> simpleStockQuote() {
        return () -> {
            // update each stock entry with a now randomized price based on the last one...
            STOCKS_MAP.entrySet().forEach(e -> e.setValue(randomizePrice(e.getValue(), 0.02)));

            return Flux.fromStream(
                    STOCKS_MAP.entrySet().stream()
                            .map(e -> MessageBuilder
                                    .withPayload(new StockQuote.StockQuoteBuilder()
                                            .symbol(e.getKey())
                                            .price(String.format("%.2f", e.getValue()))
                                            .build())
                                    .setHeader(KafkaHeaders.MESSAGE_KEY, e.getKey().getBytes(StandardCharsets.UTF_8))
                                    .build())
            );
        };
    }
```

With the `MessageBuilder` we can supply the key via a special header, and the binder will leverage that to produce the
Kafka message with the right key.  

You can verify this by configuring `kafkacat` to also display the key:

```shell
$ kafkacat -b kafka1.test.local:9192 -C -K " => " -t simpleStockQuote-out-0
```

Now if we had multiple partitions, the data for the same symbol would always be placed in the same partition.  This is
more in line with what we will want in a more polished solution.

## Additional Settings

At first, the SCSt configuration properties can be a bit daunting.  We've avoided too much direct configuration
so far for this reason.

Now is a good time to start introducing the core concepts and how to apply them:

### Bindings

Every SCSt application can support one or more Bindings that map from the functional interfaces to one or more underlying
binders.  To configure our binding, apply this to the `application.yml`

```yaml
spring.cloud.stream:
  bindings:
    simpleStockQuote-out-0:
      binder: kafka
      destination: stock-quotes
      producer:
        use-native-encoding: true
        partition-count: 5
```

Under the `bindings` qualifier, we can specify each binding in the application.  By default the name of the binding is
the `<function>-<in|out>-<index>`.  Our sample applications binding name is `simpleStockQuote-out-0`.

We can customize the high-level SCSt options for each binding this way.  Note, this is a higher-level abstraction that
just Kafka, so be careful not to assume that these options may not map directly to your understanding on Kafka.

If you have more than one binder (for example RabbitMQ and Kafka), you can use the `binder` configuration as seen above
to differentiate which one applies to what binding.  In our sample application we are only using Kafka so this
configuration is not technically required, it is here as an example.

The `destination` is what is used to over-ride the default Kafka topic name.

We also can define `producer` specific configurations.  In this example, we are overriding the default behavior of SCSt
and allowing Kafka native encoding of key/value to be preferred.  We are also telling SCSt that the producer should have
5 "partitions".  With the Kafka binder this will be used to determine how many partitions to create if the topic is to
be auto-created.

### Kafka Bindings

Another section can be added below that provide the more direct Kafka binding configurations:

```yaml
spring.cloud.stream:
  kafka.bindings:
    simpleStockQuote-out-0:
      producer:
        message-key-expression: payload.symbol
```

Here we are using a `message-key-expression` that will help the Kafka binder to know how to extract the correct value
for the message key (with this configuration we don't need to wrap the Supplier with a Message anymore!).  This
expression can be any valid [SPEL](https://docs.spring.io/spring-integration/docs/current/reference/html/spel.html)
expression.

You can review more options in the [docs](https://docs.spring.io/spring-cloud-stream-binder-kafka/docs/3.0.10.RELEASE/reference/html/spring-cloud-stream-binder-kafka.html#kafka-producer-properties).

### Kafka Binder

Finally, the Kafka binder itself (we've already briefly touched on this already to configure the bootstrap servers):

```yaml
spring.cloud.stream:
  kafka.binder:
    brokers: kafka1.test.local:9192, kafka2.test.local:9292, kafka3.test.local:9392
    auto-add-partitions: true
    min-partition-count: 3
    replication-factor: 3
    producer-properties:
      acks: all
      compression.type: snappy
      key.serializer: org.apache.kafka.common.serialization.StringSerializer
      value.serializer: org.springframework.kafka.support.serializer.JsonSerializer
      linger.ms: 100
      batch.size: 10000
```

We set `auto-add-partitions` to true here for demo purposes only and to support defaults for partition count and
replication factor.

The `producer-properties` should be familiar from the previous workshop.

## Update the Supplier

Since we've resolved the partition key issue, we can go back to the Supplier and simplify the code a bit:

```java
    @PollableBean
    Supplier<Flux<StockQuote>> simpleStockQuote() {
        return () -> {
            // update each stock entry with a now randomized price based on the last one...
            STOCKS_MAP.entrySet().forEach(e -> e.setValue(randomizePrice(e.getValue(), 0.02)));

            return Flux.fromStream(
                    STOCKS_MAP.entrySet().stream()
                            .map(e -> new StockQuote.StockQuoteBuilder()
                                    .symbol(e.getKey())
                                    .price(String.format("%.2f", e.getValue()))
                                    .build())
            );
        };
    }
```

Finally, time to restart the application and see if everything is working.

Assuming the application starts correctly, use `kafkacat` to validate that the `stock-quotes` topic was created and
configured correctly:

```shell
$ kafkacat -b kafka1.test.local:9192 -L -t stock-quotes
```

You should see 5 partitions and 3 replicas for each:

```text
Metadata for stock-quotes (from broker 100: kafka1.test.local:9192/100):
 3 brokers:
  broker 200 at kafka2.test.local:9292
  broker 100 at kafka1.test.local:9192 (controller)
  broker 300 at kafka3.test.local:9392
 1 topics:
  topic "stock-quotes" with 5 partitions:
    partition 0, leader 300, replicas: 300,200,100, isrs: 300,200,100
    partition 1, leader 200, replicas: 200,100,300, isrs: 200,100,300
    partition 2, leader 100, replicas: 100,300,200, isrs: 100,300,200
    partition 3, leader 300, replicas: 300,100,200, isrs: 300,100,200
    partition 4, leader 200, replicas: 200,300,100, isrs: 200,300,100
```

From now on, the application will publish to this topic.

## Extra Credit

- Read this [blog](https://spring.io/blog/2021/02/03/demystifying-spring-cloud-stream-producers-with-apache-kafka-partitions)
  on _Demystifying Spring Cloud Stream producers with Apache Kafka partitions_.
