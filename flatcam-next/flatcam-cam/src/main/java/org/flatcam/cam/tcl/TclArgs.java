package org.flatcam.cam.tcl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a command's argument words into positional values and {@code -option value} pairs -
 * the same algorithm as Python FlatCAM's {@code TclCommand.parse_arguments}: a word matching
 * {@code -[a-zA-Z]...} opens an option name, consuming the next word as its value (or {@code null}
 * if it was the last word); every other word is positional, kept in order. This is the one parsing
 * convention every concrete FlatCAM Tcl command shares (e.g. {@code cutout gerber_file -dia 1.2
 * -margin 0.1 -outname cutout_geo}: one positional name, the rest options) - each command still
 * reads its own fields back out with the typed accessors below and raises {@link TclException} on
 * a missing required value or a bad number, same as Python's {@code check_args}.
 */
public final class TclArgs {
    private static final Pattern OPTION = Pattern.compile("^-([a-zA-Z].*)");

    private final List<String> positional;
    private final Map<String, String> options;

    private TclArgs(List<String> positional, Map<String, String> options) {
        this.positional = positional;
        this.options = options;
    }

    public static TclArgs parse(List<String> words) {
        List<String> positional = new ArrayList<>();
        Map<String, String> options = new LinkedHashMap<>();
        String pendingOption = null;
        for (String word : words) {
            Matcher matcher = OPTION.matcher(word);
            if (matcher.matches()) {
                if (pendingOption != null) {
                    options.put(pendingOption, null);
                }
                pendingOption = matcher.group(1);
                continue;
            }
            if (pendingOption == null) {
                positional.add(word);
            } else {
                options.put(pendingOption, word);
                pendingOption = null;
            }
        }
        if (pendingOption != null) {
            options.put(pendingOption, null);
        }
        return new TclArgs(List.copyOf(positional), options);
    }

    public int positionalCount() {
        return positional.size();
    }

    public String positional(int index) throws TclException {
        if (index < 0 || index >= positional.size()) {
            throw new TclException("missing required argument at position " + (index + 1));
        }
        return positional.get(index);
    }

    public String positionalOrDefault(int index, String fallback) {
        return index >= 0 && index < positional.size() ? positional.get(index) : fallback;
    }

    public boolean has(String option) {
        return options.containsKey(option) && options.get(option) != null;
    }

    /**
     * True if {@code -option} appeared at all, even as a bare trailing flag with no following
     * value - Python's own {@code 'name' in args} check (its {@code check_args} keeps a
     * value-less option as a present key mapped to {@code None}, not an absent one). Use this for
     * a flag whose mere presence means something (Python's {@code ncc}'s {@code -all}, no value
     * expected); use {@link #has} when the option must carry a real value.
     */
    public boolean isPresent(String option) {
        return options.containsKey(option);
    }

    public String option(String name) {
        return options.get(name);
    }

    public String requireOption(String name) throws TclException {
        String value = options.get(name);
        if (value == null) {
            throw new TclException("missing required option '-" + name + "'");
        }
        return value;
    }

    public String optionOrDefault(String name, String fallback) {
        String value = options.get(name);
        return value != null ? value : fallback;
    }

    public double requireDouble(String name) throws TclException {
        return parseDouble(name, requireOption(name));
    }

    public double doubleOrDefault(String name, double fallback) throws TclException {
        String value = options.get(name);
        return value != null ? parseDouble(name, value) : fallback;
    }

    public int intOrDefault(String name, int fallback) throws TclException {
        String value = options.get(name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new TclException("option '-" + name + "': expected an integer, got \"" + value + "\"");
        }
    }

    /** Python's {@code bool(...)} via Tcl text: any of true/1/yes (case-insensitive) is true. */
    public boolean booleanOrDefault(String name, boolean fallback) {
        String value = options.get(name);
        if (value == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    /** Every option name not in {@code known}, surfaced the way Python's check_args rejects them. */
    public void rejectUnknownOptions(java.util.Set<String> known) throws TclException {
        for (String name : options.keySet()) {
            if (!known.contains(name)) {
                throw new TclException("unknown option '-" + name + "'");
            }
        }
    }

    private static double parseDouble(String name, String value) throws TclException {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new TclException("option '-" + name + "': expected a number, got \"" + value + "\"");
        }
    }
}
