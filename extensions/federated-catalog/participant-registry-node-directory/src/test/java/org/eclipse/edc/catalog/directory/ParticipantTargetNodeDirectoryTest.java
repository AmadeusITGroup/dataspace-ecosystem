package org.eclipse.edc.catalog.directory;

import org.eclipse.edc.crawler.spi.TargetNode;
import org.eclipse.edc.issuerservice.spi.holder.model.Holder;
import org.eclipse.edc.issuerservice.spi.holder.store.HolderStore;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParticipantTargetNodeDirectoryTest {

    private static final String PARTICIPANT_CONTEXT_ID = UUID.randomUUID().toString();
    private final HolderStore holderStore = mock();
    private final TransactionContext transactionContext = new NoopTransactionContext();
    private final ParticipantToTargetNodeResolver resolver = mock();
    private final Monitor monitor = mock();
    private final ParticipantTargetNodeDirectory directory = new ParticipantTargetNodeDirectory(holderStore, transactionContext, resolver, PARTICIPANT_CONTEXT_ID, monitor);


    private static Holder createHolder(String did) {
        return Holder.Builder.newInstance()
                .holderId(UUID.randomUUID().toString())
                .did(did)
                .participantContextId(UUID.randomUUID().toString())
                .holderName(UUID.randomUUID().toString())
                .build();
    }

    private static Holder createHolder() {
        return createHolder(UUID.randomUUID().toString());
    }

    @Nested
    class GetAll {
        @Test
        void success() {
            var holder1 = createHolder();
            var holder2 = createHolder();
            var self = createHolder(PARTICIPANT_CONTEXT_ID);
            var node1 = mock(TargetNode.class);
            var node2 = mock(TargetNode.class);

            when(resolver.apply(holder1)).thenReturn(Result.success(node1));
            when(resolver.apply(holder2)).thenReturn(Result.success(node2));
            when(holderStore.query(any())).thenReturn(StoreResult.success(List.of(holder1, holder2, self)));

            var result = directory.getAll();

            assertThat(result).containsExactlyInAnyOrder(node1, node2);
            verify(resolver, never()).apply(self);
        }

        @Test
        void returnsOnlyValid() {
            var participant1 = createHolder();
            var participant2 = createHolder();
            var node1 = mock(TargetNode.class);

            when(resolver.apply(participant1)).thenReturn(Result.success(node1));
            when(resolver.apply(participant2)).thenReturn(Result.failure("failure participant2"));
            when(holderStore.query(any())).thenReturn(StoreResult.success(List.of(participant1, participant2)));

            var result = directory.getAll();

            assertThat(result).containsExactly(node1);
            verify(monitor).warning("failure participant2");
        }
    }

    @Nested
    class Insert {
        @Test
        void throwsUnsupportedOperationException() {
            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> directory.insert(mock()));
        }
    }

}
