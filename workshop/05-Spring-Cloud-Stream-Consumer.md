# 05. Lab - SCSt: Stock Quote Consumer

## Goal(s)

In this section we will create a sample Consumer using the new SCSt function model and have it consume from a stream of
events and are stored in Kafka.

## Create a SCSt Consumer Starter

Like the earlier lab, go to [start.spring.io](https://start.spring.io).

Create a Spring Boot application using:

- Lombok
- Cloud Stream
- Spring for Apache Kafka 

Or use this [URL](https://start.spring.io/#!type=gradle-project&language=java&platformVersion=2.4.3.RELEASE&packaging=jar&jvmVersion=11&groupId=io.undertree.workshop.scst&artifactId=stock-quote-consumer&name=stock-quote-consumer&description=Demo%20project%20for%20Spring%20Boot&packageName=io.undertree.workshop.scst&dependencies=lombok,cloud-stream,kafka).

Generate the application, unpack it and import it into your favorite IDE.  Once imported, review the `build.gradle` file
and look at the dependencies.

## Run It!

Again the application starts and stops.  We need to start consuming something.  Let's add a very basic Consumer function
that takes a Flux of Strings:

```java
    @Bean
    Consumer<Flux<String>> consumer() {
        return stringFlux -> stringFlux.subscribe(System.out::println);
    }
```

If we run the application now, we'll see that it fails to fully start up since the default Kafka broker is not
available.  We need to configure the bootstrap servers in the `application.yml` (rename it if you haven't already):

```yaml
spring.application.name: "stock-quote-consumer"

spring.cloud.stream:
  kafka.binder:
    brokers: kafka1.test.local:9192, kafka2.test.local:9292, kafka3.test.local:9392
```

With this configuration applied we'll now be able to start a Consumer and connect it to our local Kafka.  However, we're
now consuming on a topic that doesn't make sense as it is just the default binding name: `consumer-in-0`.  We really
want to be consuming a Flux of StockQuotes so lets start adding the necessary code:

## StockQuote POJO

Add a Lombok Data POJO:

```java
@Data
@Jacksonized
class StockQuote {
    private final String timestamp;
    private final String symbol;
    private final String price;
}
```

Update the Consumer function to reflect this new object:

```java
    @Bean
    Consumer<Flux<StockQuote>> simpleStockQuote() {
        return quoteFlux -> quoteFlux.subscribe(System.out::println);
    }
```

Finally, set up the Consumer bindings, so we get the data from the right topic:

```yaml
spring.cloud.stream:
  bindings:
    simpleStockQuote-in-0:
      group: ${spring.application.name}-logger
      destination: stock-quotes
      consumer:
        use-native-encoding: true
  kafka.binder:
    brokers: kafka1.test.local:9192, kafka2.test.local:9292, kafka3.test.local:9392
    consumer-properties:
      key.deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value.deserializer: org.springframework.kafka.support.serializer.JsonDeserializer

spring.kafka.consumer.properties:
  spring.json.trusted.packages: "io.undertree.workshop.scst"
```

^ Make special note of the `spring.json.trusted.packages`, this should be set to the same package name as the one in the
Supplier (you may have to refactor the code a bit to get this to work).  This again, is a behavior in Spring intended to
reduce JSON serialization errors/vulnerabilities in Jackson.  The Spring Json deserializer is very strict in this case
wants to ensure it is the same "class".

You can disable this behavior but for this workshop we'll just stick to the happy path as it is expected that you will
likely consider switching to something like [Avro](https://docs.confluent.io/platform/current/schema-registry/serdes-develop/serdes-avro.html)
instead of JSON.

Now run the Supplier app and Consumer app together, you should start seeing StockQuotes in the log messages for the
Consumer.

## On Fluxes...

When building a consumer using a Flux you cannot parallelize the work, meaning you may only have 1 thread Consuming via
the Flux.  This is because the core of the Reactive model is designed to be single-threaded and not leverage thread
pools.  In theory, this is more efficient since there is far less context switching between threads and means that L1/L2
caches are more likely to "stay hot".

It is important to think about this in context with the targeted deployment platform.  Is this workload really isolated
or is sharing kernel resources with many other process (like Docker or Kubernetes)?  If so, the L1/L2 caching efficiency
gains may be lessened as the Linux kernel is handling scheduling of each process independently.

Additionally, it may be important to tune CPU shares for the process so that the process is neither compute bound nor
leaving compute idle (this kind of tuning is outside the scope of this workshop).

## Native Encoding

You may have noticed this block in the Consumer (and a similar one in the Supplier):

```yaml
  consumer:
    use-native-encoding: true
```

This is an important flag to consider.  SCSt has its own built-in encoding/decoding of data as a higher-level
abstraction on top of the underlying binder.  In some cases, you may prefer this but in the case of Kafka, we've decided
to disable SCSt encoding in favor of Kafka's.

Essentially, in the earliest examples of the Supplier, we did allow SCSt to do the JSON SerDes and the binder was using
ByteArray serialization.  Again because Kafka has several SerDes options, you will likely want to always use native
encoding.

# Extra Credit

- Modify the Consumer to only output Stock Quotes over $100.
