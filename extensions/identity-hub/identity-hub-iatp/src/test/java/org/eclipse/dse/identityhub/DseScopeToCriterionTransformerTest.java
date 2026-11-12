package org.eclipse.dse.identityhub;

import org.eclipse.edc.spi.query.Criterion;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static java.lang.String.join;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dse.identityhub.DseScopeToCriterionTransformer.CONTAINS_OPERATOR;
import static org.eclipse.dse.identityhub.DseScopeToCriterionTransformer.SCOPE_SEPARATOR;
import static org.eclipse.dse.identityhub.DseScopeToCriterionTransformer.TYPE_OPERAND;
import static org.eclipse.edc.spi.core.CoreConstants.DSE_VC_TYPE_SCOPE_ALIAS;

class DseScopeToCriterionTransformerTest {

    private static final String CREDENTIAL_TYPE = UUID.randomUUID().toString();
    private final DseScopeToCriterionTransformer transformer = new DseScopeToCriterionTransformer();

    @Test
    void success() {
        var scope = join(SCOPE_SEPARATOR, DSE_VC_TYPE_SCOPE_ALIAS, CREDENTIAL_TYPE, "read");

        var result = transformer.transform(scope);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).usingRecursiveComparison().isEqualTo(new Criterion(TYPE_OPERAND, CONTAINS_OPERATOR, CREDENTIAL_TYPE));
    }

}