package com.ginogipsy.sanmartino.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Parameter;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

/**
 * Logga l'esecuzione dei metodi dei layer rilevanti: controller REST, service e
 * qualunque classe o metodo annotato {@link Logged}.
 *
 * <p>Per ogni invocazione produce:
 * <ul>
 *   <li>a DEBUG, l'ingresso con i parametri mascherati;</li>
 *   <li>a DEBUG, l'uscita con durata in ms e valore di ritorno mascherato;</li>
 *   <li>a WARN, l'uscita quando la durata supera {@code slow-threshold} (default 1s);</li>
 *   <li>a WARN senza stack trace le eccezioni "attese" (errori di dominio, es. {@code *NotFoundException}),
 *       a ERROR con stack trace tutte le altre.</li>
 * </ul>
 *
 * <p>Il logger usato è quello della classe intercettata, non quello dell'aspect:
 * i livelli restano governabili per package ({@code logging.level.com.ginogipsy.sanmartino})
 * e in Loki la label {@code logger} continua a indicare il punto reale del codice.
 *
 * <p>Su ogni riga di uscita finiscono in MDC {@code operation}, {@code durationMs} e
 * {@code outcome}: con i log strutturati in JSON diventano campi di primo livello,
 * quindi filtrabili in Loki senza regex.
 */
@Aspect
@Order(MethodLoggingAspect.ORDER)
public class MethodLoggingAspect {

    /**
     * Precedenza maggiore del transaction advisor, che gira a
     * {@code Ordered.LOWEST_PRECEDENCE}: l'aspect avvolge la transazione, quindi la
     * durata misurata comprende flush e commit — il tempo che il chiamante percepisce.
     */
    static final int ORDER = 100;

    static final String MDC_OPERATION = "operation";
    static final String MDC_DURATION = "durationMs";
    static final String MDC_OUTCOME = "outcome";

    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_FAILURE = "failure";

    /**
     * Stato per thread della catena di invocazioni intercettate. Serve a due cose:
     * sapere quando la catena si chiude (per rimuovere il ThreadLocal e non lasciare
     * residui sui thread del pool) e ricordare quale eccezione è già stata loggata,
     * così un guasto che attraversa service e controller produce una sola stack trace.
     */
    private static final ThreadLocal<Invocation> CURRENT = new ThreadLocal<>();

    private final ObservabilityProperties.Logging config;
    private final LogValueFormatter formatter;
    private final long slowThresholdMillis;

    public MethodLoggingAspect(ObservabilityProperties.Logging config) {
        this.config = config;
        this.formatter = new LogValueFormatter(config.sensitiveNames(), config.maxValueLength());
        this.slowThresholdMillis = config.slowThreshold().toMillis();
    }

    /**
     * Solo codice di questo monorepo: senza questo vincolo l'aspect intercetterebbe
     * anche i bean di framework e librerie. La libreria di osservabilità è esclusa
     * per non intercettare se stessa.
     */
    @Pointcut("within(com.ginogipsy.sanmartino..*) && !within(com.ginogipsy.sanmartino.observability..*)")
    void projectCode() {
    }

    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)"
            + " || @within(org.springframework.web.bind.annotation.RestControllerAdvice)"
            + " || @within(org.springframework.stereotype.Service)"
            + " || @within(com.ginogipsy.sanmartino.observability.Logged)"
            + " || @annotation(com.ginogipsy.sanmartino.observability.Logged)")
    void observedLayer() {
    }

    @Around("projectCode() && observedLayer()")
    public Object logInvocation(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> targetClass = targetClass(joinPoint, signature);
        Logger log = LoggerFactory.getLogger(targetClass);
        String operation = targetClass.getSimpleName() + "." + signature.getName();

        Invocation invocation = enter();
        long startedAt = System.nanoTime();
        logEntry(log, operation, signature, joinPoint.getArgs());
        try {
            Object result = joinPoint.proceed();
            logSuccess(log, operation, signature, result, millisSince(startedAt));
            return result;
        } catch (Throwable failure) {
            // Rilanciata immediatamente: l'aspect osserva, non altera il flusso.
            // Vale anche per InterruptedException (java:S2142), che arriva intatta al chiamante.
            logFailure(log, operation, failure, millisSince(startedAt), invocation);
            throw failure;
        } finally {
            exit(invocation);
        }
    }

    private void logEntry(Logger log, String operation, MethodSignature signature, Object[] args) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(">> {}({})", operation, config.logArguments() ? arguments(signature, args) : "...");
    }

    private void logSuccess(Logger log, String operation, MethodSignature signature, Object result, long millis) {
        boolean slow = millis >= slowThresholdMillis;
        if (!slow && !log.isDebugEnabled()) {
            return;
        }
        try (MDC.MDCCloseable operationKey = MDC.putCloseable(MDC_OPERATION, operation);
             MDC.MDCCloseable durationKey = MDC.putCloseable(MDC_DURATION, Long.toString(millis));
             MDC.MDCCloseable outcomeKey = MDC.putCloseable(MDC_OUTCOME, OUTCOME_SUCCESS)) {
            String returned = returnValue(signature, result, log.isDebugEnabled());
            if (slow) {
                log.warn("<< {} completed in {} ms, over the {} ms threshold{}",
                        operation, millis, slowThresholdMillis, returned);
            } else {
                log.debug("<< {} completed in {} ms{}", operation, millis, returned);
            }
        }
    }

    private void logFailure(Logger log, String operation, Throwable failure, long millis, Invocation invocation) {
        try (MDC.MDCCloseable operationKey = MDC.putCloseable(MDC_OPERATION, operation);
             MDC.MDCCloseable durationKey = MDC.putCloseable(MDC_DURATION, Long.toString(millis));
             MDC.MDCCloseable outcomeKey = MDC.putCloseable(MDC_OUTCOME, OUTCOME_FAILURE)) {
            if (invocation.alreadyReported(failure)) {
                log.debug("!! {} propagated {} after {} ms",
                        operation, failure.getClass().getSimpleName(), millis);
                return;
            }
            invocation.markReported(failure);
            if (isExpected(failure)) {
                log.warn("!! {} failed after {} ms: {}: {}",
                        operation, millis, failure.getClass().getSimpleName(), failure.getMessage());
            } else {
                log.error("!! {} failed after {} ms: {}",
                        operation, millis, failure.getClass().getSimpleName(), failure);
            }
        }
    }

    private String returnValue(MethodSignature signature, Object result, boolean debugEnabled) {
        if (!config.logReturnValue() || !debugEnabled || signature.getReturnType() == void.class) {
            return "";
        }
        return " -> " + formatter.format(result);
    }

    private String arguments(MethodSignature signature, Object[] args) {
        if (args.length == 0) {
            return "";
        }
        Parameter[] parameters = signature.getMethod().getParameters();
        StringJoiner joiner = new StringJoiner(", ");
        for (int index = 0; index < args.length; index++) {
            Parameter parameter = index < parameters.length ? parameters[index] : null;
            String name = parameter != null ? parameter.getName() : "arg" + index;
            boolean masked = parameter != null && parameter.isAnnotationPresent(Masked.class);
            joiner.add(name + "=" + formatter.format(name, args[index], masked));
        }
        return joiner.toString();
    }

    private boolean isExpected(Throwable failure) {
        String name = failure.getClass().getSimpleName();
        return config.expectedExceptionSuffixes().stream().anyMatch(name::endsWith);
    }

    private static Class<?> targetClass(ProceedingJoinPoint joinPoint, MethodSignature signature) {
        Object target = joinPoint.getTarget();
        if (target == null) {
            return signature.getDeclaringType();
        }
        // getUserClass: senza questo il nome del logger sarebbe EventsController$$SpringCGLIB$$0.
        return ClassUtils.getUserClass(target.getClass());
    }

    private static long millisSince(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private static Invocation enter() {
        Invocation invocation = CURRENT.get();
        if (invocation == null) {
            invocation = new Invocation();
            CURRENT.set(invocation);
        }
        invocation.depth++;
        return invocation;
    }

    private static void exit(Invocation invocation) {
        if (--invocation.depth == 0) {
            CURRENT.remove();
        }
    }

    private static final class Invocation {

        private int depth;
        private Throwable reported;

        private boolean alreadyReported(Throwable failure) {
            return reported == failure;
        }

        private void markReported(Throwable failure) {
            this.reported = failure;
        }
    }
}
