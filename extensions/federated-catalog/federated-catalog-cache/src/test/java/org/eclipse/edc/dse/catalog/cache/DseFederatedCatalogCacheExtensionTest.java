/*
 *  Copyright (c) 2026 Amadeus IT Group
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus IT Group - initial API and implementation
 *
 */

package org.eclipse.edc.dse.catalog.cache;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.runtime.core.validator.JsonObjectValidatorRegistryImpl;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Violation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.validator.spi.Violation.violation;
import static org.mockito.Mockito.mock;

/**
 * The cache built by {@link DseFederatedCatalogCacheExtension} uses the DSE {@code in} operator
 * ({@link DseInOperatorPredicate}): a list property matches when one of its elements is in the operand list.
 * The extension also registers the management v4 {@code QuerySpec} sort field validator.
 */
@ExtendWith(DependencyInjectionExtension.class)
class DseFederatedCatalogCacheExtensionTest {

    private final DseFederatedCatalogCacheExtension extension = new DseFederatedCatalogCacheExtension();
    private final JsonObjectValidatorRegistry validatorRegistry = new JsonObjectValidatorRegistryImpl();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(JsonObjectValidatorRegistry.class, validatorRegistry);
        context.registerService(JsonLd.class, new TitaniumJsonLd(mock(Monitor.class)));
    }

    @Test
    void initialize_registersTheSortFieldValidatorUnderTheV4QuerySpecType(DseFederatedCatalogCacheExtension injected, ServiceExtensionContext context) {
        injected.initialize(context);

        assertThat(DseFederatedCatalogCacheExtension.QUERY_SPEC_V4_VALIDATOR_TYPE).isEqualTo("v4:QuerySpec");
        var result = validatorRegistry.validate("v4:QuerySpec", querySpec("doesNotExist"));
        assertThat(result.getFailure().getViolations()).extracting(Violation::path).containsExactly("sortField");
        assertThat(validatorRegistry.validate("v4:QuerySpec", querySpec("id")).succeeded()).isTrue();
    }

    @Test
    void initialize_keepsTheOtherValidatorsOfTheV4QuerySpecType(DseFederatedCatalogCacheExtension injected, ServiceExtensionContext context) {
        // e.g. the upstream management v4 JSON schema validator, when the runtime has it
        validatorRegistry.register("v4:QuerySpec", input -> ValidationResult.failure(violation("upstream rule", "limit")));

        injected.initialize(context);

        var result = validatorRegistry.validate("v4:QuerySpec", querySpec("doesNotExist"));
        assertThat(result.getFailure().getViolations()).extracting(Violation::message)
                .containsExactlyInAnyOrder("upstream rule", "Cannot sort by 'doesNotExist': field does not exist in Dataset");
    }

    @Test
    void federatedCatalogCache_inMatchesOneElementOfListProperty() {
        var cache = extension.federatedCatalogCache();
        cache.save(catalog("cat-1",
                dataset("ds-route-travel", List.of("route", "travel")),
                dataset("ds-health", List.of("health"))));

        var result = cache.query(QuerySpec.Builder.newInstance()
                .filter(new Criterion("properties.domain", "in", List.of("travel", "logistics")))
                .build());

        assertThat(result).flatExtracting(Catalog::getDatasets).extracting(Dataset::getId).containsExactly("ds-route-travel");
    }

    @Test
    void federatedCatalogCache_inKeepsTheContainmentSemanticsForScalarProperties() {
        var cache = extension.federatedCatalogCache();
        cache.save(catalog("cat-1", dataset("ds-route", "route"), dataset("ds-health", "health")));

        var result = cache.query(QuerySpec.Builder.newInstance()
                .filter(new Criterion("properties.domain", "IN", List.of("route", "travel")))
                .build());

        assertThat(result).flatExtracting(Catalog::getDatasets).extracting(Dataset::getId).containsExactly("ds-route");
    }

    private static Dataset dataset(String id, Object domain) {
        return Dataset.Builder.newInstance().id(id).property("domain", domain).build();
    }

    private static Catalog catalog(String id, Dataset... datasets) {
        return Catalog.Builder.newInstance().id(id).participantId("provider").datasets(List.of(datasets)).build();
    }

    private static JsonObject querySpec(String sortField) {
        return Json.createObjectBuilder()
                .add("@context", Json.createObjectBuilder().add("@vocab", "https://w3id.org/edc/v0.0.1/ns/"))
                .add("@type", "QuerySpec").add("sortField", sortField).build();
    }
}
