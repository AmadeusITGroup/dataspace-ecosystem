package org.eclipse.edc;

import org.eclipse.edc.spi.iam.TokenRepresentation;

public record FilterRequest(TokenRepresentation tokenRepresentation, String participantDid) {
}