package org.eclipse.edc.test.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.identityhub.api.verifiablecredentials.v1.unstable.model.CredentialDescriptor;
import org.eclipse.edc.identityhub.api.verifiablecredentials.v1.unstable.model.CredentialRequestDto;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.CredentialProfile;
import org.hamcrest.core.AnyOf;

import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

abstract class AbstractEntity {

    protected abstract String name();

    protected abstract String did();

    protected abstract String vaultUrl();

    protected abstract String identityHubIdentityUrl();

    public void requestCredential(String issuerDid, String credentialType, String credentialDefinitionId) {
        var dto = new CredentialRequestDto(issuerDid, did() + credentialType, List.of(
                new CredentialDescriptor(CredentialProfile.DCP_PROFILE_VC11, credentialType, credentialDefinitionId)));
        given()
                .baseUri(identityHubIdentityUrl())
                .body(dto)
                .contentType(JSON)
                .when()
                .post("/v1beta/participants/%s/credentials/request".formatted(did()))
                .then()
                .statusCode(isStatus2xxOrConflict());
    }

    public List<JsonObject> getCredentials(ObjectMapper mapper) throws JsonProcessingException {
        String credentials =  given()
                .baseUri(identityHubIdentityUrl())
                .when()
                .contentType(JSON)
                .get("/v1beta/participants/%s/credentials".formatted(did()))
                .then()
                .statusCode(isStatus2xx()).extract().body().asString();
        return mapper.readValue(credentials, JsonArray.class).stream()
                .map(JsonValue::asJsonObject)
                .toList();
    }

    protected AnyOf<Integer> isStatus2xx() {
        return anyOf(is(200), is(201), is(204));
    }

    protected AnyOf<Integer> isStatus2xxOrConflict() {
        return anyOf(is(200), is(201), is(204), is(SC_CONFLICT));
    }

}
