package io.quarkus.arc.impl;

import java.util.Arrays;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

public final class Sets {

    private Sets() {
    }

    /**
     * Unlike {@link Set#of(Object...)} this method does not throw an {@link IllegalArgumentException} if there are duplicate
     * elements.
     *
     * @param <E>
     * @param elements
     * @return the set
     */
    @SafeVarargs
    public static <E> Set<E> of(E... elements) {
        switch (elements.length) {
            case 0:
                return ImmutableSet.of();
            case 1:
                return ImmutableSet.of(elements[0]);
            case 2:
                return elements[0].equals(elements[1]) ? ImmutableSet.of(elements[0]) : ImmutableSet.of(elements[0], elements[1]);
            default:
                return ImmutableSet.copyOf(Arrays.asList(elements));
        }
    }

}
