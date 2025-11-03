package org.eclipse.edc.eonax.telemetry.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight JPA manager for Jetty
 */
public final class JpaUtil {

    private static EntityManagerFactory emf;

    private JpaUtil() {
    }

    public static void init(String persistenceUnitName, String datasourceDefaultUrl, String datasourceDefaultUser, String datasourceDefaultPassword) {
        Map<String, Object> props = new HashMap<>();

        props.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver");
        props.put("jakarta.persistence.jdbc.url", datasourceDefaultUrl);
        props.put("jakarta.persistence.jdbc.user", datasourceDefaultUser);
        props.put("jakarta.persistence.jdbc.password", datasourceDefaultPassword);

        if (emf == null) {
            emf = Persistence.createEntityManagerFactory(persistenceUnitName, props);
        }
    }

    public static EntityManagerFactory getEntityManagerFactory() {
        if (emf == null) {
            throw new IllegalStateException("JpaUtil not initialized. Call JpaUtil.init() first.");
        }
        return emf;
    }

    public static EntityManager createEntityManager() {
        return getEntityManagerFactory().createEntityManager();
    }

    public static void shutdown() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }
}
