package org.eclipse.dse.core.telemetry;

import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.util.concurrency.LockManager;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class TokenCache {

    private final LockManager lockManager;
    private TokenRepresentation credential;

    public TokenCache() {
        lockManager = new LockManager(new ReentrantReadWriteLock(true));
    }

    public void save(TokenRepresentation credential) {
        lockManager.writeLock(() -> this.credential = credential);
    }

    @Nullable
    public TokenRepresentation get() {
        return lockManager.readLock(() -> this.credential);
    }
}
