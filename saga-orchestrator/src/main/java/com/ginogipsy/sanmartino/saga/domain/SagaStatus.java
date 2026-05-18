package com.ginogipsy.sanmartino.saga.domain;

public enum SagaStatus {
    STARTED,
    COMPLETED,
    FAILED,
    COMPENSATING,
    COMPENSATED
}
