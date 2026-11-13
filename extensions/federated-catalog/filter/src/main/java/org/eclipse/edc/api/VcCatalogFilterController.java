package org.eclipse.edc.api;

import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.FilterRequest;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.AbstractResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.util.FederatedCatalogService;
import org.eclipse.edc.util.IdentityServiceValidator;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;
import org.eclipse.edc.web.spi.exception.NotAuthorizedException;

import java.util.Collection;

import static jakarta.json.stream.JsonCollectors.toJsonArray;

/**
 * Catalog filter endpoint {@code POST /filter}. Errors are thrown as EDC exceptions, so the EDC exception mappers that
 * {@code JerseyRestService} registers on the context write the response: the standard EDC JSON error body
 * ({@code [{"message", "type", "path", "invalidValue"}]}) for {@code EdcApiException}s; any other exception is
 * unexpected: {@code 500} without entity (Jetty then writes its error page).
 */
@Path("/filter")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VcCatalogFilterController implements FederatedCatalogFilterApiV2 {

    private final FederatedCatalogService federatedCatalogService;

    private final IdentityService identityService;
    private final TypeTransformerRegistry transformerRegistry;
    private final Monitor monitor;
    private final String participantContextId;

    public VcCatalogFilterController(ServiceExtensionContext context, Monitor monitor, FederatedCatalogService catalogService, IdentityService identityService, TypeTransformerRegistry transformerRegistry) {
        this.monitor = monitor;
        this.federatedCatalogService = catalogService;
        this.identityService = identityService;
        this.transformerRegistry = transformerRegistry;
        this.participantContextId = context.getSetting("edc.participant.id", "default-participant");
    }

    @Override
    @POST
    public Response filter(FilterRequest req) {
        if (req == null || req.tokenRepresentation() == null) {
            throw new InvalidRequestException("The catalog filter request must contain the participant token (tokenRepresentation)");
        }
        IdentityServiceValidator validator = new IdentityServiceValidator(identityService, monitor, participantContextId);
        ClaimToken credentials = validator.validate(req.tokenRepresentation());
        if (credentials == null) {
            throw new NotAuthorizedException("The participant token could not be verified");
        }

        Collection<Catalog> filtered = fetchAndFilterCatalog(credentials, req);
        if (!filtered.isEmpty()) {
            return Response.ok(filtered.stream()
                    .map(c -> transformerRegistry.transform(c, JsonObject.class))
                    .filter(Result::succeeded)
                    .map(AbstractResult::getContent)
                    .collect(toJsonArray())).build();
        } else {
            return Response.ok(filtered).build();
        }
    }

    /**
     * EDC exceptions (e.g. the federated catalog error mapped by {@link FederatedCatalogService}) are thrown as they are;
     * any other failure is unexpected and becomes an {@link EdcException} ({@code 500}).
     */
    private Collection<Catalog> fetchAndFilterCatalog(ClaimToken credentials, FilterRequest req) {
        try {
            return federatedCatalogService.fetchAndFilterCatalog(credentials, req.participantDid(), req.query());
        } catch (EdcException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EdcException("Interrupted while processing the catalog filter request", e);
        } catch (Exception e) {
            throw new EdcException("Error processing catalog filter request: " + e.getMessage(), e);
        }
    }

}
