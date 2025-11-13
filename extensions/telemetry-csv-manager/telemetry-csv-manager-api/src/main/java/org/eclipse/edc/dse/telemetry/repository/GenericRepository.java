package org.eclipse.edc.dse.telemetry.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.util.List;

public abstract class GenericRepository<T> {

    protected final EntityManager em;
    private final Class<T> type;

    public GenericRepository(EntityManager em, Class<T> type) {
        this.em = em;
        this.type = type;
    }

    public void save(T entity) {
        em.persist(entity);
    }

    public void saveTransactional(T entity) {
        executeInTransaction(() -> em.persist(entity));
    }

    public void update(T entity) {
        em.merge(entity);
    }

    public void updateTransactional(T entity) {
        executeInTransaction(() -> em.merge(entity));
    }

    public void delete(T entity) {
        em.remove(em.contains(entity) ? entity : em.merge(entity));
    }

    public void deleteTransactional(T entity) {
        executeInTransaction(() -> em.remove(em.contains(entity) ? entity : em.merge(entity)));
    }

    public T find(Object id) {
        return em.find(type, id);
    }

    public List<T> findAll() {
        return em.createQuery("FROM " + type.getSimpleName(), type).getResultList();
    }

    /**
     * Ensures consistent transaction handling
     */
    public void executeInTransaction(Runnable action) {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            action.run();
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }
}
