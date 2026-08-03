package com.dbshift.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Flyway-compatible version: V1, V001, V1_2, V1.2.3, V2024.01.01.1
 */
public final class Version implements Comparable<Version> {

    private final String raw;
    private final List<Integer> parts;

    private Version(String raw, List<Integer> parts) {
        this.raw = raw;
        this.parts = parts;
    }

    public static Version parse(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Version cannot be blank");
        }
        String trimmed = version.trim();
        String numeric = trimmed.replaceFirst("(?i)^[VU]", "");
        if (numeric.isBlank()) {
            throw new IllegalArgumentException("Invalid version: " + version);
        }
        String[] tokens = numeric.split("[._]");
        List<Integer> parts = new ArrayList<>();
        for (String token : tokens) {
            if (token.isBlank()) {
                throw new IllegalArgumentException("Invalid version: " + version);
            }
            parts.add(Integer.parseInt(token));
        }
        return new Version(trimmed.toUpperCase().startsWith("U")
                ? "U" + numeric.replace('_', '.')
                : "V" + numeric.replace('_', '.'), List.copyOf(parts));
    }

    public static Version ofParts(int... numbers) {
        List<Integer> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder("V");
        for (int i = 0; i < numbers.length; i++) {
            parts.add(numbers[i]);
            if (i > 0) {
                sb.append('.');
            }
            sb.append(numbers[i]);
        }
        return new Version(sb.toString(), List.copyOf(parts));
    }

    public String display() {
        return raw;
    }

    public List<Integer> parts() {
        return Collections.unmodifiableList(parts);
    }

    @Override
    public int compareTo(Version other) {
        int max = Math.max(parts.size(), other.parts.size());
        for (int i = 0; i < max; i++) {
            int a = i < parts.size() ? parts.get(i) : 0;
            int b = i < other.parts.size() ? other.parts.get(i) : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Version version)) {
            return false;
        }
        return compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parts);
    }

    @Override
    public String toString() {
        return raw;
    }
}
