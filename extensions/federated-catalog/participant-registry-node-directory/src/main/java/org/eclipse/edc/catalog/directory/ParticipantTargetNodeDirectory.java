package org.eclipse.edc.catalog.directory;


import org.eclipse.edc.crawler.spi.TargetNode;
import org.eclipse.edc.crawler.spi.TargetNodeDirectory;
import org.eclipse.edc.issuerservice.spi.holder.store.HolderStore;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.AbstractResult;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.util.List;
import java.util.stream.Collectors;

public class ParticipantTargetNodeDirectory implements TargetNodeDirectory {

    private final HolderStore holderStore;
    private final TransactionContext transactionContext;
    private final ParticipantToTargetNodeResolver resolver;
    private final String participantContextId;
    private final Monitor monitor;

    public ParticipantTargetNodeDirectory(HolderStore holderStore, TransactionContext transactionContext, ParticipantToTargetNodeResolver resolver, String participantContextId, Monitor monitor) {
        this.holderStore = holderStore;
        this.transactionContext = transactionContext;
        this.resolver = resolver;
        this.participantContextId = participantContextId;
        this.monitor = monitor;
    }


    @Override
    public List<TargetNode> getAll() {
        return transactionContext.execute(() -> ServiceResult.from(holderStore.query(QuerySpec.max())))
                .map(participants -> participants.stream()
                        .filter(holder -> !holder.getDid().equals(participantContextId))
                        .map(resolver)
                        .peek(result -> {
                            if (result.failed()) {
                                monitor.warning(result.getFailureDetail());
                            }
                        })
                        .filter(AbstractResult::succeeded)
                        .map(AbstractResult::getContent)
                        .collect(Collectors.toList()))
                .orElse(failure -> {
                    monitor.severe(failure.getFailureDetail());
                    return List.of();
                });
    }

    @Override
    public void insert(TargetNode targetNode) {
        throw new UnsupportedOperationException("Cannot add participant in directory");
    }

    @Override
    public TargetNode remove(String s) {
        throw new UnsupportedOperationException("Cannot remove participant in directory");
    }

}
