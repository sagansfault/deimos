package sh.sagan.deimos.type;

import java.util.Optional;

public interface Converter<T> {
    Optional<T> convert(String arg);
}
