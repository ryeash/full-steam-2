package com.fullsteam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.List;

public final class RandomNames {
    private static final List<String> names;

    static {
        try (InputStream resourceAsStream = RandomNames.class.getResourceAsStream("/names.txt")) {
            if (resourceAsStream == null) {
                throw new IllegalArgumentException("missing names resource");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream));
            names = reader.lines()
                    .filter(s -> !s.contains("("))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private RandomNames() {
    }

    public static String randomName() {
        return names.get((int) (Math.random() * names.size()));
    }
}