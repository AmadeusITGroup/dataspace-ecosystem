package org.eclipse.eonax.identityhub;

import org.eclipse.edc.spi.query.Criterion;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static java.lang.String.join;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.spi.core.CoreConstants.EONAX_VC_TYPE_SCOPE_ALIAS;
import static org.eclipse.eonax.identityhub.EonaxScopeToCriterionTransformer.CONTAINS_OPERATOR;
import static org.eclipse.eonax.identityhub.EonaxScopeToCriterionTransformer.SCOPE_SEPARATOR;
import static org.eclipse.eonax.identityhub.EonaxScopeToCriterionTransformer.TYPE_OPERAND;

class EonaxScopeToCriterionTransformerTest {

    private static final String CREDENTIAL_TYPE = UUID.randomUUID().toString();
    private final EonaxScopeToCriterionTransformer transformer = new EonaxScopeToCriterionTransformer();

    @Test
    void success() {
        var scope = join(SCOPE_SEPARATOR, EONAX_VC_TYPE_SCOPE_ALIAS, CREDENTIAL_TYPE, "read");

        var result = transformer.transform(scope);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).usingRecursiveComparison().isEqualTo(new Criterion(TYPE_OPERAND, CONTAINS_OPERATOR, CREDENTIAL_TYPE));
    }

}