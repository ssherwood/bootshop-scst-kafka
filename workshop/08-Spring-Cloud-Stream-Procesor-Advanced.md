# 08. Lab - SCSt: Stock Quote Processor Advanced

## Goal(s)

In this lab we'll enhance the data in the Stock Quote even more, making more direct use of the reactive programming
model to derive information from the stream itself.  The goal will be to add in the output stream the stock price delta
from the last one and calculate the percentage change.

# What the Flux?

Remember in the Spring Cloud Function demo we saw that you can compose functions together?  We can also do that in SCSt.
We'll introduce a new POJO to enhance the data even more:

```java
@Data
@Builder
class EnhancedStockQuoteDelta {
    private final String timestamp;
    private final String symbol;
    private final String fullName;
    private final String price;
    private final String lowPrice;
    private final String highPrice;
    private final String deltaPrice;

    static EnhancedStockQuoteDeltaBuilder from(final EnhancedStockQuote enhancedStockQuote) {
        return EnhancedStockQuoteDelta.builder()
                .timestamp(enhancedStockQuote.getTimestamp())
                .symbol(enhancedStockQuote.getSymbol())
                .fullName(enhancedStockQuote.getFullName())
                .price(enhancedStockQuote.getPrice())
                .lowPrice(enhancedStockQuote.getLowPrice())
                .highPrice(enhancedStockQuote.getHighPrice());
    }
}
```

With this object we now can track the "deltaPrice".  This should track the difference in price between any two quotes
for the same symbol.  That can be a bit tricky to do without diving deeper into Project Reactor and learning more
about Fluxes.  

Let's add a new function processor called `calculatePriceDelta`:

```java
    @Bean
    public Function<Flux<EnhancedStockQuote>, Flux<EnhancedStockQuoteDelta>> calculatePriceDelta() {
        return quoteFlux ->
                quoteFlux.groupBy(EnhancedStockQuote::getSymbol)
                        .flatMap(groupedFlux -> groupedFlux.buffer(2, 1)
                                .filter(groupedQuotes -> groupedQuotes.size() == 2)
                                .map(groupedQuotes -> {
                                    // this is effectively blocking the stream...
                                    var currentQuote = groupedQuotes.get(1);
                                    var deltaPrice = Double.parseDouble(currentQuote.getPrice()) - Double.parseDouble(groupedQuotes.get(0).getPrice());
                                    return EnhancedStockQuoteDelta.from(currentQuote).deltaPrice(String.format("%+.2f", deltaPrice)).build();
                                })
                        ).log();
    }
```

This function takes in the `EnhancedStockQuote` and returns the new `EnhancedStockQuoteDelta`.

Take some time to review the code, this is an example of using `groupBy`, `flatMap` and `buffer` together to split the
stream of data into separate streams mapped by the stock quote symbol.  We then buffer every 2 entries, with a step of 1
for a given quote stream.  Finally, we use a blocking map operation to get the "latest" quote and compare it to the
previous one and produce the new EnhancedQuoteDelta object.

But how do we stitch this together so SCSt will invoke the original function with this?

## Function Definition(s)

In the application.yml review the function definition:

```yaml
spring.cloud.function.definition: enhanceStockQuote
```

First we need to use the pipe `|` symbol to join the functions:

```yaml
spring.cloud.function.definition: enhanceStockQuote|calculatePriceDelta
```

However, this effectively changes the channel name, so we also need to update all of them as well:

- enhanceStockQuote|calculatePriceDelta-in-0
- enhanceStockQuote|calculatePriceDelta-out-0

You have to do this for both the `bindings` and the `kafka.bindings`.

It is possible to create what SCSt calls a "Descriptive Binding Names" where you define a more an aesthetically pleasing
name for the function definition and use that for the bindings.  These however create another layer of indirection and
can potentially make it even more confusing to read the already complex yaml structure.

The final yaml should look like this:

```yaml
spring.application.name: "stock-quote-processor"

spring.cloud.function.definition: enhanceStockQuote|calculatePriceDelta

spring.cloud.stream:
  bindings:
    enhanceStockQuote|calculatePriceDelta-in-0:
      binder: kafka
      group: ${spring.application.name}-enhancer
      destination: stock-quotes
      consumer:
        use-native-decoding: true
    enhanceStockQuote|calculatePriceDelta-out-0:
      binder: kafka
      destination: stock-quotes.enhanced
      producer:
        use-native-encoding: true
        partition-count: 5
  kafka.bindings:
    enhanceStockQuote|calculatePriceDelta-in-0:
      consumer:
        #reset-offsets: true
        start-offset: earliest
        configuration:
          key.deserializer: org.apache.kafka.common.serialization.StringDeserializer
          value.deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
    enhanceStockQuote|calculatePriceDelta-out-0:
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

Now if you run the application, you should be able to confirm that the function chaining has taken effect that the
`stock-quote.enhanced` contains the delta price.

Warning, you may need to delete the `stock-quote` topic at this point if you have poison messages in it that this
processor hasn't already seen before (since we've not really configured any DLQ/DLT behavior).  To delete the topic, use
the Kafka CLI:

```shell
$ bin/kafka-topics.sh --bootstrap-server kafka1.test.local:9192 --delete --topic stock-quotes
```

Re-run the Supplier and the Processor now and review the enhanced topic with kafkacat:

```shell
$ kafkacat -b kafka1.test.local:9192 -C -f "key=[%k] -> value=[%s]\n" -t stock-quotes.enhanced
```

You should see messages that look something like this:

```text
...
key=[PFE] -> value=[{"timestamp":"2021-03-14T18:50:29.133505Z","symbol":"PFE","fullName":"Pfizer Inc.","price":"33.45","lowPrice":"26.41","highPrice":"43.08","deltaPrice":"+0.00"}]
key=[WBA] -> value=[{"timestamp":"2021-03-14T18:50:29.133793Z","symbol":"WBA","fullName":"Walgreens Boots Alliance Inc.","price":"47.33","lowPrice":"33.36","highPrice":"55.49","deltaPrice":"-0.01"}]
key=[INTC] -> value=[{"timestamp":"2021-03-14T18:50:29.134262Z","symbol":"INTC","fullName":"Intel Corp.","price":"74.83","lowPrice":"43.61","highPrice":"65.11","deltaPrice":"+0.00"}]
key=[XOM] -> value=[{"timestamp":"2021-03-14T18:50:29.134448Z","symbol":"XOM","fullName":"Exxon Mobil Corp.","price":"56.43","lowPrice":"30.11","highPrice":"61.61","deltaPrice":"-0.01"}]
```

If you watch the logs closely, you should be able to validate that the deltaPrice is accurate.

## Reactive Gotchas

There are a couple of considerations that you might want to be aware of using these techniques.

First, we used a `groupBy` method that effectively splits the stream up into multiple smaller streams, we probably only
want to do this with data streams that have a low cardinality on whatever key we are going to group by.

We also used a buffer statement which, at least in this case is very small, but something to be considerate of since it
will impact memory consumption.

Finally, we're not tracking state across start/stop of the application, this effectively means we're loosing the initial
stock quote event as there is no previous message to compare it to.  There might be clever ways to deal wit this in the
stream handling itself instead of just filtering it out (as we did).

# Extra Credit

