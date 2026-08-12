package com.ginogipsy.sanmartino.observability;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class LogValueFormatterTest {

    private static final List<String> SENSITIVE = List.of("password", "token", "secret");

    private final LogValueFormatter formatter = new LogValueFormatter(SENSITIVE, 300);

    @Test
    void masksArgumentsWhoseNameLooksSensitive() {
        assertThat(formatter.format("password", "hunter2", false)).isEqualTo(LogValueFormatter.MASK);
        assertThat(formatter.format("rawPassword", "hunter2", false)).isEqualTo(LogValueFormatter.MASK);
        assertThat(formatter.format("clientSecret", "abc", false)).isEqualTo(LogValueFormatter.MASK);
        assertThat(formatter.format("username", "gino", false)).isEqualTo("gino");
    }

    @Test
    void masksArgumentsMarkedByAnnotation() {
        assertThat(formatter.format("credentials", "hunter2", true)).isEqualTo(LogValueFormatter.MASK);
    }

    @Test
    void masksSensitiveAssignmentsInsideRenderedObjects() {
        String rendered = formatter.format(new LombokLikeRequest("gino", "hunter2"));

        assertThat(rendered).contains("username=gino");
        assertThat(rendered).contains("password=" + LogValueFormatter.MASK);
        assertThat(rendered).doesNotContain("hunter2");
    }

    @Test
    void masksSensitiveAssignmentsInJsonLikeText() {
        String rendered = formatter.format("{\"username\":\"gino\",\"password\":\"hunter2\"}");

        assertThat(rendered).doesNotContain("hunter2");
        assertThat(rendered).contains("gino");
    }

    @Test
    void masksSensitiveMapKeys() {
        String rendered = formatter.format(Map.of("token", "abc123"));

        assertThat(rendered).doesNotContain("abc123");
        assertThat(rendered).contains("token=" + LogValueFormatter.MASK);
    }

    @Test
    void rendersJpaEntitiesAsIdOnly() {
        UUID id = UUID.randomUUID();

        String rendered = formatter.format(new SampleEntity(id, "Festa di San Martino"));

        assertThat(rendered).isEqualTo("SampleEntity(id=" + id + ")");
        assertThat(rendered).doesNotContain("Festa");
    }

    @Test
    void doesNotTouchCollectionsThatAreNotFromTheJdk() {
        // Simula una lazy collection Hibernate: qualunque accesso al contenuto
        // (size() incluso) scatenerebbe una query o una LazyInitializationException.
        String rendered = formatter.format(new ExplodingCollection());

        assertThat(rendered).isEqualTo("ExplodingCollection(...)");
    }

    @Test
    void limitsTheNumberOfRenderedElements() {
        List<Integer> numbers = IntStream.rangeClosed(1, 12).boxed().toList();

        assertThat(formatter.format(numbers)).isEqualTo("[1, 2, 3, 4, 5, ... +7 more]");
    }

    @Test
    void rendersArraysAndNestedStructures() {
        assertThat(formatter.format(new int[]{1, 2, 3})).isEqualTo("[1, 2, 3]");
        assertThat(formatter.format(List.of(List.of("a", "b")))).isEqualTo("[[a, b]]");
        assertThat(formatter.format(new byte[]{1, 2, 3})).isEqualTo("byte[3]");
    }

    @Test
    void keepsOneEventOnOneLine() {
        // I toString() di openapi-generator sono multi-riga.
        String rendered = formatter.format("class Event {\n    id: 42\n    name: Sagra\n}");

        assertThat(rendered).doesNotContain("\n");
        assertThat(rendered).isEqualTo("class Event { id: 42 name: Sagra }");
    }

    @Test
    void truncatesValuesLongerThanTheConfiguredLimit() {
        LogValueFormatter shortFormatter = new LogValueFormatter(SENSITIVE, 10);

        String rendered = shortFormatter.format("0123456789abcdef");

        assertThat(rendered).isEqualTo("0123456789...(16 chars)");
    }

    @Test
    void rendersNullAndOptional() {
        assertThat(formatter.format(null)).isEqualTo("null");
        assertThat(formatter.format(Optional.empty())).isEqualTo("empty");
        assertThat(formatter.format(Optional.of(7))).isEqualTo("7");
    }

    @Test
    void withoutSensitiveNamesNothingIsMasked() {
        LogValueFormatter permissive = new LogValueFormatter(List.of(), 300);

        assertThat(permissive.format("password", "hunter2", false)).isEqualTo("hunter2");
        assertThat(permissive.format("a=1, b=2")).isEqualTo("a=1, b=2");
    }

    /** Riproduce la forma di un {@code toString()} generato da Lombok. */
    private record LombokLikeRequest(String username, String password) {

        @Override
        public String toString() {
            return "RegistrationRequest(username=" + username + ", password=" + password + ")";
        }
    }

    @Entity
    static class SampleEntity {

        @Id
        private UUID id;
        private String name;

        SampleEntity(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return "SampleEntity(id=" + id + ", name=" + name + ")";
        }
    }

    private static final class ExplodingCollection extends AbstractCollection<String> {

        @Override
        public Iterator<String> iterator() {
            throw new IllegalStateException("collection touched");
        }

        @Override
        public int size() {
            throw new IllegalStateException("collection touched");
        }
    }
}
