package com.ginogipsy.sanmartino.sample;

import org.springframework.stereotype.Service;

/**
 * Secondo livello intercettato, per verificare cosa accade quando un'eccezione
 * attraversa più join point: la stack trace va loggata una volta sola.
 */
@Service
public class SampleFacade {

    private final SampleService delegate;

    public SampleFacade(SampleService delegate) {
        this.delegate = delegate;
    }

    public String greet(String name) {
        return delegate.greet(name);
    }

    public void boom() {
        delegate.unexpected();
    }
}
