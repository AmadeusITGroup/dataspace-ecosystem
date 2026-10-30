package org.eclipse.dse.identityhub.legacypath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyParticipantPathRewriterTest {

    private static final String DID = "did:web:consumer-identityhub%3A8383:api:did";
    private static final String B64 = Base64.getUrlEncoder().withoutPadding().encodeToString(DID.getBytes(StandardCharsets.UTF_8));

    @Test
    void shouldDecodeBase64DidAndMapV1alphaToV1beta() {
        var result = LegacyParticipantPathRewriter.rewrite("/api/issuance/v1alpha/participants/" + B64 + "/credentials");

        assertThat(result).hasValueSatisfying(path -> {
            assertThat(path).startsWith("/api/issuance/v1beta/participants/").endsWith("/credentials");
            var segment = path.split("/")[5];
            assertThat(URLDecoder.decode(segment, StandardCharsets.UTF_8)).isEqualTo(DID);
        });
    }

    @Test
    void shouldDecodePaddedBase64() {
        var padded = Base64.getUrlEncoder().encodeToString(DID.getBytes(StandardCharsets.UTF_8));

        assertThat(LegacyParticipantPathRewriter.rewrite("/api/credentials/v1/participants/" + padded + "/presentations/query"))
                .hasValueSatisfying(path -> assertThat(URLDecoder.decode(path.split("/")[5], StandardCharsets.UTF_8)).isEqualTo(DID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%3D", "%3d"})
    void shouldDecodePercentEncodedPadding(String encodedPadding) {
        var padded = Base64.getUrlEncoder().encodeToString(DID.getBytes(StandardCharsets.UTF_8));
        assertThat(padded).endsWith("=");
        var encoded = padded.replace("=", encodedPadding);

        assertThat(LegacyParticipantPathRewriter.rewrite("/api/identity/v1alpha/participants/" + encoded + "/credentials"))
                .hasValueSatisfying(path -> {
                    assertThat(path).startsWith("/api/identity/v1beta/participants/").endsWith("/credentials");
                    assertThat(URLDecoder.decode(path.split("/")[5], StandardCharsets.UTF_8)).isEqualTo(DID);
                });
    }

    @Test
    void shouldLeaveNonDidSegmentWithEncodedPaddingUntouched() {
        // base64 of "user" with %3D padding: decodes, but not to a DID, so the segment is kept as sent
        assertThat(LegacyParticipantPathRewriter.rewrite("/api/identity/v1beta/participants/dXNlcg%3D%3D/keypairs")).isEmpty();
    }

    @Test
    void shouldKeepV1AndOnlyDecodeSegment() {
        assertThat(LegacyParticipantPathRewriter.rewrite("/api/credentials/v1/participants/" + B64 + "/presentations/query"))
                .hasValueSatisfying(path -> assertThat(path).startsWith("/api/credentials/v1/participants/did%3Aweb%3A"));
    }

    @Test
    void shouldOnlyMapVersionForPlainIds() {
        assertThat(LegacyParticipantPathRewriter.rewrite("/api/issueradmin/v1alpha/participants/user/holders"))
                .contains("/api/issueradmin/v1beta/participants/user/holders");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/issueradmin/v1beta/participants/user/holders",
            "/api/identity/v1beta/participants/did%3Aweb%3Ax/credentials",
            "/api/identity/v1beta/participants",
            "/api/identity/v1beta/participants/dXNlcg/keypairs", // base64 of "user": not a DID, left untouched
            "/api/catalog/v1alpha/catalog/query",
            "/api/identity/v1beta/participants/",
            "/api/identity/v1alpha/participantsx",
            "/api/identity/v1alpha/participantsx/user"
    })
    void shouldNotRewrite(String path) {
        assertThat(LegacyParticipantPathRewriter.rewrite(path)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/identity/v1alpha/participants", "/api/identity/v1alpha/participants/"})
    void shouldMapVersionOfCollectionUrl(String path) {
        assertThat(LegacyParticipantPathRewriter.rewrite(path)).contains(path.replace("/v1alpha/", "/v1beta/"));
    }

    @Test
    void shouldNotRewriteNull() {
        assertThat(LegacyParticipantPathRewriter.rewrite(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dXNlcg", "dXNlcg==", "ZGlk", "YQ", "YWI", "YWI="})
    void shouldAcceptDecodableBase64(String segment) {
        assertThat(LegacyParticipantPathRewriter.isDecodableBase64Url(segment)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "abcde", "YQ=", "YWI==", "YWJj=", "us.er", "a=b"})
    void shouldRejectUndecodableBase64(String segment) {
        assertThat(LegacyParticipantPathRewriter.isDecodableBase64Url(segment)).isFalse();
    }

    @Test
    void shouldLeaveUndecodableSegmentAndOnlyMapVersion() {
        assertThat(LegacyParticipantPathRewriter.rewrite("/api/identity/v1alpha/participants/abcde/keypairs"))
                .contains("/api/identity/v1beta/participants/abcde/keypairs");
    }
}
