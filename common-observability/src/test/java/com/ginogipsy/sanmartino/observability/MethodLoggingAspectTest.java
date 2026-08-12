package com.ginogipsy.sanmartino.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ginogipsy.sanmartino.sample.SampleFacade;
import com.ginogipsy.sanmartino.sample.SampleNotFoundException;
import com.ginogipsy.sanmartino.sample.SampleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MethodLoggingAspectTest {

    private static final String SAMPLE_PACKAGE_LOGGER = "com.ginogipsy.sanmartino.sample";

    /** Le durate reali sono 0 o 1 ms a seconda della macchina: normalizzarle evita test flaky. */
    private static final Pattern DURATION = Pattern.compile("\\d+ ms");

    private ListAppender<ILoggingEvent> appender;
    private Logger packageLogger;
    private Level previousLevel;

    @BeforeEach
    void captureLogs() {
        appender = new ListAppender<>();
        appender.start();
        packageLogger = (Logger) LoggerFactory.getLogger(SAMPLE_PACKAGE_LOGGER);
        previousLevel = packageLogger.getLevel();
        packageLogger.setLevel(Level.DEBUG);
        packageLogger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        packageLogger.detachAppender(appender);
        packageLogger.setLevel(previousLevel);
        appender.stop();
    }

    @Test
    void logsEntryExitAndDuration() {
        SampleService service = proxy(new SampleService(), defaults());

        assertThat(service.greet("gino")).isEqualTo("ciao gino");

        assertThat(messages()).containsExactly(
                ">> SampleService.greet(name=gino)",
                "<< SampleService.greet completed in N ms -> ciao gino");
    }

    @Test
    void putsOperationDurationAndOutcomeInMdc() {
        SampleService service = proxy(new SampleService(), defaults());

        service.greet("gino");

        ILoggingEvent exit = appender.list.get(1);
        assertThat(exit.getMDCPropertyMap())
                .containsEntry(MethodLoggingAspect.MDC_OPERATION, "SampleService.greet")
                .containsEntry(MethodLoggingAspect.MDC_OUTCOME, "success")
                .containsKey(MethodLoggingAspect.MDC_DURATION);
        // L'ingresso non porta i campi dell'esito: MDC viene popolato solo sulla riga di uscita.
        assertThat(appender.list.getFirst().getMDCPropertyMap())
                .doesNotContainKey(MethodLoggingAspect.MDC_OUTCOME);
    }

    @Test
    void masksSensitiveArgumentsByNameAndByAnnotation() {
        SampleService service = proxy(new SampleService(), defaults());

        service.login("gino", "hunter2");
        service.rotate("very-secret");

        assertThat(messages()).contains(
                ">> SampleService.login(username=gino, password=***)",
                ">> SampleService.rotate(credentials=***)");
        assertThat(messages()).noneMatch(message -> message.contains("hunter2") || message.contains("very-secret"));
    }

    @Test
    void logsNoReturnValueForVoidMethods() {
        SampleService service = proxy(new SampleService(), defaults());

        service.nothing();

        assertThat(messages()).containsExactly(
                ">> SampleService.nothing()",
                "<< SampleService.nothing completed in N ms");
    }

    @Test
    void canBeConfiguredToOmitArguments() {
        SampleService service = proxy(new SampleService(), logging(false, true, Duration.ofSeconds(1)));

        service.greet("gino");

        assertThat(messages().getFirst()).isEqualTo(">> SampleService.greet(...)");
    }

    @Test
    void logsExpectedDomainExceptionsAtWarnWithoutStackTrace() {
        SampleService service = proxy(new SampleService(), defaults());

        assertThatThrownBy(service::expected).isInstanceOf(SampleNotFoundException.class);

        ILoggingEvent failure = eventsAt(Level.WARN).getFirst();
        assertThat(normalize(failure.getFormattedMessage()))
                .isEqualTo("!! SampleService.expected failed after N ms: SampleNotFoundException: id 42");
        assertThat(failure.getThrowableProxy()).isNull();
        assertThat(failure.getMDCPropertyMap()).containsEntry(MethodLoggingAspect.MDC_OUTCOME, "failure");
    }

    @Test
    void logsUnexpectedExceptionsAtErrorWithStackTrace() {
        SampleService service = proxy(new SampleService(), defaults());

        assertThatThrownBy(service::unexpected).isInstanceOf(IllegalStateException.class);

        ILoggingEvent failure = eventsAt(Level.ERROR).getFirst();
        assertThat(normalize(failure.getFormattedMessage()))
                .isEqualTo("!! SampleService.unexpected failed after N ms: IllegalStateException");
        assertThat(failure.getThrowableProxy()).isNotNull();
    }

    @Test
    void logsTheStackTraceOnlyOnceWhenAnExceptionCrossesSeveralLayers() {
        MethodLoggingAspect aspect = new MethodLoggingAspect(defaults());
        SampleService service = proxy(new SampleService(), aspect);
        SampleFacade facade = proxy(new SampleFacade(service), aspect);

        assertThatThrownBy(facade::boom).isInstanceOf(IllegalStateException.class);

        assertThat(eventsAt(Level.ERROR)).hasSize(1);
        assertThat(normalize(eventsAt(Level.ERROR).getFirst().getFormattedMessage()))
                .isEqualTo("!! SampleService.unexpected failed after N ms: IllegalStateException");
        assertThat(messages()).contains("!! SampleFacade.boom propagated IllegalStateException after N ms");
    }

    @Test
    void logsSlowInvocationsAtWarn() {
        SampleService service = proxy(new SampleService(), logging(true, true, Duration.ZERO));

        service.greet("gino");

        assertThat(eventsAt(Level.WARN)).hasSize(1);
        assertThat(normalize(eventsAt(Level.WARN).getFirst().getFormattedMessage()))
                .isEqualTo("<< SampleService.greet completed in N ms, over the N ms threshold -> ciao gino");
    }

    @Test
    void staysSilentWhenDebugIsDisabledAndNothingGoesWrong() {
        packageLogger.setLevel(Level.INFO);
        SampleService service = proxy(new SampleService(), defaults());

        assertThat(service.greet("gino")).isEqualTo("ciao gino");

        assertThat(appender.list).isEmpty();
    }

    private static ObservabilityProperties.Logging defaults() {
        return logging(true, true, Duration.ofSeconds(1));
    }

    private static ObservabilityProperties.Logging logging(boolean arguments, boolean returnValue, Duration slow) {
        return new ObservabilityProperties.Logging(
                true, arguments, returnValue, 300, slow,
                List.of("NotFoundException"),
                List.of("password", "secret", "token", "credential", "credentials"));
    }

    private static <T> T proxy(T target, ObservabilityProperties.Logging config) {
        return proxy(target, new MethodLoggingAspect(config));
    }

    private static <T> T proxy(T target, MethodLoggingAspect aspect) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    private static String normalize(String message) {
        return DURATION.matcher(message).replaceAll("N ms");
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).map(MethodLoggingAspectTest::normalize).toList();
    }

    private List<ILoggingEvent> eventsAt(Level level) {
        return appender.list.stream().filter(event -> event.getLevel() == level).toList();
    }
}
