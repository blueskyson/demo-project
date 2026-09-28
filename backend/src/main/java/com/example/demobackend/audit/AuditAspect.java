package com.example.demobackend.audit;

import com.example.demobackend.audit.annotation.Audited;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Opens an {@link AuditScope} around every {@link Audited} method and hands the finished
 * {@link AuditEvent} to the {@link AuditPublisher}. Runs outermost so any transaction
 * started inside the method has committed (and its entity changes are known) when it finishes.
 * <p>
 * Auditing must never change the outcome of the business call: every audit step is guarded,
 * and the method's own result or exception is always passed through untouched.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditPublisher publisher;

    AuditAspect(AuditPublisher publisher) {
        this.publisher = publisher;
    }

    @Around("@annotation(com.example.demobackend.audit.annotation.Audited)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        if (AuditScope.current() != null) {
            return joinPoint.proceed(); // nested call: the outer scope already records everything
        }
        AuditScope scope = open(joinPoint);
        if (scope == null) {
            return joinPoint.proceed();
        }

        AuditScope.bind(scope);
        Throwable failure = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            AuditScope.unbind();
            finish(scope, failure);
        }
    }

    private AuditScope open(ProceedingJoinPoint joinPoint) {
        try {
            Audited audited = ((MethodSignature) joinPoint.getSignature()).getMethod().getAnnotation(Audited.class);
            return new AuditScope(audited.action());
        } catch (RuntimeException ex) {
            log.warn("Could not start audit for {}", joinPoint.getSignature().toShortString(), ex);
            return null;
        }
    }

    private void finish(AuditScope scope, Throwable failure) {
        try {
            scope.complete(failure);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                // Called inside a caller's transaction: wait for it, so we know whether it committed.
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            scope.rolledBack();
                        }
                        publisher.publish(scope.toEvent());
                    }
                });
            } else {
                publisher.publish(scope.toEvent());
            }
        } catch (RuntimeException ex) {
            log.warn("Could not finish audit", ex);
        }
    }
}
