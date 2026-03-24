package org.eclipse.edc;

import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.query.QuerySpec;

public record FilterRequest(TokenRepresentation tokenRepresentation, String participantDid, QuerySpec query) {
}