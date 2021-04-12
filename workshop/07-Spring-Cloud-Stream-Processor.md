# 07. Lab - SCSt: Stock Quote Processor

## Goal(s)

Consider the Java Functional interfaces, a Processor is a Function as it is both a Consumer and a Supplier.  Processors
are the start of really exploring SCSt as a streaming abstraction (so far, we've just set up the basics of producing and
consuming).  In this lab we will tap into the Stock Quote stream and enrich it with data.

## Create a Processor

Start with [start.spring.io](https://start.spring.io).

Create a Spring Boot application using:

- Lombok
- Cloud Stream
- Spring for Apache Kafka

Or use this [URL](https://start.spring.io/#!type=gradle-project&language=java&platformVersion=2.4.3.RELEASE&packaging=jar&jvmVersion=11&groupId=io.undertree.workshop.scst&artifactId=stock-quote-processor&name=stock-quote-processor&description=Demo%20project%20for%20Spring%20Boot&packageName=io.undertree.workshop.scst&dependencies=lombok,cloud-stream,kafka).

Generate the application, unpack it and import it into your favorite IDE.

## Add a Processor

If you try to start the application now, it will startup and stop as before.  This time, we need to implement a
Processor function.

In the application add two POJOs that will represent the consuming side and another for the supplier side:

```java
@Data
@Builder
@Jacksonized
class StockQuote {
    private final String timestamp;
    private final String symbol;
    private final String price;
}

@Data
@Builder
class EnhancedStockQuote {
    private final String timestamp;
    private final String symbol;
    private final String fullName;
    private final String price;
    private final String lowPrice;
    private final String highPrice;

    static EnhancedStockQuoteBuilder from(final StockQuote stockQuote) {
        return EnhancedStockQuote.builder()
                .timestamp(stockQuote.getTimestamp())
                .symbol(stockQuote.getSymbol())
                .price(stockQuote.getPrice());
    }
}
```

In this scenario we will take in a simple StockQuote and "enrich" it with the company ticker full name, and it's 52 week
highs and lows.  These really aren't streams of data/events as they are fairly static data.  This data could come from
anywhere including a traditional SQL database, a middleware cache or even a compacted Kafka topic!

To simplify the set up for now, we'll hard code values in the processor:

```java
    @Bean
    public Function<Flux<StockQuote>, Flux<EnhancedStockQuote>> enhanceStockQuote() {
        return fluxValue -> fluxValue.map(quote -> EnhancedStockQuote.builder()
                .timestamp(quote.getTimestamp())
                .symbol(quote.getSymbol())
                .fullName("TODO")
                .price(quote.getPrice())
                .highPrice(String.format("%.2f", 300.00))
                .lowPrice(String.format("%.2f", 1.00))
                .build());
    }
```

Finally, configure the bootstrap servers in the `application.yml` (just rename it from the .properties):

```yaml
spring.application.name: "stock-quote-processor"

spring.cloud.stream:
  kafka.binder:
    brokers: kafka1.test.local:9192, kafka2.test.local:9292, kafka3.test.local:9392
    auto-add-partitions: true
    min-partition-count: 3
    replication-factor: 3
```

Review the logs.  There should be a lot more going on now.  Essentially, you are seeing a Producer and Consumer
configuration launching in the same application.

Use kafkacat to review the topics:

```shell
$ kafkacat -b kafka1.test.local:9192 -L
```

You should see these new topics:

```text
topic "enhanceStockQuote-out-0" with 3 partitions:
    partition 0, leader 200, replicas: 200,100,300, isrs: 200,100,300
    partition 1, leader 100, replicas: 100,300,200, isrs: 100,300,200
    partition 2, leader 300, replicas: 300,200,100, isrs: 300,200,100
  topic "enhanceStockQuote-in-0" with 3 partitions:
    partition 0, leader 100, replicas: 100,200,300, isrs: 100,200,300
    partition 1, leader 300, replicas: 300,100,200, isrs: 300,100,200
    partition 2, leader 200, replicas: 200,300,100, isrs: 200,300,100
```

The first problem should be obvious, our consumer isn't using the right topic (destination), so let's tackle that first.

## Consumer Binding

Very much like our last application, this processor has a consumer side that needs to be configured in SCSt bindings.
Add the requisite configuration:

```yaml
spring.cloud.stream:
  bindings:
    enhanceStockQuote-in-0:
      binder: kafka
      group: ${spring.application.name}-enhancer
      destination: stock-quotes
      consumer:
        use-native-decoding: true
  kafka.bindings:
    enhanceStockQuote-in-0:
      consumer.configuration:
        key.deserializer: org.apache.kafka.common.serialization.StringDeserializer
        value.deserializer: org.springframework.kafka.support.serializer.JsonDeserializer

spring.kafka.consumer.properties:
  spring.json.trusted.packages: "io.undertree.workshop.scst"
```

If you start the Processor now and have the Supplier send a few messages, you'll see an error:

```text
Caused by: org.springframework.integration.MessageDispatchingException: Dispatcher has no subscribers
```

This again is fairly straight forward, we just didn't configure the bindings for the Supplier side of the equation.  To
do that, add another block of yaml:

```yaml
spring.cloud.stream:
  bindings:
    enhanceStockQuote-out-0:
      binder: kafka
      destination: stock-quotes.enhanced
      producer:
        use-native-encoding: true
        partition-count: 5
```

Then update the Kafka bindings `spring.cloud.stream.kafka.bindings` for the `enhanceStockQuote-out-0`:

```yaml
spring.cloud.stream:
  kafka.bindings:
    enhanceStockQuote-out-0:
      producer:
        message-key-expression: payload.symbol
        configuration:
          acks: all
          compression.type: snappy
          key.serializer: org.apache.kafka.common.serialization.StringSerializer
          value.serializer: org.springframework.kafka.support.serializer.JsonSerializer
          linger.ms: 100
          batch.size: 10000
```

In total, your `application.yml` should look like this:

```yaml
spring.application.name: "stock-quote-processor"

spring.cloud.function.definition: enhanceStockQuote

spring.cloud.stream:
  bindings:
    enhanceStockQuote-in-0:
      binder: kafka
      group: ${spring.application.name}-enhancer
      destination: stock-quotes
      consumer:
        use-native-decoding: true
    enhanceStockQuote-out-0:
      binder: kafka
      destination: stock-quotes.enhanced
      producer:
        use-native-encoding: true
        partition-count: 5
  kafka.bindings:
    enhanceStockQuote-in-0:
      consumer:
        #reset-offsets: true
        start-offset: earliest
        configuration:
          key.deserializer: org.apache.kafka.common.serialization.StringDeserializer
          value.deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
    enhanceStockQuote-out-0:
      producer:
        message-key-expression: payload.symbol
        configuration:
          acks: all
          compression.type: snappy
          key.serializer: org.apache.kafka.common.serialization.StringSerializer
          value.serializer: org.springframework.kafka.support.serializer.JsonSerializer
          linger.ms: 100
          batch.size: 10000
  kafka.binder:
    brokers: kafka1.test.local:9192, kafka2.test.local:9292, kafka3.test.local:9392
    auto-add-partitions: true
    min-partition-count: 3
    replication-factor: 3

spring.kafka.consumer.properties:
  spring.json.trusted.packages: "io.undertree.workshop.scst"
```

## A Quick Review

This is actually a lot of yaml configuration to digest, so lets break it up and describe each section in a little more
detail:

### spring.cloud.stream.bindings.*

This section contains all the high-level bindings, and their configurations; by default, this will be mapped to named
keys that match the naming convention: `functionName`-`<in|out>`-`<index>` (i.e. the channel name).  Above we create two
of these keys for both sides of the Function.  In each binding, we then map the higher-level configurations for each
considering one is a Consumer, and the other is a Producer.

Some configurations may look Kafkaesque (e.g. `partition-count`) but are actually generalized settings that will
influence the underlying binder to do the right thing.

### spring.cloud.stream.kafka.bindings.*

For each binding in `stream.bindings` that is mapped to Kafka, a parallel block should be entered here.  In these blocks
there are more direct Kafka specific configurations here.  Note, in each case you will have to define either Consumer or
Producer properties.

### spring.cloud.stream.kafka.binder.*

This section is for the general settings for all bindings using the Kafka binder.  You can see by how we've configured
this to always use the same cluster and set some base requirements for partitioning and replication.

For more information, please read this [blog](https://spring.io/blog/2021/02/03/demystifying-spring-cloud-stream-producers-with-apache-kafka-partitions)
as it is essential for understanding the nuances of Kafka configuration in SCSt.

## Run It!

Now that the configuration is complete, we should be able to start the application successfully and start consuming from
the `stock-quotes` topic.  If all is well, you should be able to determine via the log that messages are being processed
as we are logging them.

To confirm that the enriched messsages are also being produced, view it with `kafakat`:

```shell
$ kafkacat -b kafka1.test.local:9192 -C -K " => " -t stock-quotes.enhanced
```

You should see something like this:

```text
...
PFE => {"timestamp":"2021-03-10T17:39:07.956454Z","symbol":"PFE","fullName":"Pfizer Inc.","price":"33.45","lowPrice":"26.41","highPrice":"43.08"}
WBA => {"timestamp":"2021-03-10T17:39:07.956652Z","symbol":"WBA","fullName":"Walgreens Boots Alliance Inc.","price":"47.33","lowPrice":"33.36","highPrice":"55.49"}
INTC => {"timestamp":"2021-03-10T17:39:07.957010Z","symbol":"INTC","fullName":"Intel Corp.","price":"74.83","lowPrice":"43.61","highPrice":"65.11"}
XOM => {"timestamp":"2021-03-10T17:39:07.957047Z","symbol":"XOM","fullName":"Exxon Mobil Corp.","price":"56.46","lowPrice":"30.11","highPrice":"61.61"}
% Reached end of topic stock-quotes.enhanced [0] at offset 7
% Reached end of topic stock-quotes.enhanced [2] at offset 7
% Reached end of topic stock-quotes.enhanced [1] at offset 3
% Reached end of topic stock-quotes.enhanced [3] at offset 6
```

# Extra Credit

- 
