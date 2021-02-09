package sh.sagan.deimos;

import sh.sagan.deimos.command.DeimosCommand;
import sh.sagan.deimos.command.DeimosExecutor;
import sh.sagan.deimos.type.TypeRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Mock {

    public static void main(String[] args) {

        TypeRegistry registry = new TypeRegistry();

        DeimosCommand.Sub sub = new DeimosCommand.Sub("mhmm");
        sub.addArg(new DeimosCommand.Arg(Integer.class, registry.getConverter(Integer.class).get(), Optional.of("")));
        sub.addArg(new DeimosCommand.Arg(Integer.class, registry.getConverter(Integer.class).get(), Optional.of("")));
        sub.addArg(new DeimosCommand.Arg(Integer.class, registry.getConverter(Integer.class).get(), Optional.of("5")));

        String[] rawArgs = {"1", "2"};

        Optional<List<DeimosExecutor.PassType>> passTypes = acceptableArguments(rawArgs, sub);
        System.out.println(passTypes);
    }

    private static Optional<List<DeimosExecutor.PassType>> acceptableArguments(String[] rawArgs, DeimosCommand.Sub sub) {

        /*
        A check to handle if the subcommand had no params (just base root invocation) and if there were also no rawArgs
        to accompany it. If this was the case, return an optional containing an empty list. We need to return an empty
        list and not just an empty optional because this was a valid command call and we need to indicate that there
        was no argument status to pass in.
         */
        if (sub.getArgs().isEmpty() && rawArgs.length == 0) {
            return Optional.of(new ArrayList<>());
        }

        // Too few or too many arguments. This also takes into account possible @Text params
        if (rawArgs.length > sub.maxPossibleArgs() || rawArgs.length < sub.minPossibleArgs()) {
            return Optional.empty();
        }

        // Could just stream the args and check for allMatch or noneMatch but this preserves the use of an extra loop
        boolean allRequired = true;
        boolean allOptional = true;
        boolean textEnding = sub.getLastArg().isText();

        // if we know that it's a @Text ending then we know the args before it must be required and don't need to check these
        if (!textEnding) {
            for (DeimosCommand.Arg arg : sub.getArgs()) {
                if (!arg.isRequired()) {
                    allRequired = false;
                } else if (arg.isRequired()) {
                    allOptional = false;
                }
            }
        }

        if (textEnding) {
            /*
            TEXT ENDING
            This case would actually fall into the last "mix" case but to make processing easier and faster, I made it
            a special one to catch it right off the bat and avoid doing some long algorithm to catch it anyway
             */
            if (!sub.getLastArg().isText()) {
                return Optional.empty();
            }

            List<DeimosExecutor.PassType> passTypes = new ArrayList<>();

            // make sure all the args before the text match up first
            for (int i = 0; i < sub.getArgs().size() - 1; i++) {
                if (!sub.getArgs().get(i).convert(rawArgs[i]).isPresent()) {
                    return Optional.empty();
                } else {
                    passTypes.add(DeimosExecutor.PassType.PASS_VALUE);
                }
            }

            // check for last arg present (assert that it's a @Text param)
            if (rawArgs.length >= sub.getArgs().size()) {
                passTypes.add(DeimosExecutor.PassType.PASS_REMAINING);
            } else {
                passTypes.add(sub.getLastArg().isOptionalWithNoDefault() ? DeimosExecutor.PassType.PASS_NULL : DeimosExecutor.PassType.PASS_DEFAULT);
            }

            return Optional.of(passTypes);
        } if (allRequired || rawArgs.length == sub.maxPossibleArgs()) {
            /*
            ALL REQUIRED or MAX GIVEN
            This case is a catch for if either all the arguments are required or the maximum arguments were provided. In
            this case we can just do a one for once match. In the case of either, if one of them doesn't match then
            there's no room for shifting or optional passing because the max was given and/or all the args are required.
             */
            return tryDirectMatch(rawArgs, sub);
        } else if (allOptional) {
            /*
            ALL OPTIONAL
            Currently, because of previous checks, if the command has a @Text ending, the previous arguments MUST be
            required. A more elegant solution is currently being thought of. The possible @Text ending was already accounted
            for in a previous check so we can assume all params here are optional and there is no @Text ending param
             */
            if (sub.getArgs().stream().anyMatch(DeimosCommand.Arg::isRequired)) {
                return Optional.empty();
            }

            // first account for if ALL optional args are passed in
            if (rawArgs.length == sub.getArgs().size()) {
                // The max amount of args were passed in so we can check for a 1 to 1 match
                return tryDirectMatch(rawArgs, sub);
            } else if (rawArgs.length == 0) {
                // no args were passed into an all optional subcommand so just need to determine whether to pass null or default
                List<DeimosExecutor.PassType> passTypes = sub.getArgs().stream()
                        .map(arg -> arg.isOptionalWithNoDefault() ? DeimosExecutor.PassType.PASS_NULL : DeimosExecutor.PassType.PASS_DEFAULT)
                        .collect(Collectors.toList());
                return Optional.of(passTypes);
            } else {
                // rawArgs' length was less then the max and more than 0
                List<DeimosCommand.Arg> mutableArgs = new ArrayList<>(sub.getArgs());
                List<DeimosExecutor.PassType> possiblePassTypes = new ArrayList<>();

                /*
                This is a bit troublesome to explain but the basis of this "algorithm" is the fact that the args supplied
                by the player are always sequential. One arg might not come right next to another but it will always
                come after it in some distance. It works by first checking the first argument against the first param
                of the sub command. If there's no match then we shift the whole set of rawArgs over by 1. Now we compare
                the first rawArg to param 2. If there is a match then we move to the next: compare rawArg 2 its param
                (which is now param 3 due to the shift caused by rawArg 1). If there is a match, shift until there is or
                until we dont have enough room left to shift (ie.) 3 rawArgs provided to be checked against a subcommand
                with 5 params, and we have to shift twice and there still isn't match. We can't shift further because
                after 2 shifts, rawArg 3 would be checked against a non-existent param 6. In this case we know that this
                sub command did not match.
                 */
                int passedValues = 0;
                OUTER:
                while (rawArgs.length <= mutableArgs.size() && passedValues < rawArgs.length) {
                    for (int i = 0; i < rawArgs.length; i++) {
                        if (!mutableArgs.get(i).convert(rawArgs[i]).isPresent()) {
                            mutableArgs.remove(i);
                            possiblePassTypes.add(mutableArgs.get(i).isOptionalWithNoDefault() ?
                                    DeimosExecutor.PassType.PASS_NULL : DeimosExecutor.PassType.PASS_DEFAULT);
                            continue OUTER;
                        } else {
                            possiblePassTypes.add(DeimosExecutor.PassType.PASS_VALUE);
                            passedValues++;
                        }
                    }
                }

                // if we matched all the args but there are some remaining, pass in null or default
                if (mutableArgs.size() > rawArgs.length) {
                    // start where we left off
                    for (int i = rawArgs.length; i < mutableArgs.size(); i++) {
                        possiblePassTypes.add(mutableArgs.get(i).isOptionalWithNoDefault() ?
                                DeimosExecutor.PassType.PASS_NULL : DeimosExecutor.PassType.PASS_DEFAULT);
                    }
                }

                /*
                We checked all the args and since the `checkingArgIndex` only increments when there is a match then we
                can just see if we matched all of them by checking that the `checkingArgIndex` is greater then the last
                arg's index (equal to the length)
                 */
                return passedValues == rawArgs.length ? Optional.of(possiblePassTypes) : Optional.empty();
            }
        } else {
            // MIX

            List<DeimosExecutor.PassType> passTypes = new ArrayList<>();
            int requiredArgsCount = (int) sub.getArgs().stream().filter(DeimosCommand.Arg::isRequired).count();

            /*
            We can first check for the optimal situation: there was as many args provided by the player as there were
            required params in the command (all optionals were omitted).
             */
            if (rawArgs.length == requiredArgsCount) {
                int rawArgCheckIndex = 0;
                for (DeimosCommand.Arg arg : sub.getArgs()) {
                    if (!arg.isRequired()) {
                        passTypes.add(arg.isOptionalWithNoDefault() ? DeimosExecutor.PassType.PASS_NULL : DeimosExecutor.PassType.PASS_DEFAULT);
                        continue;
                    }

                    // if even one doesn't match then cancel
                    if (!arg.convert(rawArgs[rawArgCheckIndex]).isPresent()) {
                        return Optional.empty();
                    } else {
                        passTypes.add(DeimosExecutor.PassType.PASS_VALUE);
                        rawArgCheckIndex++;
                    }
                }

                return Optional.of(passTypes);
            }

            /*
            By now we know that:
                - There is a mix of REQUIRED and OPTIONAL args.
                - There are no @Text arguments
                - There were more arguments provided than the minimum but also less than the maximum

            For now, this current algorithm will just brute force it's way along the subcommand's arguments until a
            full match is found where all the arguments match and all the required arguments are filled.
             */

            // index is arg, value is place of that arg
            int[] placements = IntStream.range(0, rawArgs.length).toArray();
            int intoArrSize = sub.getArgs().size();

            OUTER:
            while (placements[0] < (intoArrSize - placements.length)) {
                int argToShift = 0;
                for (int i = 0; i < placements.length; i++) {
                    int checkingArg = placements.length - 1 - i;
                    if (placements[checkingArg] < intoArrSize - 1 - i) {
                        argToShift = checkingArg;
                        break;
                    }
                }

                placements[argToShift] += 1;
                for (int i = (argToShift + 1); i < placements.length; i++) {
                    placements[i] = placements[argToShift] + (i - argToShift);
                }

                /*
                Check to make sure placements contain all

                Because we know there are some required args. We first need to check that there are placements for the
                required args of the sub command.
                 */
                for (int placement : placements) {
                    boolean match = false;
                    for (int j = 0; j < sub.getArgs().size(); j++) {
                        if (!sub.getArgs().get(j).isRequired()) continue;
                        if (j == placement) {
                            match = true;
                            break;
                        }
                    }
                    if (!match) {
                        continue OUTER;
                    }
                }

                int placementCheck = 0;
                for (int i = 0; i < sub.getArgs().size(); i++) {

                    DeimosCommand.Arg arg = sub.getArgs().get(i);

                    // if there should be a check for type here
                    if (i == placements[placementCheck]) {
                        if (!arg.convert(rawArgs[i]).isPresent()) {
                            continue OUTER;
                        } else {
                            passTypes.add(DeimosExecutor.PassType.PASS_VALUE);
                        }
                    } else {
                        // otherwise pass default or null
                        passTypes.add(arg.isOptionalWithNoDefault() ? DeimosExecutor.PassType.PASS_NULL : DeimosExecutor.PassType.PASS_DEFAULT);
                    }
                }

                // if there was an issue in the matching, then continue OUTER loop otherwise return the passtypes
                return Optional.of(passTypes);
            }
        }

        return Optional.empty();
    }

    private static Optional<List<DeimosExecutor.PassType>> tryDirectMatch(String[] rawArgs, DeimosCommand.Sub sub) {

        // have to be the same length
        if (rawArgs.length != sub.getArgs().size()) {
            return Optional.empty();
        }

        for (int i = 0; i < sub.getArgs().size(); i++) {
            // if there's at least one that doesn't match
            if (!sub.getArgs().get(i).convert(rawArgs[i]).isPresent()) {
                return Optional.empty();
            }
        }

        return Optional.of(Collections.nCopies(rawArgs.length, DeimosExecutor.PassType.PASS_VALUE));
    }
}
