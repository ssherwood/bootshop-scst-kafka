# 03. Lab - SCSt: Stock Quote Supplier

## Goal(s)

In this section we will create a sample Producer using the new SCSt function model and have it create a stream of
Stock Quote events and have them stored into Apache Kafka.

## Create a SCSt Supplier Starter

Like most labs, start with [start.spring.io](https://start.spring.io).

Create a Spring Boot application using:

- Lombok
- Cloud Stream

Or simply use this [URL](https://start.spring.io/#!type=gradle-project&language=java&platformVersion=2.4.3.RELEASE&packaging=jar&jvmVersion=11&groupId=io.undertree.workshop.scst&artifactId=stock-quote-supplier&name=stock-quote-supplier&description=Demo%20project%20for%20Spring%20Boot&packageName=io.undertree.workshop.scst&dependencies=lombok,cloud-stream).

Generate the application, unpack it and import it into your favorite IDE.  Once imported, review the `build.gradle` file
and look at the dependencies.

## Run It!

If you run the application now without any modifications, you'll see a lot of new messages logged to the console and
then the process immediately shuts down. This is because we don't have enough logic and configuration ready for SCSt to
actually run it.

If you review the logs, you'll see a section like this:

```text
...
2021-03-01 10:40:01.824  INFO 82953 --- [           main] faultConfiguringBeanFactoryPostProcessor : No bean named 'errorChannel' has been explicitly defined. Therefore, a default PublishSubscribeChannel will be created.
2021-03-01 10:40:01.827  INFO 82953 --- [           main] faultConfiguringBeanFactoryPostProcessor : No bean named 'taskScheduler' has been explicitly defined. Therefore, a default ThreadPoolTaskScheduler will be created.
2021-03-01 10:40:01.829  INFO 82953 --- [           main] faultConfiguringBeanFactoryPostProcessor : No bean named 'integrationHeaderChannelRegistry' has been explicitly defined. Therefore, a default DefaultHeaderChannelRegistry will be created.
...
```

We are getting a lot of default behavior but nothing that sticks enough for SCSt to stay resident.

First add a simple `Supplier` (we'll use a reactive `Flux` wrapper here as well):

```java
    @Bean
    public Supplier<Flux<String>> stocks() {
        return () -> Flux.fromIterable(List.of("AAPL", "UNH", "HD", "GS"));
    }
```

If we start the application again, we should get an error:

```text
...
org.springframework.context.ApplicationContextException: Failed to start bean 'outputBindingLifecycle'; nested exception is java.lang.IllegalArgumentException: A default binder has been requested, but there is no binder available
...
```

We haven't provided a Binder library for SCSt to use for connecting this Supplier to.  Since we will be using Kafka,
let's add the binder to the dependencies:

```text
implementation 'org.springframework.cloud:spring-cloud-stream-binder-kafka'
```

Refresh your dependencies and restart the application again.  If this is the only 

This time the applications will start successfully but will start generating errors (unless you already have a Kafka running):

```text
...
2021-03-01 10:54:21.913  WARN 84348 --- [| adminclient-1] org.apache.kafka.clients.NetworkClient   : [AdminClient clientId=adminclient-1] Connection to node -1 (localhost/127.0.0.1:9092) could not be established. Broker may not be available.
...
```

## Docker Compose for Kafka

You can use the Docker Compose file we used before, or the one [bundled](../docker/docker-compose.yml) with this workshop.

Put the `compose.yml` file in a folder called `docker` in your project and run it:

```shell
$ docker-compose up -d
```

NOTE: if you see the error: `WARNING: The KAFKA_DATA variable is not set. Defaulting to a blank string.`, export a
variable to the folder you want to save the Kafka logs and run docker-compose again:

```shell
$ export $KAFKA_DATA=<your path>
```

Run the docker-compose `ps` command to validate that the Kafka cluster is up and running:

```shell
$ docker-compose ps
```

You should see something like this:

```text
❯ docker-compose ps
       Name                     Command               State                          Ports                        
------------------------------------------------------------------------------------------------------------------
docker_kafka1_1      /opt/bitnami/scripts/kafka ...   Up      0.0.0.0:9192->9092/tcp                              
docker_kafka2_1      /opt/bitnami/scripts/kafka ...   Up      0.0.0.0:9292->9092/tcp                              
docker_kafka3_1      /opt/bitnami/scripts/kafka ...   Up      0.0.0.0:9392->9092/tcp                              
docker_zookeeper_1   /opt/bitnami/scripts/zooke ...   Up      0.0.0.0:2181->2181/tcp, 2888/tcp, 3888/tcp, 8080/tcp
```

NOTE: If you are having problems with this setup, please refer to the section in the [previous workshop](https://gitlab.com/ssherwood/demo-kafka-workshop/-/blob/50eea8ae0cb1628bd537d9c26a2bb93089a5c71f/workshop/09-Lab-Docker-Compose.md).

Finally, since we are running our cluster on non-default ports, we need to configure SCSt for the boostrap servers in
the `application.yml` file (rename if it is still a .properties file):

```yaml
spring.cloud.stream:
  kafka:
    binder:
      brokers: kafka1.test.local:9192, kafka2.test.local:9292, kafka3.test.local:9392
```

^ Remember back to the original Kafka workshop, we defined local `kafka1`, `kafka2` and `kafka3` in our `/etc/hosts`
file like this (with the address being your Docker host IP):

```text
# for Kafka
172.17.0.1      kafka1.test.local
172.17.0.1      kafka2.test.local
172.17.0.1      kafka3.test.local
```

## Launch the Application Again

With these final configuration in place, we can restart the application.  It should start up without any explicit
errors this time.

Take a few minutes to review the log output.  You should notice a few interesting things going on now.

- The default binder is set up as Kafka
- The default outbound topic is going to be called `stocks-out-0`:
  - ^ This name is the default naming convention of SCSt using the function name, in/out and an index

```text
2021-03-01 11:20:30.318  INFO 94641 --- [           main] o.s.c.s.binder.DefaultBinderFactory      : Creating binder: kafka
2021-03-01 11:20:30.395  INFO 94641 --- [           main] o.s.c.s.binder.DefaultBinderFactory      : Caching the binder: kafka
2021-03-01 11:20:30.395  INFO 94641 --- [           main] o.s.c.s.binder.DefaultBinderFactory      : Retrieving cached binder: kafka
2021-03-01 11:20:30.429  INFO 94641 --- [           main] o.s.c.s.b.k.p.KafkaTopicProvisioner      : Using kafka topic for outbound: stocks-out-0
```

This time we are letting SCSt create the Kafka Topic using the AdminClient configuration just to get things up and
running - eventually we'll want to override those configurations with our own.

First lets verify that we are even getting anything into Kafka:

```shell
$ kafkacat -b kafka1.test.local:9192 -C -t stocks-out-0
```

You'll see the stocks we created, possibly repeating if you've restarted the application multiple times.

Let's liven it up a bit and make it appear a bit more dynamic, convert the Supplier `@Bean` to a pollable bean (a
pollable bean is one that will be re-invoked on a periodic basis):

```java
    @PollableBean
    public Supplier<Flux<String>> stocks() {
        return () -> Flux.fromIterable(List.of("AAPL", "UNH", "HD", "GS"));
    }
```

Now, when you restart the application the stocks() method will be invoked on the default polling interval which is every
second.  You should be able to see this if you've left `kafkacat` running in the background and listening for
events.

We can override the default poller configuration and provide our own (this one will delay the first call and then call
every 5 seconds afterwards):

```yaml
spring.cloud.stream:
  poller:
    initial-delay: 1000
    fixed-delay: 5000
```

There are many types of use cases where you'll use this kind of polling mechanism associated with a Supplier.

Finally, restart the application and verify your changes are taking effect.

## Some SCSt Observations...

If you dig a little deeper into the default behavior of the Kafka binder, you'll probably notice a few things that
you'll want to override.  Use `kafkacat` to get the metadata about the topic:

```shell
$ kafkacat -b kafka1.test.local:9192 -L -t stocks-out-0
```

- There is only 1 partition
- There is only 1 replica (itself)
- The message is generated without a key
- The topic name is auto generated with a `<method>-out-0` naming pattern
- The message is serialized with ByteArraySerializer

We'll leave these configurations alone for now but just remember that these are important to override.

## Supplying More Meaningful Data

This is a very contrived example of a Supplier.  Let's make it appear a little more realistic by adding a data class
with values:

First, add a Lombok POJO data class to capture a very simple Stock quote:

```java
@Data
@Builder
class StockQuote {
    @Builder.Default
    private final String timestamp = Instant.now().toString();
    private final String symbol;
    private final String price;
}
```

Add a variable to hold the stocks as a Map of Symbols to Price (note, `Map.ofEntries()` produces an immutable Map, so we
need to pass that into a new HashMap):

```java
    // simple map of some stocks with initial starting prices
    static Map<String, Double> STOCKS_MAP = new HashMap<>(
        Map.ofEntries(
            Map.entry("AAPL", 125.35),
            Map.entry("UNH", 333.66),
            Map.entry("HD", 259.57),
            Map.entry("GS", 333.58),
            Map.entry("MSFT", 233.12),
            Map.entry("V", 333.66),
            Map.entry("MCD", 207.73),
            Map.entry("BA", 225.63),
            Map.entry("MMM", 176.83),
            Map.entry("JNJ", 158.36),
            Map.entry("CAT", 213.25),
            Map.entry("WMT", 129.50),
            Map.entry("PG", 123.42),
            Map.entry("IBM", 120.66),
            Map.entry("TRV", 149.12),
            Map.entry("DIS", 194.66),
            Map.entry("JPM", 150.97),
            Map.entry("NKE", 136.80),
            Map.entry("AXP", 139.25),
            Map.entry("CXV", 103.52),
            Map.entry("MRK", 72.66),
            Map.entry("RXT", 74.83),
            Map.entry("INTC", 74.83),
            Map.entry("VZ", 54.60),
            Map.entry("KO", 49.96),
            Map.entry("CSCO", 45.48),
            Map.entry("DOW", 62.35),
            Map.entry("XOM", 56.46),
            Map.entry("WBA", 47.33),
            Map.entry("PFE", 33.46)
        )
    );
```

To introduce randomness into the stock prices over time, we'll add a simple helper function:

```java
    /**
     * Generates a randomized stock price based on the old value and some
     * artificial volatility.
     *
     * @param oldPrice   the old price to be influenced
     * @param volatility amount of fluctuation to introduce 0.0 (low) to 1.0 (high)
     * @return a new price
     */
    static double randomizePrice(double oldPrice, double volatility) {
        var changePercent = 2.0 * volatility * (ThreadLocalRandom.current().nextDouble() - 0.5);
        return oldPrice + (oldPrice * (changePercent / 100));
    }
```
^ Thanks [Stack Overflow](https://stackoverflow.com/questions/8597731/are-there-known-techniques-to-generate-realistic-looking-fake-stock-data/8597889#8597889)!

Finally, update the original Supplier function:

```java
    @PollableBean
    Supplier<Flux<StockQuote>> simpleStockQuote() {
        return () -> {
            // update each stock entry with a randomized price based on the previous one...
            STOCKS_MAP.replaceAll((k, v) -> randomizePrice(v, 0.02));

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

Finally, run the application again.

Verify that the new topic is receiving the StockQuote messages as events:

```shell
$ kafkacat -b kafka1.test.local:9192 -C -t simpleStockQuote-out-0
```

It is fake data but this should give us a good start to what a SCSt Producer will eventually look like.

## Extra Credit

- Experiment with the `@PollingBean` configuration options.


- Review the Kafka Binder docs: https://github.com/spring-cloud/spring-cloud-stream-binder-kafka
