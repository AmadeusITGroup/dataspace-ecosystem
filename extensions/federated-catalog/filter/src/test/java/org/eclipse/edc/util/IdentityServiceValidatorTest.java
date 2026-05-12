package org.eclipse.edc.util;

import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.iam.VerificationContext;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.util.IdentityServiceValidator.READ_ALL_CREDENTIAL_SCOPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityServiceValidatorTest {

    private final IdentityService identityService = mock();
    private final Monitor monitor = mock();
    private final IdentityServiceValidator validator = new IdentityServiceValidator(identityService, monitor, "test-participant");

    @Test
    void validate_success_shouldReturnClaimToken() {
        var token = TokenRepresentation.Builder.newInstance().token("jwt").build();
        var claimToken = ClaimToken.Builder.newInstance().build();
        when(identityService.verifyJwtToken(eq("test-participant"), eq(token), any(VerificationContext.class)))
                .thenReturn(Result.success(claimToken));

        var result = validator.validate(token);

        assertThat(result).isSameAs(claimToken);
    }

    @Test
    void validate_shouldPassParticipantContextId() {
        var token = TokenRepresentation.Builder.newInstance().token("jwt").build();
        var claimToken = ClaimToken.Builder.newInstance().build();
        when(identityService.verifyJwtToken(any(), any(), any(VerificationContext.class)))
                .thenReturn(Result.success(claimToken));

        validator.validate(token);

        verify(identityService).verifyJwtToken(eq("test-participant"), eq(token), any(VerificationContext.class));
    }

    @Test
    void validate_shouldPassCorrectScopes() {
        var token = TokenRepresentation.Builder.newInstance().token("jwt").build();
        var claimToken = ClaimToken.Builder.newInstance().build();
        ArgumentCaptor<VerificationContext> ctxCaptor = ArgumentCaptor.forClass(VerificationContext.class);
        when(identityService.verifyJwtToken(any(), any(), ctxCaptor.capture()))
                .thenReturn(Result.success(claimToken));

        validator.validate(token);

        var ctx = ctxCaptor.getValue();
        assertThat(ctx.getScopes()).containsExactly(READ_ALL_CREDENTIAL_SCOPE);
    }

    @Test
    void validate_failure_shouldLogWarning() {
        var token = TokenRepresentation.Builder.newInstance().token("jwt").build();
        when(identityService.verifyJwtToken(any(), any(), any(VerificationContext.class)))
                .thenReturn(Result.failure("token expired"));

        validator.validate(token);

        verify(monitor).warning(any(String.class));
    }
}
