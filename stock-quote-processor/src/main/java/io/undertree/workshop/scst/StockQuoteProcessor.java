package io.undertree.workshop.scst;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.util.Map;
import java.util.function.Function;

@SpringBootApplication
public class StockQuoteProcessor {

    static final Map<String, Tuple3<String, Double, Double>> STOCK_DATA = Map.ofEntries(
            Map.entry("AAPL", Tuples.of("Apple Inc.", 53.15, 145.09)),
            Map.entry("UNH", Tuples.of("UnitedHealth Group Inc.", 187.72, 367.95)),
            Map.entry("HD", Tuples.of("Home Depot Inc.", 140.63, 292.95)),
            Map.entry("GS", Tuples.of("Goldman Sachs Group Inc.", 130.85, 340.10)),
            Map.entry("MSFT", Tuples.of("Microsoft Corp.", 132.52, 246.13)),
            Map.entry("V", Tuples.of("Visa Inc. Cl A", 133.93, 226.13)),
            Map.entry("MCD", Tuples.of("McDonald's Corp.", 124.23, 231.91)),
            Map.entry("BA", Tuples.of("Boeing Co.", 89.00, 244.08)),
            Map.entry("MMM", Tuples.of("3M Co.", 114.04, 187.27)),
            Map.entry("JNJ", Tuples.of("Johnson & Johnson", 109.16, 173.65)),
            Map.entry("CAT", Tuples.of("Caterpillar Inc.", 87.50, 226.67)),
            Map.entry("WMT", Tuples.of("Walmart Inc.", 102.00, 153.66)),
            Map.entry("PG", Tuples.of("Procter & Gamble Co.", 94.34, 146.92)),
            Map.entry("IBM", Tuples.of("International Business Machines Corp.", 90.56, 135.88)),
            Map.entry("TRV", Tuples.of("Travelers Cos. Inc.", 76.99, 157.25)),
            Map.entry("DIS", Tuples.of("Walt Disney Co.", 79.07, 203.02)),
            Map.entry("JPM", Tuples.of("JPMorgan Chase & Co.", 76.91, 155.46)),
            Map.entry("NKE", Tuples.of("Nike Inc. Cl B", 60.00, 147.95)),
            Map.entry("AXP", Tuples.of("American Express Co.", 67.00, 151.46)),
            Map.entry("CVX", Tuples.of("Chevron Corp.", 51.60, 110.69)),
            Map.entry("MRK", Tuples.of("Merck & Co. Inc.A", 65.25, 87.80)),
            Map.entry("RXT", Tuples.of("Rackspace Technology Inc.", 15.25, 25.76)),
            Map.entry("INTC", Tuples.of("Intel Corp.", 43.61, 65.11)),
            Map.entry("VZ", Tuples.of("Verizon Communications Inc.", 48.84, 61.95)),
            Map.entry("KO", Tuples.of("Coca-Cola Co.", 36.27, 54.93)),
            Map.entry("CSCO", Tuples.of("Cisco Systems Inc.", 32.40, 49.34)),
            Map.entry("DOW", Tuples.of("Dow Inc.", 21.95, 64.27)),
            Map.entry("XOM", Tuples.of("Exxon Mobil Corp.", 30.11, 61.61)),
            Map.entry("WBA", Tuples.of("Walgreens Boots Alliance Inc.", 33.36, 55.49)),
            Map.entry("PFE", Tuples.of("Pfizer Inc.", 26.41, 43.08))
    );

    public static void main(String[] args) {
        SpringApplication.run(StockQuoteProcessor.class, args);
    }

    @Bean
    public Function<Flux<StockQuote>, Flux<EnhancedStockQuote>> enhanceStockQuote() {
        return quoteFlux -> quoteFlux
                .map(quote -> {
                            var stockData = STOCK_DATA.getOrDefault(quote.getSymbol(), Tuples.of("UNKNOWN", 0.01, 9999.00));

                            return EnhancedStockQuote.from(quote)
                                    .fullName(stockData.getT1())
                                    .lowPrice(String.format("%.2f", stockData.getT2()))
                                    .highPrice(String.format("%.2f", stockData.getT3()))
                                    .build();
                        }
                ).log();
    }

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
}

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