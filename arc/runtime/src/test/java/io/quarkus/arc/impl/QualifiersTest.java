package io.quarkus.arc.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;

import javax.enterprise.context.Initialized;
import javax.enterprise.inject.Any;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

public class QualifiersTest {

    @Test
    public void testIsSubset() {
        Set<Annotation> observed = ImmutableSet.of(Initialized.Literal.REQUEST, Any.Literal.INSTANCE);
        Set<Annotation> event = ImmutableSet.of(Initialized.Literal.APPLICATION, Any.Literal.INSTANCE);
        assertFalse(Qualifiers.isSubset(observed, event, Collections.emptyMap()));

        observed = ImmutableSet.of(Initialized.Literal.APPLICATION, Any.Literal.INSTANCE);
        assertTrue(Qualifiers.isSubset(observed, event, Collections.emptyMap()));

        observed = ImmutableSet.of(Any.Literal.INSTANCE);
        assertTrue(Qualifiers.isSubset(observed, event, Collections.emptyMap()));

        observed = ImmutableSet.of(Initialized.Literal.APPLICATION);
        assertTrue(Qualifiers.isSubset(observed, event, Collections.emptyMap()));
    }

}
