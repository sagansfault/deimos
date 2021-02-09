package sh.sagan.deimos.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.sagan.deimos.command.annotations.Priority;
import sh.sagan.deimos.command.annotations.SubCommand;
import sh.sagan.deimos.command.annotations.Text;
import sh.sagan.deimos.type.Converter;
import sh.sagan.deimos.type.TypeRegistry;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeimosCommand extends Command {

    private final DeimosExecutor executor;

    private final String root;
    private final List<Sub> subs = new ArrayList<>();

    public DeimosCommand(String root, String usage, String desc, String... aliases) {
        super(root, desc, usage, Arrays.asList(aliases));
        this.root = root;
        this.executor = new DeimosExecutor(this);
    }

    public DeimosCommand(String root, String... aliases) {
        this(root, "", "", aliases);
    }

    public final String getRoot() {
        return root;
    }

    public void registerSubs(Logger logger, TypeRegistry typeRegistry) {

        OUTER:
        for (Method method : this.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(SubCommand.class)) {

                // begin making a sub command for this method because it has the required annotation.
                // marked as priority if it has the annotation present on it. Default is false.
                Sub sub = new Sub(method.getAnnotation(SubCommand.class).base(), method.isAnnotationPresent(Priority.class));

                // make sure the first arg is always a player
                if (!method.getParameters()[0].getType().equals(Player.class)) {
                    logger.log(Level.WARNING, "Subcommand not registered. First argument must be of type 'Player':" + method.toString());
                    continue;
                }

                Parameter[] parameters = method.getParameters();

                // need to make sure that the only arg containing a Text annotation is the last one
                // @Text annotations are only allowed at the end of a command
                for (int i = 0; i < (parameters.length - 1); i++) {
                    if (parameters[i].isAnnotationPresent(Text.class)) {
                        logger.log(Level.WARNING, "Subcommand not registered. Text annotation can only be applied" +
                                " to last arg: " + method.toString());
                        continue OUTER;
                    }
                }

                /*
                CURRENTLY A WORK IN PROGRESS:
                Currently, if a command ends in a @Text parameter, all the previous args MUST be required. This is due
                to how the commands are processed and is currently being worked on for a more elegant solution.
                 */
                if (parameters[parameters.length - 1].isAnnotationPresent(Text.class)) {
                    // -1 because @Text could be optional despite its predecessors not being allowed to be
                    for (int i = 0; i < parameters.length - 1; i++) {
                        Parameter parameter = parameters[i];
                        if (parameter.isAnnotationPresent(sh.sagan.deimos.command.annotations.Optional.class)) {
                            logger.log(Level.WARNING, "Subcommand not registered. @Optional annotation not allowed" +
                                    "on previous params of @Text ending subcommand: " + method.toString());
                            break OUTER;
                        }
                    }
                }

                /*
                Note that if the last parameter has a @Text annotation, it's type does not necessarily have to
                be 'String'. The raw string arguments are just joined together and passed into the type converter
                placed on that @Text annotation parameter.
                 */

                // constructing the arg from the parameters and its annotations
                for (int i = 1; i < parameters.length; i++) {
                    Parameter parameter = parameters[i];

                    Optional<Converter<?>> converter = typeRegistry.getConverter(parameter.getType());
                    // make sure that the type on this parameter has a valid converter
                    if (!converter.isPresent()) {
                        logger.log(Level.WARNING, "Subcommand not registered. No converter found for parameter " +
                                "'" + parameter.toString() + "': " + method.toString());
                        continue OUTER;
                    }

                    Arg arg;
                    if (parameter.isAnnotationPresent(sh.sagan.deimos.command.annotations.Optional.class)) {
                        arg = new Arg(
                                parameter.getType(), converter.get(),
                                Optional.of(parameter.getDeclaredAnnotation(sh.sagan.deimos.command.annotations.Optional.class).value()),
                                parameter.isAnnotationPresent(Text.class) // a check is made to ensure this only applies to the last arg
                        );
                    } else {
                        arg = new Arg(parameter.getType(), converter.get(),
                                Optional.empty(), parameter.isAnnotationPresent(Text.class));
                    }
                    sub.addArg(arg);
                }
                this.subs.add(sub);
            }
        }
    }

    public List<Sub> getSubs() {
        return subs;
    }

    @Override
    public boolean execute(@Nonnull CommandSender sender, @Nonnull String label, @Nonnull String[] args) {
        return this.executor.onCommand(sender, this, label, args);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeimosCommand command = (DeimosCommand) o;
        return root.equals(command.root);
    }

    public static class Sub {
        private final List<Arg> args = new ArrayList<>();
        private final String base;
        private boolean priority = false;

        public Sub(String base, boolean priority) {
            String trimmed = base.trim();
            if (base.equals("") || base.isEmpty() || trimmed.isEmpty()) {
                this.base = null;
            } else {
                this.base = trimmed;
            }
            this.priority = priority;
        }

        public Sub(String base) {
            this(base, false);
        }

        public Sub(boolean priority) {
            this("", priority);
        }

        public Sub() {
            this("", false);
        }

        public void addArg(Arg arg) {
            this.args.add(arg);
        }

        public List<Arg> getArgs() {
            return args;
        }

        public Optional<String> getBase() {
            return Optional.ofNullable(base);
        }

        public boolean hasPriority() {
            return priority;
        }

        public int minPossibleArgs() {
            return (int) this.args.stream().filter(Arg::isRequired).count();
        }

        public int maxPossibleArgs() {
            // 500 is the maximum character length a chat message can be in minecraft
            return this.args.stream().anyMatch(Arg::isText) ? 500 : this.args.size();
        }

        public Arg getLastArg() {
            return this.args.get(this.args.size() - 1);
        }
    }

    public static class Arg {
        private final Class<?> type;
        private final Converter<?> converter;
        private final Optional<String> defaultValue;
        private final boolean text;

        public Arg(Class<?> type, Converter<?> converter, Optional<String> defaultValue, boolean text) {
            this.type = type;
            this.defaultValue = defaultValue;
            this.text = text;
            this.converter = converter;
        }

        public Arg(Class<?> type, Converter<?> converter, Optional<String> defaultValue) {
            this(type, converter, defaultValue, false);
        }

        public Class<?> getType() {
            return type;
        }

        public Optional<String> getDefault() {
            return defaultValue;
        }

        public boolean isOptionalWithNoDefault() {
            return this.defaultValue.isPresent() && this.defaultValue.get().equalsIgnoreCase("");
        }

        public boolean isRequired() {
            return !this.defaultValue.isPresent();
        }

        public boolean isText() {
            return text;
        }

        public Converter<?> getConverter() {
            return converter;
        }

        public Optional<?> convert(String arg) {
            return this.converter.convert(arg);
        }
    }
}
