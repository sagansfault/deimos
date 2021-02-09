package sh.sagan.deimos;

import org.bukkit.plugin.java.JavaPlugin;
import sh.sagan.deimos.command.DeimosCommandManager;

public class Deimos extends JavaPlugin {

    private static Deimos instance;
    private DeimosCommandManager commandManager;

    @Override
    public void onEnable() {
        instance = this;
        commandManager = new DeimosCommandManager(this);
    }

    public static Deimos getInstance() {
        return instance;
    }

    public DeimosCommandManager getCommandManager() {
        return commandManager;
    }
}
