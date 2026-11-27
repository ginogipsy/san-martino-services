package com.ginogipsy.sanmartino.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceIdResponseFilterTest {

    @Test
    void addsTraceIdHeaderWhenSpanIsActive() throws ServletException, IOException {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");

        TraceIdResponseFilter filter = new TraceIdResponseFilter(tracer, "X-Trace-Id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void deduplicatesHeaderAddedByDownstreamFilterChain() throws ServletException, IOException {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");

        TraceIdResponseFilter filter = new TraceIdResponseFilter(tracer, "X-Trace-Id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, (req, res) ->
                ((jakarta.servlet.http.HttpServletResponse) res).addHeader("X-Trace-Id", "4bf92f3577b34da6a3ce929d0e0e4736"));

        assertThat(response.getHeaders("X-Trace-Id")).containsExactly("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void doesNothingWhenNoSpanIsActive() throws ServletException, IOException {
        Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);

        TraceIdResponseFilter filter = new TraceIdResponseFilter(tracer, "X-Trace-Id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getHeader("X-Trace-Id")).isNull();
    }

    @Test
    void doesNothingWhenHeaderNameIsBlank() throws ServletException, IOException {
        Tracer tracer = mock(Tracer.class);
        TraceIdResponseFilter filter = new TraceIdResponseFilter(tracer, "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getHeaderNames()).isEmpty();
    }

    @Test
    void autoConfigurationRegistersFilterWithoutOrderingSensitivity() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class))
                .run(context -> assertThat(context).hasBean("traceIdResponseFilterRegistration"));
    }

    @Test
    void populatesXTraceIdHeaderEndToEndWithOpenTelemetryAndServerHttpObservationFilter() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration.class,
                        org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration.class,
                        org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration.class,
                        org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration.class,
                        ObservabilityAutoConfiguration.class))
                .withPropertyValues("management.tracing.sampling.probability=1.0")
                .run(context -> {
                    var obsFilter = new org.springframework.web.filter.ServerHttpObservationFilter(
                            context.getBean(io.micrometer.observation.ObservationRegistry.class));
                    @SuppressWarnings("unchecked")
                    var traceFilterReg = (org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>)
                            context.getBean("traceIdResponseFilterRegistration");

                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/events");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    obsFilter.doFilter(request, response, (req, res) ->
                            traceFilterReg.getFilter().doFilter(req, res, new MockFilterChain()));

                    assertThat(response.getHeader("X-Trace-Id"))
                            .isNotBlank()
                            .matches("[0-9a-f]{32}");
                });
    }
}



