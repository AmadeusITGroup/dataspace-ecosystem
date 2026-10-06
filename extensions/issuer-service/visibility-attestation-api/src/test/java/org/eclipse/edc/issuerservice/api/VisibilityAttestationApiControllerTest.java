package org.eclipse.edc.issuerservice.api;

import io.restassured.common.mapper.TypeRef;
import io.restassured.specification.RequestSpecification;
import org.eclipse.dse.spi.issuerservice.VisibilityAttestation;
import org.eclipse.dse.spi.issuerservice.VisibilityAttestationStore;
import org.eclipse.dse.spi.issuerservice.VisibilityScope;
import org.eclipse.edc.identityhub.api.Versions;
import org.eclipse.edc.junit.annotations.ApiTest;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.web.jersey.testfixtures.RestControllerTestBase;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ApiTest
class VisibilityAttestationApiControllerTest extends RestControllerTestBase {

    private static final String PARTICIPANT_ID = "test-participant";
    private final VisibilityAttestationStore store = mock();

    @Override
    protected Object controller() {
        return new VisibilityAttestationApiController(store);
    }

    @Test
    void shouldCreateAttestationWithVisibilityScope() {
        when(store.save(any())).thenReturn(StoreResult.success());

        baseRequest()
                .contentType(JSON)
                .body(new VisibilityAttestationDto("attestation-id", "holder-id", VisibilityScope.SUBSET))
                .post()
                .then()
                .statusCode(204);

        verify(store).save(assertArg(attestation -> {
            assertThat(attestation.holderId()).isEqualTo("holder-id");
            assertThat(attestation.visibilityScope()).isEqualTo(VisibilityScope.SUBSET);
        }));
    }

    @Test
    void shouldRejectMissingVisibilityScope() {
        baseRequest()
                .contentType(JSON)
                .body("{\"id\":\"attestation-id\",\"holderId\":\"holder-id\"}")
                .post()
                .then()
                .statusCode(400);
    }

    @Test
    void shouldRejectMissingHolderId() {
        baseRequest()
                .contentType(JSON)
                .body("{\"id\":\"attestation-id\",\"visibility_scope\":\"ALL\"}")
                .post()
                .then()
                .statusCode(400);
    }

    @Test
    void shouldRejectUnsupportedVisibilityScope() {
        baseRequest()
                .contentType(JSON)
                .body("{\"id\":\"attestation-id\",\"holderId\":\"holder-id\",\"visibility_scope\":\"PUBLIC\"}")
                .post()
                .then()
                .statusCode(400);
    }

    @Test
    void shouldQueryAttestations() {
        var attestation = new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.ALL);
        when(store.query(any(QuerySpec.class))).thenReturn(Stream.of(attestation));

        var result = baseRequest()
                .contentType(JSON)
                .post("/request")
                .then()
                .statusCode(200)
                .extract()
                .as(new TypeRef<List<VisibilityAttestationDto>>() {
                });

        assertThat(result).containsExactly(new VisibilityAttestationDto("attestation-id", "holder-id", VisibilityScope.ALL));
    }

    private RequestSpecification baseRequest() {
        return given().baseUri("http://localhost:" + port + Versions.UNSTABLE + "/participants/" +
                Base64.getUrlEncoder().encodeToString(PARTICIPANT_ID.getBytes()) + "/attestation-visibility");
    }
}