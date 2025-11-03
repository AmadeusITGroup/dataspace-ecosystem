package org.eclipse.edc.eonax.telemetry.api;

import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobStorageException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.eonax.telemetry.services.ReportUtil;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.StreamSupport;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.edc.eonax.telemetry.services.report.ReportGeneratorSchedulerExtension.azureStorageService;
import static org.eclipse.edc.eonax.telemetry.services.report.ReportGeneratorSchedulerExtension.scheduler;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path("/billing-reports")
public class TelemetryCsvManagerApiController implements TelemetryCsvManagerApi {

    private static final Base64.Decoder DECODER = Base64.getDecoder();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Monitor monitor;

    public TelemetryCsvManagerApiController(Monitor monitor) {
        this.monitor = monitor;
        monitor.info("Telemetry CSV Manager API Controller initialized");
    }

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("text/csv")
    public Response getReport(@HeaderParam("Authorization") String authHeader, @QueryParam("month") Integer month, @QueryParam("year") Integer year) {
        monitor.info("Fetching Report...");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Missing JWT token").build();
        }

        if (checksInvalidPeriod(month)) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid date range provided").build();
        }

        String jwtToken = authHeader.split(" ")[1]; // Removes Bearer part from header
        String[] jwtParts = jwtToken.split("\\.");
        if (jwtParts.length != 3) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Invalid JWT token").build();
        }

        try {
            List<String> roles = extractRolesFromJwt(jwtParts);

            // Validate exactly one element -> participant
            if (roles.size() != 1) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("Invalid JWT token").build();
            }

            String[] roleParts = roles.get(0).split("\\.");
            if (roleParts.length < 2 || roleParts[1] == null || roleParts[1].isEmpty()) {
                return Response.status(Response.Status.FORBIDDEN).entity("Missing or invalid participant in roles").build();
            }
            String participantName = roleParts[1];

            String reportFilename = ReportUtil.generateReportFileName(participantName);
            String objectPath = ReportUtil.getObjectPath(false, LocalDateTime.of(year, month, 1, 0, 0), reportFilename);
            byte[] csvData = getReportFromRemoteStorage(objectPath);
            if (csvData == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("No report found for specified period").build();
            } else {
                return Response.ok(csvData)
                        .type("text/csv")
                        .header("Content-Disposition", "attachment; filename=\"" + reportFilename + "\"")
                        .build();
            }
        } catch (JwtException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Invalid JWT: " + e.getMessage()).build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> extractRolesFromJwt(String[] jwtParts) throws JsonProcessingException {
        String decodedJwtPayload = new String(DECODER.decode(jwtParts[1]));
        JsonNode root = MAPPER.readTree(decodedJwtPayload);
        JsonNode rolesNode = root.get("roles");
        if (rolesNode == null || !rolesNode.isArray()) {
            throw new IllegalArgumentException("Missing roles array in JWT");
        }

        return StreamSupport.stream(rolesNode.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private static boolean checksInvalidPeriod(Integer month) {
        return month == null || month < 1 || month > 12;
    }

    // This api will not be exposed via APIM, it is meant to be used only internally
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/json")
    public Response generateReport(ReportGenerationRequest reportGenerationRequest) {
        String participantName = reportGenerationRequest.participantName();
        Integer year = reportGenerationRequest.year();
        Integer month = reportGenerationRequest.month();

        if (participantName == null || participantName.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Participant name not provided").build();
        }

        if (checksInvalidPeriod(month)) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid date range provided").build();
        }

        try {
            scheduler.triggerGenerationForParticipant(participantName, LocalDateTime.of(year, month, 1, 0, 0));
            return Response.status(Response.Status.CREATED).build();
        } catch (BlobStorageException e) {
            if (e.getErrorCode() == BlobErrorCode.BLOB_ALREADY_EXISTS) {
                String conflictMessage = "This report already exists, participant: " +
                        reportGenerationRequest.participantName() + " month: " + reportGenerationRequest.month() +
                        " year: " + reportGenerationRequest.year();
                return Response.status(Response.Status.CONFLICT).entity(conflictMessage).build();
            }
            throw e;
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Generation failed").build();
        }
    }

    private byte[] getReportFromRemoteStorage(String objectPath) {
        try (InputStream downloadedInputStream = azureStorageService.download(objectPath)) {
            return downloadedInputStream.readAllBytes();
        } catch (Exception e) {
            this.monitor.warning("Exception thrown while getting report: " + e);
            return null;
        }
    }
}
