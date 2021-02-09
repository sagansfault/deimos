package sh.sagan.deimos.type.converters;

import sh.sagan.deimos.type.Converter;

import java.util.Optional;

public class StringConverter implements Converter<String> {

    @Override
    public Optional<String> convert(String arg) {
        return Optional.of(arg);
    }
}
