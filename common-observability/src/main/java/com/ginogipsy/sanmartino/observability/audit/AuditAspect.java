package com.ginogipsy.sanmartino.observability.audit;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aspect AOP che intercetta i metodi annotati con {@link Auditable} ed emette
 * un evento {@link AuditEvent} con i dati dell'operazione di business.
 */
@Aspect
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final String serviceId;
    private final String correlationMdcKey;
    private final AuditPublisher auditPublisher;
    private final AuditUserResolver auditUserResolver;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public AuditAspect(String serviceId,
                       String correlationMdcKey,
                       AuditPublisher auditPublisher,
                       AuditUserResolver auditUserResolver) {
        this.serviceId = serviceId != null && !serviceId.isBlank() ? serviceId : "unknown-service";
        this.correlationMdcKey = correlationMdcKey != null ? correlationMdcKey : "correlationId";
        this.auditPublisher = auditPublisher;
        this.auditUserResolver = auditUserResolver;
    }

    @AfterReturning(
            pointcut = "@annotation(auditable) || (@within(auditable) && execution(public * *(..)))",
            returning = "result",
            argNames = "joinPoint,auditable,result"
    )
    public void onAuditSuccess(JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();

            // Se l'annotazione sul metodo sovrascrive quella a livello di classe
            Auditable effectiveAuditable = method.isAnnotationPresent(Auditable.class)
                    ? method.getAnnotation(Auditable.class)
                    : auditable;

            if (effectiveAuditable == null) {
                return;
            }

            EvaluationContext context = buildEvaluationContext(joinPoint, signature, result);

            String description = evaluateSpel(effectiveAuditable.description(), context);
            String resourceId = evaluateSpel(effectiveAuditable.resourceId(), context);

            AuditUserResolver.AuditUser user = auditUserResolver.resolveUser();
            String correlationId = MDC.get(correlationMdcKey);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("method", signature.getDeclaringTypeName() + "." + signature.getName());

            AuditEvent event = new AuditEvent(
                    UUID.randomUUID().toString(),
                    serviceId,
                    effectiveAuditable.action(),
                    user.userId(),
                    user.username(),
                    resourceId,
                    description,
                    correlationId,
                    Instant.now(),
                    metadata
            );

            auditPublisher.publish(event);
        } catch (Exception ex) {
            log.error("Errore durante la registrazione dell'evento di audit: {}", ex.getMessage(), ex);
        }
    }

    private EvaluationContext buildEvaluationContext(JoinPoint joinPoint, MethodSignature signature, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("result", result);

        Object[] args = joinPoint.getArgs();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(signature.getMethod());

        if (paramNames != null && args != null) {
            for (int i = 0; i < Math.min(paramNames.length, args.length); i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        return context;
    }

    private String evaluateSpel(String expression, EvaluationContext context) {
        if (expression == null || expression.isBlank()) {
            return "";
        }
        try {
            Object val = parser.parseExpression(expression).getValue(context);
            return val != null ? String.valueOf(val) : "";
        } catch (Exception ex) {
            log.trace("Valutazione SpEL fallita per '{}': {}", expression, ex.getMessage());
            return expression;
        }
    }
}
