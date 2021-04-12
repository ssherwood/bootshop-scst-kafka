package io.undertree.workshop.function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@SpringBootApplication
public class DemoFunctionApp {

    public static void main(String[] args) {
        SpringApplication.run(DemoFunctionApp.class, args);
    }

    @Bean
    public Supplier<String> greetings() {
        return () -> "Hello at " + Instant.now();
    }

    @Bean
    public Function<String, String> toUppercase() {
        return value -> value.toUpperCase();
    }

    @Bean
    public Consumer<String> logMessage() {
        return System.out::println;
    }

}