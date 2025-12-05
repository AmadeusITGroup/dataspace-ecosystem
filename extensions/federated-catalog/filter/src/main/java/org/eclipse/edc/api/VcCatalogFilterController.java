package org.eclipse.edc.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.FilterRequest;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.util.FederatedCatalogService;
import org.eclipse.edc.util.IdentityServiceValidator;

import java.util.List;

@Path("/catalogfilter")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VcCatalogFilterController implements FederatedCatalogFilterApiV2 {

    private final FederatedCatalogService federatedCatalogService;

    private final IdentityService identityService;

    private final Monitor monitor;

    public VcCatalogFilterController(ServiceExtensionContext context, Monitor monitor, FederatedCatalogService catalogService, IdentityService identityService) {
        this.monitor = monitor;
        this.federatedCatalogService = catalogService;
        this.identityService = identityService;
    }

    @Override
    @POST
    @Path("/filter")
    public Response filter(FilterRequest req) {
        if (req == null || req.tokenRepresentation() == null) {
            return Response.status(400).entity("Missing VCToken").build();
        }
        try {
            List<JsonNode> filtered = null;
            IdentityServiceValidator validator =  new IdentityServiceValidator(identityService, monitor);
            ClaimToken credentials = validator.validate(req.tokenRepresentation());
            if (credentials != null) {
                filtered = federatedCatalogService.fetchAndFilterCatalog(credentials, req.participantDid());
            }
            if (filtered != null && !filtered.isEmpty()) {
                return Response.ok(filtered).build();
            } else {
                return Response.status(204).build();
            }
        } catch (Exception e) {
            return Response.status(500).build();
        }
    }

}


