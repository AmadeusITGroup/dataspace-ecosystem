package org.eclipse.dse.identityhub.legacypath;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Rewrites request paths of the IdentityHub / IssuerService participant-scoped APIs that use the pre-0.17 URL form.
 *
 * <p>Up to EDC 0.16 those APIs expected the participant context id as a base64url-encoded path segment and the
 * unstable APIs lived under {@code /v1alpha}. EDC 0.17 (eclipse-edc/IdentityHub#937) stopped decoding the segment and
 * EDC 0.18 (eclipse-edc/IdentityHub#998) moved {@code /v1alpha} to {@code /v1beta}. DSE participant context ids are
 * DIDs containing {@code %3A}; sending them raw requires {@code %25} in the path, which Jetty rejects as ambiguous.
 * In addition, the base64 form is published in every participant's DID document (CredentialService, IssuerService).
 *
 * <p>This rewriter therefore maps {@code /v1alpha/participants[/...]} (including the collection URL used to create and
 * list participants) to {@code /v1beta/participants[/...]} and replaces a
 * base64url segment that decodes to a DID with its percent-encoded value, which JAX-RS decodes back to the DID.
 */
public final class LegacyParticipantPathRewriter {

    // the participant id is optional, so that the collection URL (create / list participants) is mapped as well
    private static final Pattern PARTICIPANT_PATH =
            Pattern.compile("^(?<prefix>.*?/)(?<version>v1alpha|v1beta|v1)(?<participants>/participants)(?:/(?<id>[^/?#]+))?(?<rest>(?:/.*)?)$");
    private static final Pattern BASE64_URL = Pattern.compile("^(?<data>[A-Za-z0-9_-]+)(?<padding>={0,2})$");
    private static final Pattern ENCODED_PADDING = Pattern.compile("%3[Dd]");
    private static final String DID_PREFIX = "did:";
    private static final String LEGACY_VERSION = "v1alpha";
    private static final String CURRENT_VERSION = "v1beta";
    private static final int BASE64_QUANTUM = 4;
    private static final int UNDECODABLE_REMAINDER = 1;

    private LegacyParticipantPathRewriter() {
    }

    /**
     * Returns the rewritten raw path, or empty if the path does not need rewriting.
     *
     * @param rawPath the raw (still percent-encoded) request path
     */
    public static Optional<String> rewrite(String rawPath) {
        if (rawPath == null) {
            return Optional.empty();
        }
        var matcher = PARTICIPANT_PATH.matcher(rawPath);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        var version = LEGACY_VERSION.equals(matcher.group("version")) ? CURRENT_VERSION : matcher.group("version");
        var segment = matcher.group("id");
        var newSegment = segment == null
                ? ""
                : ("/" + decodeLegacyId(segment).map(LegacyParticipantPathRewriter::encodePathSegment).orElse(segment));

        var rewritten = matcher.group("prefix") + version + matcher.group("participants") + newSegment + matcher.group("rest");
        return rewritten.equals(rawPath) ? Optional.empty() : Optional.of(rewritten);
    }

    private static Optional<String> decodeLegacyId(String rawSegment) {
        // the segment is still percent-encoded: clients may send the base64 padding "=" as "%3D"
        var segment = ENCODED_PADDING.matcher(rawSegment).replaceAll("=");
        if (!isDecodableBase64Url(segment)) {
            return Optional.empty();
        }
        var decoded = new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
        return decoded.startsWith(DID_PREFIX) ? Optional.of(decoded) : Optional.empty();
    }

    /**
     * Checks alphabet and length up front, so that {@link Base64.Decoder#decode(String)} cannot fail: unpadded input
     * must not leave a single trailing character, padded input must be a whole number of 4-character quanta.
     */
    static boolean isDecodableBase64Url(String segment) {
        var matcher = BASE64_URL.matcher(segment);
        if (!matcher.matches()) {
            return false;
        }
        var dataLength = matcher.group("data").length();
        var paddingLength = matcher.group("padding").length();
        if (paddingLength == 0) {
            return dataLength % BASE64_QUANTUM != UNDECODABLE_REMAINDER;
        }
        return (dataLength + paddingLength) % BASE64_QUANTUM == 0 && dataLength % BASE64_QUANTUM == BASE64_QUANTUM - paddingLength;
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
