package com.ginogipsy.sanmartino.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    private final CorrelationIdFilter filter = new CorrelationIdFilter(HEADER, MDC_KEY);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesACorrelationIdWhenTheRequestHasNone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInMdc = new AtomicReference<>();

        filter.doFilter(get("/v1/events"), response, chain(request -> seenInMdc.set(MDC.get(MDC_KEY))));

        assertThat(seenInMdc.get()).isNotBlank();
        assertThat(response.getHeader(HEADER)).isEqualTo(seenInMdc.get());
        assertThat(UUID.fromString(seenInMdc.get())).isNotNull();
    }

    @Test
    void keepsTheIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = get("/v1/events");
        request.addHeader(HEADER, "abc-123_ok.42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInMdc = new AtomicReference<>();

        filter.doFilter(request, response, chain(forwarded -> seenInMdc.set(MDC.get(MDC_KEY))));

        assertThat(seenInMdc.get()).isEqualTo("abc-123_ok.42");
        assertThat(response.getHeader(HEADER)).isEqualTo("abc-123_ok.42");
    }

    @Test
    void replacesAnIncomingIdThatCouldBeUsedForLogInjection() throws Exception {
        MockHttpServletRequest request = get("/v1/events");
        request.addHeader(HEADER, "evil\r\nlog.level=ERROR");
        AtomicReference<String> seenInMdc = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), chain(forwarded -> seenInMdc.set(MDC.get(MDC_KEY))));

        assertThat(seenInMdc.get()).doesNotContain("evil");
        assertThat(UUID.fromString(seenInMdc.get())).isNotNull();
    }

    @Test
    void replacesAnIncomingIdThatIsTooLong() throws Exception {
        MockHttpServletRequest request = get("/v1/events");
        request.addHeader(HEADER, "x".repeat(65));
        AtomicReference<String> seenInMdc = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), chain(forwarded -> seenInMdc.set(MDC.get(MDC_KEY))));

        assertThat(seenInMdc.get()).hasSize(36);
    }

    @Test
    void exposesTheGeneratedIdOnTheForwardedRequest() throws Exception {
        // È il pezzo che fa funzionare la correlazione attraverso il gateway:
        // Spring Cloud Gateway inoltra gli header della richiesta che riceve.
        AtomicReference<String> headerDownstream = new AtomicReference<>();
        AtomicReference<Boolean> listedAmongNames = new AtomicReference<>();

        filter.doFilter(get("/v1/events"), new MockHttpServletResponse(), chain(forwarded -> {
            headerDownstream.set(forwarded.getHeader("x-correlation-id"));
            listedAmongNames.set(Collections.list(forwarded.getHeaderNames()).contains(HEADER));
        }));

        assertThat(headerDownstream.get()).isNotBlank();
        assertThat(listedAmongNames.get()).isTrue();
    }

    @Test
    void putsHttpMethodAndPathInMdcAndCleansUpAfterwards() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();

        filter.doFilter(get("/v1/events/42"), new MockHttpServletResponse(), chain(forwarded -> {
            method.set(MDC.get(CorrelationIdFilter.MDC_HTTP_METHOD));
            path.set(MDC.get(CorrelationIdFilter.MDC_HTTP_PATH));
        }));

        assertThat(method.get()).isEqualTo("GET");
        assertThat(path.get()).isEqualTo("/v1/events/42");
        // Nessun residuo sul thread: sarebbe un id sbagliato sulla richiesta successiva.
        assertThat(MDC.get(MDC_KEY)).isNull();
        assertThat(MDC.get(CorrelationIdFilter.MDC_HTTP_METHOD)).isNull();
    }

    private static MockHttpServletRequest get(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private static FilterChain chain(ChainAssertion assertion) {
        return new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                assertion.check((HttpServletRequest) request);
            }
        };
    }

    @FunctionalInterface
    private interface ChainAssertion {
        void check(HttpServletRequest request);
    }
}
