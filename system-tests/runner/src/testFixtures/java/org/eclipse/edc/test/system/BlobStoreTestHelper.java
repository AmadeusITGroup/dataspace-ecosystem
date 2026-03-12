package org.eclipse.edc.test.system;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.eclipse.edc.test.system.ParticipantConstants.KUBECTL_CONTEXT;

/**
 * Test helper that verifies blob content in the Azurite emulator running inside the Kind cluster.
 * Uses {@code kubectl port-forward} to temporarily expose the Azurite service, then connects
 * via the Azure Storage SDK to list and download blobs.
 */
public class BlobStoreTestHelper {

    private static final String AZURITE_SERVICE = "svc/azurite-blobstorage";
    private static final int AZURITE_PORT = 10000;

    private final String accountName;
    private final String accountKey;

    public BlobStoreTestHelper(String accountName, String accountKey) {
        this.accountName = accountName;
        this.accountKey = accountKey;
    }

    /**
     * Asserts that a blob exists in the given container and returns its content as a string.
     *
     * @param containerName the container to look in
     * @param blobName      the blob name (prefix) to search for
     * @return the blob content as a UTF-8 string
     */
    public String downloadBlob(String containerName, String blobName) {
        int localPort = findAvailablePort();
        Process portForward = null;
        try {
            portForward = startPortForward(localPort);
            waitForPortForward(localPort);

            var client = createBlobServiceClient(localPort);
            var containerClient = client.getBlobContainerClient(containerName);

            var blobClient = containerClient.getBlobClient(blobName);
            var outputStream = new ByteArrayOutputStream();
            blobClient.downloadStream(outputStream);
            return outputStream.toString(StandardCharsets.UTF_8);
        } finally {
            if (portForward != null) {
                portForward.destroyForcibly();
            }
        }
    }

    /**
     * Lists all blob names in the given container.
     *
     * @param containerName the container to list
     * @return list of blob names
     */
    public List<String> listBlobs(String containerName) {
        int localPort = findAvailablePort();
        Process portForward = null;
        try {
            portForward = startPortForward(localPort);
            waitForPortForward(localPort);

            var client = createBlobServiceClient(localPort);
            var containerClient = client.getBlobContainerClient(containerName);

            return containerClient.listBlobs().stream()
                    .map(blobItem -> blobItem.getName())
                    .collect(Collectors.toList());
        } finally {
            if (portForward != null) {
                portForward.destroyForcibly();
            }
        }
    }

    /**
     * Lists blob names in the given container that start with the specified prefix.
     *
     * @param containerName the container to search
     * @param prefix        the prefix to filter by
     * @return list of matching blob names
     */
    public List<String> listBlobsByPrefix(String containerName, String prefix) {
        int localPort = findAvailablePort();
        Process portForward = null;
        try {
            portForward = startPortForward(localPort);
            waitForPortForward(localPort);

            var client = createBlobServiceClient(localPort);
            var containerClient = client.getBlobContainerClient(containerName);

            return containerClient.listBlobs().stream()
                    .map(blobItem -> blobItem.getName())
                    .filter(blobName -> blobName.startsWith(prefix))
                    .collect(Collectors.toList());
        } finally {
            if (portForward != null) {
                portForward.destroyForcibly();
            }
        }
    }

    private BlobServiceClient createBlobServiceClient(int localPort) {
        var connectionString = String.format(
                "DefaultEndpointsProtocol=http;AccountName=%s;AccountKey=%s;BlobEndpoint=http://127.0.0.1:%d/%s;",
                accountName, accountKey, localPort, accountName
        );
        return new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
    }

    private Process startPortForward(int localPort) {
        try {
            var process = new ProcessBuilder(
                    "kubectl",
                    "--context", KUBECTL_CONTEXT,
                    "port-forward",
                    AZURITE_SERVICE,
                    localPort + ":" + AZURITE_PORT,
                    "-n", "default"
            ).redirectErrorStream(true).start();

            // Give kubectl a moment to establish the port-forward
            if (!process.isAlive()) {
                throw new RuntimeException("kubectl port-forward exited immediately with code " + process.exitValue());
            }
            return process;
        } catch (IOException e) {
            throw new RuntimeException("Failed to start kubectl port-forward", e);
        }
    }

    private void waitForPortForward(int localPort) {
        var deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(15);
        while (System.currentTimeMillis() < deadline) {
            try (var socket = new java.net.Socket("127.0.0.1", localPort)) {
                return; // connection succeeded
            } catch (IOException e) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for port-forward", ie);
                }
            }
        }
        throw new RuntimeException("Timed out waiting for kubectl port-forward on port " + localPort);
    }

    private static int findAvailablePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find available port", e);
        }
    }
}
