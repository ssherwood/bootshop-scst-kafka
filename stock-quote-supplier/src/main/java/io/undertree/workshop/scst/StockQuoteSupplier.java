package io.undertree.workshop.scst;

import lombok.Builder;
import lombok.Data;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.context.PollableBean;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@SpringBootApplication
public class StockQuoteSupplier {

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
                    Map.entry("CVX", 103.52),
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

    public static void main(String[] args) {
        SpringApplication.run(StockQuoteSupplier.class, args);
    }

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
                                    .build()));
        };
    }

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
}

@Data
@Builder
class StockQuote {
    @Builder.Default
    private final String timestamp = Instant.now().toString();
    private final String symbol;
    private final String price;
}