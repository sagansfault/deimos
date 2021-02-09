package sh.sagan.deimos.type.converters;

import sh.sagan.deimos.type.Converter;

import java.util.Optional;

public class BooleanConverter implements Converter<Boolean> {

    @Override
    public Optional<Boolean> convert(String arg) {
        if (arg.equalsIgnoreCase("true")) {
            return Optional.of(true);
        } else if (arg.equalsIgnoreCase("false")) {
            return Optional.of(false);
        } else return Optional.empty();
    }
}
