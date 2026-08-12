package com.ginogipsy.sanmartino.observability;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.util.Collection;
import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rende argomenti e valori di ritorno in una forma adatta al log.
 *
 * <p>Tre garanzie, in ordine di importanza:
 *
 * <ol>
 *   <li><b>Non fa uscire segreti.</b> Maschera per nome (parametro, campo, chiave di mappa) e,
 *       come seconda rete, per pattern {@code chiave=valore} sulla stringa già renderizzata —
 *       così copre anche i {@code toString()} generati da Lombok, dove il nome del parametro
 *       non basterebbe ({@code RegistrationRequest(username=x, password=y)}).</li>
 *   <li><b>Non cambia il comportamento dell'applicazione.</b> Sulle entity JPA stampa solo l'id
 *       (letto per reflection dal campo, non dal getter) e non itera le collezioni che non sono
 *       del JDK: una lazy collection Hibernate renderizzata scatenerebbe una query o una
 *       {@code LazyInitializationException}. Il logging non deve avere effetti collaterali.</li>
 *   <li><b>Non fa esplodere il volume dei log.</b> Tronca, limita il numero di elementi delle
 *       collezioni e collassa gli spazi: i {@code toString()} di openapi-generator sono
 *       multi-riga, e un evento di log deve restare una riga.</li>
 * </ol>
 */
final class LogValueFormatter {

    static final String MASK = "***";

    private static final String NULL = "null";
    private static final String OPAQUE_SUFFIX = "(...)";
    private static final int MAX_ELEMENTS = 5;
    private static final int MAX_DEPTH = 3;
    private static final String JPA_ENTITY_ANNOTATION = "jakarta.persistence.Entity";
    private static final String ID_FIELD = "id";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final List<String> sensitiveNames;
    private final Pattern sensitiveAssignment;
    private final int maxLength;

    LogValueFormatter(List<String> sensitiveNames, int maxLength) {
        this.sensitiveNames = sensitiveNames.stream()
                .filter(name -> !name.isBlank())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
        this.sensitiveAssignment = buildAssignmentPattern(this.sensitiveNames);
        this.maxLength = maxLength;
    }

    /**
     * Formatta un argomento di cui si conosce il nome.
     *
     * @param parameterName nome del parametro, confrontato con i nomi sensibili
     * @param value         valore da renderizzare
     * @param masked        {@code true} se il parametro è annotato {@link Masked}
     */
    String format(String parameterName, Object value, boolean masked) {
        if (masked || isSensitiveName(parameterName)) {
            return MASK;
        }
        return format(value);
    }

    /** Formatta un valore di cui non si conosce il nome (tipicamente il valore di ritorno). */
    String format(Object value) {
        return truncate(maskAssignments(collapseWhitespace(render(value, 0))));
    }

    boolean isSensitiveName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return sensitiveNames.stream().anyMatch(lower::contains);
    }

    private String render(Object value, int depth) {
        if (value == null) {
            return NULL;
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(inner -> render(inner, depth)).orElse("empty");
        }
        if (value instanceof CharSequence || isScalar(value)) {
            return String.valueOf(value);
        }
        if (value instanceof char[] chars) {
            return "char[" + chars.length + "]";
        }
        if (value instanceof byte[] bytes) {
            return "byte[" + bytes.length + "]";
        }
        if (isJpaEntity(value.getClass())) {
            return entityReference(value);
        }
        if (depth >= MAX_DEPTH) {
            return value.getClass().getSimpleName() + OPAQUE_SUFFIX;
        }
        return renderContainer(value, depth);
    }

    private String renderContainer(Object value, int depth) {
        // La verifica "è del JDK" precede qualsiasi accesso al contenuto: size() su una
        // PersistentBag Hibernate non inizializzata farebbe partire una query.
        boolean fromJdk = value.getClass().getName().startsWith("java.");
        if (value instanceof Collection<?> collection) {
            return fromJdk ? renderCollection(collection, depth) : opaque(value);
        }
        if (value instanceof Map<?, ?> map) {
            return fromJdk ? renderMap(map, depth) : opaque(value);
        }
        if (value.getClass().isArray()) {
            return renderArray(value, depth);
        }
        return String.valueOf(value);
    }

    private String renderCollection(Collection<?> collection, int depth) {
        int size = collection.size();
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        int index = 0;
        for (Object element : collection) {
            if (index++ == MAX_ELEMENTS) {
                joiner.add(more(size));
                break;
            }
            joiner.add(render(element, depth + 1));
        }
        return joiner.toString();
    }

    private String renderMap(Map<?, ?> map, int depth) {
        int size = map.size();
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        int index = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (index++ == MAX_ELEMENTS) {
                joiner.add(more(size));
                break;
            }
            String key = String.valueOf(entry.getKey());
            joiner.add(key + "=" + (isSensitiveName(key) ? MASK : render(entry.getValue(), depth + 1)));
        }
        return joiner.toString();
    }

    private String renderArray(Object array, int depth) {
        int size = Array.getLength(array);
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (int index = 0; index < size; index++) {
            if (index == MAX_ELEMENTS) {
                joiner.add(more(size));
                break;
            }
            joiner.add(render(Array.get(array, index), depth + 1));
        }
        return joiner.toString();
    }

    private static String more(int size) {
        return "... +" + (size - MAX_ELEMENTS) + " more";
    }

    private static String opaque(Object value) {
        return value.getClass().getSimpleName() + OPAQUE_SUFFIX;
    }

    private static boolean isScalar(Object value) {
        return value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof UUID
                || value instanceof Temporal
                || value instanceof TemporalAmount
                || value instanceof Date
                || value instanceof URI
                || value instanceof URL
                || value instanceof Path
                || value instanceof Locale
                || value instanceof Currency
                || value instanceof Class<?>;
    }

    private static boolean isJpaEntity(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Annotation annotation : current.getAnnotations()) {
                if (JPA_ENTITY_ANNOTATION.equals(annotation.annotationType().getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String entityReference(Object entity) {
        return entity.getClass().getSimpleName() + "(id=" + readId(entity) + ")";
    }

    private static Object readId(Object entity) {
        for (Class<?> current = entity.getClass(); current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(ID_FIELD);
                if (Modifier.isStatic(field.getModifiers())) {
                    return null;
                }
                // Lettura diretta del campo: non passa dai getter, quindi su un proxy
                // Hibernate non forza l'inizializzazione (restituisce null e va bene così).
                field.setAccessible(true);
                return field.get(entity);
            } catch (NoSuchFieldException notHere) {
                // il campo può stare in una superclasse (es. una @MappedSuperclass): si continua a salire
            } catch (IllegalAccessException | SecurityException | InaccessibleObjectException inaccessible) {
                return null;
            }
        }
        return null;
    }

    private static Pattern buildAssignmentPattern(List<String> names) {
        if (names.isEmpty()) {
            return null;
        }
        String alternatives = names.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        // Copre `password=x`, `"password": "x"` e `password : x`.
        // Gruppo 1 = chiave + separatore (si conserva), gruppo 2 = valore (si maschera).
        return Pattern.compile("([\"']?(?:" + alternatives + ")[\"']?\\s*[=:]\\s*)([^,;)}\\]]+)",
                Pattern.CASE_INSENSITIVE);
    }

    private String maskAssignments(String text) {
        if (sensitiveAssignment == null || text.isEmpty()) {
            return text;
        }
        return sensitiveAssignment.matcher(text)
                .replaceAll(match -> Matcher.quoteReplacement(match.group(1) + MASK));
    }

    private static String collapseWhitespace(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    private String truncate(String text) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(" + text.length() + " chars)";
    }
}
