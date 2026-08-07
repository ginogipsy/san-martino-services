package com.ginogipsy.sanmartino.observability;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un parametro (o il valore di ritorno di un metodo) come sensibile:
 * nei log compare {@code ***} invece del valore.
 *
 * <p>Il mascheramento per nome è già automatico ({@code password}, {@code token},
 * {@code secret}, … — vedi {@code sanmartino.observability.logging.sensitive-names}).
 * Questa annotazione copre i casi in cui il nome non lo suggerisce:
 *
 * <pre>{@code
 * public User register(String username, @Masked String plainCredentials) { … }
 * }</pre>
 */
@Target({ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Masked {
}
