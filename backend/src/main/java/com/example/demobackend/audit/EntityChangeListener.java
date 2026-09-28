package com.example.demobackend.audit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.example.demobackend.audit.AuditEvent.EntityChange;
import com.example.demobackend.audit.AuditEvent.FieldChange;
import com.example.demobackend.audit.AuditEvent.Operation;
import com.example.demobackend.audit.annotation.AuditIgnore;
import com.example.demobackend.audit.annotation.AuditedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.stereotype.Component;

/**
 * Turns Hibernate's post-insert/update/delete events of {@link AuditedEntity} classes into
 * field-level diffs. Hibernate already knows the old and new state of every flushed entity,
 * so no business code has to capture "before" values.
 */
@Component
class EntityChangeListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private static final Logger log = LoggerFactory.getLogger(EntityChangeListener.class);

    /** Bookkeeping fields that change on every write and would only add noise. */
    private static final List<Class<? extends Annotation>> IGNORED_FIELD_ANNOTATIONS = List.of(
            AuditIgnore.class, CreationTimestamp.class, UpdateTimestamp.class,
            CreatedDate.class, LastModifiedDate.class, Version.class);

    private final AuditChangeRecorder recorder;
    private final int maxValueLength;
    private final Map<Class<?>, Set<String>> ignoredFieldsByType = new ConcurrentHashMap<>();

    EntityChangeListener(AuditChangeRecorder recorder, AuditProperties properties) {
        this.recorder = recorder;
        this.maxValueLength = properties.maxValueLength();
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        record(event.getPersister(), event.getId(), Operation.INSERT, null, event.getState(), null, event.getSession());
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        record(event.getPersister(), event.getId(), Operation.UPDATE, event.getOldState(), event.getState(),
                event.getDirtyProperties(), event.getSession());
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        record(event.getPersister(), event.getId(), Operation.DELETE, event.getDeletedState(), null, null,
                event.getSession());
    }

    private void record(EntityPersister persister, Object id, Operation operation, Object[] oldState,
            Object[] newState, int[] dirtyProperties, SharedSessionContractImplementor session) {
        Class<?> type = persister.getMappedClass();
        if (!type.isAnnotationPresent(AuditedEntity.class)) {
            return;
        }
        try {
            List<FieldChange> fields = diff(type, persister.getPropertyNames(), oldState, newState, dirtyProperties,
                    session);
            if (!fields.isEmpty()) {
                recorder.record(new EntityChange(type.getSimpleName(), String.valueOf(id), operation, fields));
            }
        } catch (RuntimeException ex) {
            // Never let auditing fail the flush of a business transaction.
            log.warn("Could not audit {} of {}#{}", operation, type.getSimpleName(), id, ex);
        }
    }

    private List<FieldChange> diff(Class<?> type, String[] names, Object[] oldState, Object[] newState,
            int[] dirtyProperties, SharedSessionContractImplementor session) {
        Set<String> ignored = ignoredFieldsByType.computeIfAbsent(type, EntityChangeListener::ignoredFields);
        List<FieldChange> fields = new ArrayList<>();
        for (int i : propertyIndexes(dirtyProperties, names.length)) {
            Object before = oldState == null ? null : oldState[i];
            Object after = newState == null ? null : newState[i];
            if (ignored.contains(names[i]) || before instanceof Collection || after instanceof Collection
                    || Objects.deepEquals(before, after)) {
                continue;
            }
            fields.add(new FieldChange(names[i], format(before, session), format(after, session)));
        }
        return fields;
    }

    private static int[] propertyIndexes(int[] dirtyProperties, int propertyCount) {
        if (dirtyProperties != null) {
            return dirtyProperties;
        }
        int[] all = new int[propertyCount];
        for (int i = 0; i < propertyCount; i++) {
            all[i] = i;
        }
        return all;
    }

    private String format(Object value, SharedSessionContractImplementor session) {
        if (value == null) {
            return null;
        }
        if (value instanceof HibernateProxy proxy) {
            value = proxy.getHibernateLazyInitializer().getIdentifier();
        } else if (value.getClass().isAnnotationPresent(Entity.class)) {
            value = session.getFactory().getPersistenceUnitUtil().getIdentifier(value); // store references by id
        }
        String text = value.toString();
        return text.length() <= maxValueLength ? text : text.substring(0, maxValueLength) + "…";
    }

    private static Set<String> ignoredFields(Class<?> type) {
        Set<String> names = new HashSet<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (IGNORED_FIELD_ANNOTATIONS.stream().anyMatch(field::isAnnotationPresent)) {
                    names.add(field.getName());
                }
            }
        }
        return Set.copyOf(names);
    }
}
