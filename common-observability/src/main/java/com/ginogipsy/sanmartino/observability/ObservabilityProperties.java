package com.ginogipsy.sanmartino.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Configurazione dell'osservabilità applicativa, prefisso {@code sanmartino.observability}.
 *
 * <p>Record con constructor binding: i default stanno qui, non nei sei
 * {@code application.yaml}. Un servizio sovrascrive solo ciò che gli serve.
 *
 * @param enabled     interruttore generale: a {@code false} non viene registrato né l'aspect né il filtro
 * @param logging     comportamento dell'aspect di logging dei metodi
 * @param correlation propagazione del correlation id verso MDC e servizi a valle
 */
@ConfigurationProperties("sanmartino.observability")
public record ObservabilityProperties(

        @DefaultValue("true") boolean enabled,
        @DefaultValue Logging logging,
        @DefaultValue Correlation correlation
) {

    /**
     * @param enabled                   registra o no l'aspect di logging
     * @param logArguments              logga i parametri di input (mascherati) a livello DEBUG
     * @param logReturnValue            logga il valore di ritorno (mascherato) a livello DEBUG
     * @param maxValueLength            lunghezza massima di un valore renderizzato, oltre la quale viene troncato
     * @param slowThreshold             durata oltre la quale l'invocazione viene loggata a WARN invece che a DEBUG
     * @param expectedExceptionSuffixes suffissi di classi di eccezione considerate "attese" (errori di dominio,
     *                                  non guasti): loggate a WARN e senza stack trace. Il default copre le
     *                                  {@code *NotFoundException} dei servizi, che mappano su un 404 e non su un bug
     * @param sensitiveNames            frammenti di nome (parametro, campo, chiave di mappa) il cui valore va mascherato
     */
    public record Logging(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("true") boolean logArguments,
            @DefaultValue("true") boolean logReturnValue,
            @DefaultValue("300") int maxValueLength,
            @DefaultValue("1s") Duration slowThreshold,
            @DefaultValue({"NotFoundException"}) List<String> expectedExceptionSuffixes,
            @DefaultValue({
                    "password", "passwd", "secret", "token", "credential", "credentials",
                    "authorization", "apikey", "api-key", "privatekey", "pin", "otp"
            }) List<String> sensitiveNames
    ) {
    }

    /**
     * @param enabled    registra o no il filtro servlet che popola MDC
     * @param headerName header HTTP letto in ingresso e riemesso in risposta
     * @param mdcKey     chiave MDC, che nel log JSON diventa un campo di primo livello
     */
    public record Correlation(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("X-Correlation-Id") String headerName,
            @DefaultValue("correlationId") String mdcKey
    ) {
    }
}
