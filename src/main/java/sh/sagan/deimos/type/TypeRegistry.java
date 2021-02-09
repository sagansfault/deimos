package sh.sagan.deimos.type;

import org.bukkit.entity.Player;
import sh.sagan.deimos.type.converters.BooleanConverter;
import sh.sagan.deimos.type.converters.IntegerConverter;
import sh.sagan.deimos.type.converters.OnlinePlayerConverter;
import sh.sagan.deimos.type.converters.StringConverter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class TypeRegistry {

    private final Map<Class<?>, Converter<?>> converters = new ConcurrentHashMap<>();

    public TypeRegistry() {
        this.registerDefaultConverters();
    }

    public <T> void register(Class<T> clazz, Converter<T> converter) {
        this.converters.put(clazz, converter);
    }

    public Map<Class<?>, Converter<?>> converters() {
        return this.converters;
    }

    public Converter<?> getConverterOrDefault(Class<?> clazz) {
        return this.converters.get(clazz) == null ? converters.get(String.class) : this.converters.get(clazz);
    }

    public Optional<Converter<?>> getConverter(Class<?> clazz) {
        return Optional.ofNullable(this.converters.get(clazz));
    }

    public void registerDefaultConverters() {
        this.register(Boolean.class, new BooleanConverter());
        this.register(String.class, new StringConverter());
        this.register(Integer.class, new IntegerConverter());
        // this.register(Player.class, new OnlinePlayerConverter());
    }
}
