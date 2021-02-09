package sh.sagan.deimos.type.converters;

import sh.sagan.deimos.type.Converter;

import java.util.Optional;

public class IntegerConverter implements Converter<Integer> {

    @Override
    public Optional<Integer> convert(String arg) {
        int value;
        try {
            value = Integer.parseInt(arg);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
