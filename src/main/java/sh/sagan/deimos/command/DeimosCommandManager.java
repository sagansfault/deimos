package sh.sagan.deimos.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;
import sh.sagan.deimos.type.TypeRegistry;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeimosCommandManager {

    private final Set<DeimosCommand> commands = new HashSet<>();

    private final TypeRegistry typeRegistry;
    private final JavaPlugin plugin;
    private final CommandMap commandMap;
    private final Logger logger;

    public DeimosCommandManager(JavaPlugin plugin) {
        this.typeRegistry = new TypeRegistry();
        this.plugin = plugin;
        this.logger = Logger.getLogger(this.getClass().getName());
        this.commandMap = (CommandMap) this.accessPrivateField(Bukkit.getServer(), "commandMap");
    }

    public TypeRegistry getTypeRegistry() {
        return typeRegistry;
    }

    public void register(DeimosCommand command) {
        this.commands.add(command);
        command.registerSubs(this.logger, this.typeRegistry);
    }

    public void registerAllIntoSpigot() {
        if (commandMap != null) {
            for (DeimosCommand command : commands) {
                commandMap.register(plugin.getName(), command);
            }
        }
    }

    private Object accessPrivateField(Object instance, String fieldName) {

        Field field;
        try {
            field = instance.getClass().getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            logger.log(Level.WARNING, "No such field '" + fieldName + "', stacktrace:");
            e.printStackTrace();
            return null;
        }

        Object found;

        field.setAccessible(true);
        try {
            found = field.get(instance);
        } catch (IllegalAccessException e) {
            logger.log(Level.WARNING, "Could not access private field '" + fieldName + "', stacktrace:");
            e.printStackTrace();
            return null;
        }

        return found;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }
}
