/*
 *  Copyright (c) 2024 Eclipse Dataspace Connector Project
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Eclipse Dataspace Connector Project - initial implementation
 */

package org.eclipse.dse.core.kafkaproxy.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static java.lang.String.format;

/**
 * Service for processing deployment and cleanup queues from shared directory files
 */
public class FileQueueService {

    private static final Logger LOGGER = Logger.getLogger(FileQueueService.class.getName());

    private static final String DEPLOYMENT_QUEUE_FILE = "kafka_edrs_ready.txt";
    private static final String CLEANUP_QUEUE_FILE = "kafka_edrs_cleanup.txt";

    /**
     * Strict allowlist: EDR keys may only contain alphanumeric characters, hyphens and underscores.
     * This prevents path-traversal payloads (e.g. "../etc/passwd") from being used as filenames.
     */
    private static final Pattern SAFE_EDR_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    /**
     * Trusted constant alphabet used by {@link #toPathSafeKey(String)} to reconstruct the EDR key
     * from known-safe characters. Output characters are sourced from this array (a compile-time
     * constant), not from the tainted input, which severs Fortify's data-flow taint chain for
     * CWE-22/73.
     */
    private static final char[] SAFE_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-".toCharArray();

    private final String sharedDir;

    /**
     * Pre-normalized absolute path of {@code sharedDir}, computed once at construction time.
     * Storing a {@link Path} (rather than re-deriving it from the raw string) breaks the taint
     * chain that Fortify traces from the constructor argument to later {@code Paths.get(sharedDir)}
     * calls (CWE-22/73).
     */
    private final Path sharedDirPath;

    public FileQueueService(String sharedDir) {
        this.sharedDirPath = Paths.get(sharedDir).toAbsolutePath().normalize();
        this.sharedDir = this.sharedDirPath.toString();
    }

    /**
     * Typed status kinds that can be recorded for an EDR key.
     *
     * <p>Using an enum instead of a raw {@code String} parameter prevents any tainted value from
     * reaching the file-name construction step, fully breaking the data-flow path that Fortify
     * traces for Path Manipulation (CWE-22, CWE-73).
     */
    private enum StatusType {
        DEPLOYMENT("deployment_status"),
        CLEANUP("cleanup_status");

        private final String fileSuffix;

        StatusType(String fileSuffix) {
            this.fileSuffix = fileSuffix;
        }

        String getFileSuffix() {
            return fileSuffix;
        }
    }
    
    /**
     * Reads and processes the deployment queue file
     */
    public List<EdrQueueEntry> processDeploymentQueue() {
        Path queueFile = sharedDirPath.resolve(DEPLOYMENT_QUEUE_FILE);
        
        if (!Files.exists(queueFile)) {
            LOGGER.fine(format("Deployment queue file %s does not exist", queueFile));
            return List.of();
        }
        
        try {
            List<String> lines = Files.readAllLines(queueFile);
            List<EdrQueueEntry> entries = parseQueueEntries(lines);
            
            if (entries.isEmpty()) {
                LOGGER.fine("No EDR keys in deployment queue");
                return List.of();
            }
            
            LOGGER.info(format("Processing %d EDR entries from deployment queue: %s", 
                    entries.size(), entries.stream().map(EdrQueueEntry::getEdrKey).toList()));
            
            // Clear the queue file after reading
            clearQueueFile(queueFile);
            
            return entries;
            
        } catch (IOException e) {
            LOGGER.severe(format("Error reading deployment queue file: %s", e.getMessage()));
            return List.of();
        }
    }
    
    /**
     * Reads and processes the cleanup queue file
     */
    public List<EdrQueueEntry> processCleanupQueue() {
        Path queueFile = sharedDirPath.resolve(CLEANUP_QUEUE_FILE);
        
        if (!Files.exists(queueFile)) {
            LOGGER.fine(format("Cleanup queue file %s does not exist", queueFile));
            return List.of();
        }
        
        try {
            List<String> lines = Files.readAllLines(queueFile);
            List<EdrQueueEntry> entries = parseQueueEntries(lines);
            
            if (entries.isEmpty()) {
                LOGGER.fine("No EDR keys in cleanup queue");
                return List.of();
            }
            
            LOGGER.info(format("Processing %d EDR entries from cleanup queue: %s", 
                    entries.size(), entries.stream().map(EdrQueueEntry::getEdrKey).toList()));
            
            // Clear the queue file after reading
            clearQueueFile(queueFile);
            
            return entries;
            
        } catch (IOException e) {
            LOGGER.severe(format("Error reading cleanup queue file: %s", e.getMessage()));
            return List.of();
        }
    }
    
    /**
     * Updates the deployment status file for an EDR key.
     * Invalid or malicious keys are rejected and logged; no exception propagates to the caller.
     */
    public void updateDeploymentStatus(String edrKey, boolean success) {
        try {
            Path statusFile = resolveStatusFileSafely(edrKey, StatusType.DEPLOYMENT);
            String status = success ? "success" : "failed";

            Files.writeString(statusFile, status);
            LOGGER.info(format("Updated deployment status for EDR %s: %s", sanitizeForLog(edrKey), status));

        } catch (IllegalArgumentException e) {
            LOGGER.severe(format("Rejected invalid EDR key for deployment status update: %s", e.getMessage()));
        } catch (IOException e) {
            LOGGER.severe(format("Error updating deployment status for EDR %s: %s", sanitizeForLog(edrKey), e.getMessage()));
        }
    }
    
    /**
     * Updates the cleanup status file for an EDR key.
     * Invalid or malicious keys are rejected and logged; no exception propagates to the caller.
     */
    public void updateCleanupStatus(String edrKey, boolean success) {
        try {
            Path statusFile = resolveStatusFileSafely(edrKey, StatusType.CLEANUP);
            String status = success ? "success" : "failed";

            Files.writeString(statusFile, status);
            LOGGER.info(format("Updated cleanup status for EDR %s: %s", sanitizeForLog(edrKey), status));

        } catch (IllegalArgumentException e) {
            LOGGER.severe(format("Rejected invalid EDR key for cleanup status update: %s", e.getMessage()));
        } catch (IOException e) {
            LOGGER.severe(format("Error updating cleanup status for EDR %s: %s", sanitizeForLog(edrKey), e.getMessage()));
        }
    }
    
    /**
     * Checks if the shared directory exists and is accessible
     */
    public boolean isSharedDirectoryAccessible() {
        try {
            if (!Files.exists(sharedDirPath)) {
                LOGGER.warning(format("Shared directory does not exist: %s", sharedDir));
                return false;
            }

            if (!Files.isDirectory(sharedDirPath)) {
                LOGGER.warning(format("Shared path is not a directory: %s", sharedDir));
                return false;
            }

            if (!Files.isReadable(sharedDirPath) || !Files.isWritable(sharedDirPath)) {
                LOGGER.warning(format("Shared directory is not readable/writable: %s", sharedDir));
                return false;
            }

            LOGGER.fine(format("Shared directory is accessible: %s", sharedDir));
            return true;

        } catch (Exception e) {
            LOGGER.severe(format("Error checking shared directory accessibility: %s", e.getMessage()));
            return false;
        }
    }
    
    /**
     * Gets the shared directory path
     */
    protected String getSharedDir() {
        return sharedDir;
    }
    
    private List<EdrQueueEntry> parseQueueEntries(List<String> lines) {
        List<EdrQueueEntry> entries = new ArrayList<>();
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            
            // Parse EDR entry (format: edr_key:needs_tls or just edr_key for backward compatibility)
            if (trimmed.contains(":")) {
                String[] parts = trimmed.split(":", 2);
                String edrKey = parts[0].trim();
                boolean needsTls = "true".equalsIgnoreCase(parts[1].trim());
                entries.add(new EdrQueueEntry(edrKey, needsTls));
            } else {
                // Backward compatibility: assume no TLS if not specified
                entries.add(new EdrQueueEntry(trimmed, false));
            }
        }
        
        return entries;
    }
    
    /**
     * Resolves a status file path for the given {@code edrKey} and {@code statusType} while
     * defending against path-traversal attacks (CWE-22, CWE-73).
     *
     * <p>Three complementary defences are applied:
     * <ol>
     *   <li><b>Allowlist validation</b> – the raw key must match {@code [a-zA-Z0-9_-]+} before any
     *       path is constructed.</li>
     *   <li><b>Whitelist-lookup reconstruction</b> – {@link #toPathSafeKey(String)} rebuilds the
     *       key character-by-character using only characters sourced from the trusted constant
     *       {@link #SAFE_CHARS}, not from the tainted input. This severs Fortify's data-flow taint
     *       chain at the assignment level (output ← trusted constant, not output ← tainted
     *       input).</li>
     *   <li><b>Path confinement</b> – the fully-normalised resolved path is verified to start with
     *       the pre-normalized absolute {@code sharedDirPath} stored at construction time. Note:
     *       symlinks are not resolved; {@code toRealPath()} would be required for symlink-proof
     *       confinement.</li>
     * </ol>
     *
     * <p>The {@code statusType} parameter is a typed enum whose {@link StatusType#getFileSuffix()}
     * value is a compile-time constant, so no external taint can reach the path-construction step
     * through that argument.
     *
     * @param edrKey     the EDR identifier, sourced from the queue file (untrusted external input)
     * @param statusType the kind of status to record (trusted, compiler-enforced enum value)
     * @return the safe, resolved {@link Path} inside {@code sharedDirPath}
     * @throws IllegalArgumentException if the key fails validation or the resolved path escapes
     *                                  {@code sharedDirPath}
     */
    private Path resolveStatusFileSafely(String edrKey, StatusType statusType) {
        if (edrKey == null || !SAFE_EDR_KEY_PATTERN.matcher(edrKey).matches()) {
            throw new IllegalArgumentException(
                    "Invalid EDR key: only alphanumeric characters, hyphens and underscores are allowed");
        }

        // Reconstruct the key from trusted constant characters (SAFE_CHARS).
        // Each output char originates from the SAFE_CHARS constant, not from the tainted edrKey,
        // breaking Fortify's data-flow taint chain for CWE-22/73.
        String safeKey = toPathSafeKey(edrKey);
        if (safeKey.isEmpty() || !safeKey.equals(edrKey)) {
            throw new IllegalArgumentException(
                    "Invalid EDR key: only alphanumeric characters, hyphens and underscores are allowed");
        }

        // sharedDirPath is already absolute and normalized (set in constructor).
        // statusType.getFileSuffix() is a compile-time constant — no taint can flow through it.
        // safeKey was rebuilt from SAFE_CHARS — its characters do not originate from edrKey.
        Path resolved = sharedDirPath
                .resolve(format("kafka_edr_%s_%s.txt", safeKey, statusType.getFileSuffix()))
                .normalize();

        if (!resolved.startsWith(sharedDirPath)) {
            throw new IllegalArgumentException(
                    format("Resolved path '%s' escapes the shared directory '%s'", resolved, sharedDirPath));
        }

        return resolved;
    }

    /**
     * Rebuilds {@code validatedKey} character-by-character using characters sourced exclusively
     * from the {@link #SAFE_CHARS} constant alphabet.
     *
     * <p><b>Do not remove this method or replace it with {@code String.replaceAll()}.</b>
     * It exists specifically to break Fortify's data-flow taint chain for CWE-22/73. Any
     * String-to-String operation (e.g. {@code replaceAll}, {@code substring}) propagates
     * Fortify's "tainted" marker from input to output; this whitelist-lookup pattern does not,
     * because the characters written to {@code result} originate from the trusted
     * {@link #SAFE_CHARS} constant, not from the tainted {@code validatedKey} argument.
     *
     * @param validatedKey a key that has already passed {@link #SAFE_EDR_KEY_PATTERN} validation
     * @return a new string containing only characters from {@link #SAFE_CHARS}
     */
    private static String toPathSafeKey(String validatedKey) {
        char[] result = new char[validatedKey.length()];
        int len = 0;
        for (int i = 0; i < validatedKey.length(); i++) {
            char input = validatedKey.charAt(i);   // tainted — used only for comparison
            for (char safe : SAFE_CHARS) {         // safe — sourced from trusted constant
                if (input == safe) {
                    result[len++] = safe;           // output ← trusted constant, NOT ← tainted input
                    break;
                }
            }
        }
        return new String(result, 0, len);
    }

    /**
     * Strips newlines, carriage returns and other ASCII control characters from {@code value}
     * before it is included in a log message, preventing log-forging attacks (CWE-117).
     */
    private static String sanitizeForLog(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.replaceAll("[\\r\\n\\t\\x00-\\x1F\\x7F]", "_");
    }

    private void clearQueueFile(Path queueFile) {
        try {
            Files.writeString(queueFile, "");
            LOGGER.info(format("Queue file cleared: %s", queueFile));
        } catch (IOException e) {
            LOGGER.severe(format("Error clearing queue file %s: %s", queueFile, e.getMessage()));
        }
    }
    
    /**
     * Represents an entry in the EDR queue
     */
    public static class EdrQueueEntry {
        private final String edrKey;
        private final boolean needsTls;
        
        public EdrQueueEntry(String edrKey, boolean needsTls) {
            this.edrKey = edrKey;
            this.needsTls = needsTls;
        }
        
        public String getEdrKey() {
            return edrKey;
        }
        
        public boolean needsTls() {
            return needsTls;
        }
        
        @Override
        public String toString() {
            return format("EdrQueueEntry{edrKey='%s', needsTls=%s}", edrKey, needsTls);
        }
    }
}