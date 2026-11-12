package com.ginogipsy.sanmartino.observability.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Contrassegna un metodo o una classe per la registrazione automatica di un evento di Audit Log di business.
 *
 * <p>I campi {@link #description()} e {@link #resourceId()} supportano espressioni
 * Spring Expression Language (SpEL) valutate sul contesto dell'invocazione.
 * Le variabili disponibili sono:
 * <ul>
 *     <li>I parametri del metodo con il loro nome, es. {@code #standId}, {@code #request}</li>
 *     <li>{@code #result}: il valore restituito dal metodo</li>
 * </ul>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /**
     * Codice identificativo dell'azione di business (es. "STAND:PRICE_UPDATE", "EVENT:CREATE").
     */
    String action();

    /**
     * Descrizione dell'evento, supporta SpEL.
     * Esempio: {@code "'Aggiornato prezzo dello stand ' + #standId + ' a ' + #newPrice"}
     */
    String description() default "";

    /**
     * Identificativo della risorsa impattata, supporta SpEL.
     * Esempio: {@code "#standId"} oppure {@code "#result != null ? #result.id : null"}
     */
    String resourceId() default "";
}
