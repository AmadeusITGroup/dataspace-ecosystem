package org.eclipse.edc.test.system;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialFormat;
import org.eclipse.edc.identityhub.api.verifiablecredentials.v1.unstable.model.CredentialDescriptor;
import org.eclipse.edc.identityhub.api.verifiablecredentials.v1.unstable.model.CredentialRequestDto;
import org.hamcrest.core.AnyOf;

import java.util.Base64;
import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

abstract class AbstractEntity {

    protected abstract String name();

    protected abstract String did();

    protected abstract String vaultUrl();

    protected abstract String identityHubIdentityUrl();

    public void requestMembershipCredential(String issuerDid, String credentialType) {
        var dto = new CredentialRequestDto(issuerDid, did(), List.of(
                new CredentialDescriptor(CredentialFormat.VC1_0_JWT.name(), credentialType)
        ));

        given()
                .baseUri(identityHubIdentityUrl())
                .body(dto)
                .contentType(JSON)
                .when()
                .post("/v1alpha/participants/%s/credentials/request".formatted(toBase64(did())))
                .then()
                .statusCode(isStatus2xx());
    }

    protected String toBase64(String s) {
        return Base64.getUrlEncoder().encodeToString(s.getBytes());
    }

    protected AnyOf<Integer> isStatus2xx() {
        return anyOf(is(200), is(201), is(204));
    }

}
