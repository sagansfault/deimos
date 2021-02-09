package sh.sagan.deimos.type.converters;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import sh.sagan.deimos.type.Converter;

import java.util.Optional;

public class OnlinePlayerConverter implements Converter<Player> {

    @Override
    public Optional<Player> convert(String arg) {
        return Optional.ofNullable(Bukkit.getServer().getPlayer(arg));
    }
}
