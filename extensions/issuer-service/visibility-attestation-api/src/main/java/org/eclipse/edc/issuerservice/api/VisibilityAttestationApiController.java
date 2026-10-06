package org.eclipse.edc.issuerservice.api;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import org.eclipse.dse.spi.issuerservice.VisibilityAttestation;
import org.eclipse.dse.spi.issuerservice.VisibilityAttestationStore;
import org.eclipse.edc.identityhub.api.Versions;
import org.eclipse.edc.spi.query.QuerySpec;

import java.util.Collection;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.edc.spi.result.ServiceResult.from;
import static org.eclipse.edc.web.spi.exception.ServiceResultHandler.exceptionMapper;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(Versions.UNSTABLE + "/participants/{participantContextId}/attestation-visibility")
public class VisibilityAttestationApiController implements VisibilityAttestationAdminApi {

    private final VisibilityAttestationStore store;

    public VisibilityAttestationApiController(VisibilityAttestationStore store) {
        this.store = store;
    }

    @POST
    @Override
    public void createVisibilityAttestation(@PathParam("participantContextId") String participantContextId, VisibilityAttestationDto dto) {
        from(store.save(toAttestation(dto))).orElseThrow(exceptionMapper(VisibilityAttestation.class));
    }

    @PUT
    @Override
    public void updateVisibilityAttestation(@PathParam("participantContextId") String participantContextId, VisibilityAttestationDto dto) {
        from(store.update(toAttestation(dto))).orElseThrow(exceptionMapper(VisibilityAttestation.class));
    }

    @DELETE
    @Path("/{id}")
    @Override
    public void deleteVisibilityAttestation(@PathParam("participantContextId") String participantContextId, @PathParam("id") String id) {
        from(store.deleteById(id)).orElseThrow(exceptionMapper(VisibilityAttestation.class, id));
    }

    @POST
    @Path("/request")
    @Override
    public Collection<VisibilityAttestationDto> queryVisibilityAttestations(@PathParam("participantContextId") String participantContextId, QuerySpec querySpec) {
        if (querySpec == null) {
            querySpec = QuerySpec.Builder.newInstance().build();
        }
        try (var attestations = store.query(querySpec)) {
            return attestations
                    .map(attestation -> new VisibilityAttestationDto(attestation.id(), attestation.holderId(), attestation.visibilityScope()))
                    .toList();
        }
    }

    private VisibilityAttestation toAttestation(VisibilityAttestationDto dto) {
        if (dto == null) {
            throw new BadRequestException("Request body is required");
        }
        if (isBlank(dto.holderId())) {
            throw new BadRequestException("holderId is required");
        }
        if (dto.visibilityScope() == null) {
            throw new BadRequestException("visibility_scope is required");
        }
        var id = isBlank(dto.id()) ? dto.holderId() : dto.id();
        return new VisibilityAttestation(id, dto.holderId(), dto.visibilityScope());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}