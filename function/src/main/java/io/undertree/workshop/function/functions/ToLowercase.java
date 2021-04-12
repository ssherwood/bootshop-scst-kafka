package io.undertree.workshop.function.functions;

import java.util.function.Function;

public class ToLowercase implements Function<String, String> {
    @Override
    public String apply(String s) {
        return s.toLowerCase();
    }
}
