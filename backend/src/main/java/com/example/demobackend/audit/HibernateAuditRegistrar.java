package com.example.demobackend.audit;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.context.annotation.Configuration;

/** Plugs {@link EntityChangeListener} into Hibernate's event system. */
@Configuration(proxyBeanMethods = false)
class HibernateAuditRegistrar {

    HibernateAuditRegistrar(EntityManagerFactory entityManagerFactory, EntityChangeListener listener) {
        EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry()
                .requireService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_INSERT, listener);
        registry.appendListeners(EventType.POST_UPDATE, listener);
        registry.appendListeners(EventType.POST_DELETE, listener);
    }
}
