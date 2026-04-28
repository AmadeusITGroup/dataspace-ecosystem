package org.eclipse.dse.issuerservice.issuance.jsondatabase;

import org.eclipse.edc.issuerservice.spi.issuance.model.AttestationDefinition;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Validator;
import org.eclipse.edc.validator.spi.Violation;

import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.eclipse.dse.issuerservice.issuance.jsondatabase.JsonDatabaseAttestationSource.DATASOURCE_NAME;
import static org.eclipse.dse.issuerservice.issuance.jsondatabase.JsonDatabaseAttestationSource.ID_COLUMN;
import static org.eclipse.dse.issuerservice.issuance.jsondatabase.JsonDatabaseAttestationSource.PROPERTIES_COLUMN;
import static org.eclipse.dse.issuerservice.issuance.jsondatabase.JsonDatabaseAttestationSource.REQUIRED;
import static org.eclipse.dse.issuerservice.issuance.jsondatabase.JsonDatabaseAttestationSource.TABLE_NAME;

public class JsonDatabaseAttestationSourceValidator implements Validator<AttestationDefinition> {

    public static final String ATTESTATION_TYPE = "json-database";
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$");

    @Override
    public ValidationResult validate(AttestationDefinition definition) {
        if (!ATTESTATION_TYPE.equals(definition.getAttestationType())) {
            return ValidationResult.failure(
                    Violation.violation("Expecting attestation type: " + ATTESTATION_TYPE, ATTESTATION_TYPE));
        }
        var config = definition.getConfiguration();

        var requiredResult = requireNonBlankString(config.get(DATASOURCE_NAME), DATASOURCE_NAME);
        if (requiredResult != null) return requiredResult;

        var tableResult = requireSafeIdentifier(config.get(TABLE_NAME), TABLE_NAME);
        if (tableResult != null) return tableResult;

        var propsResult = requireSafeIdentifier(config.get(PROPERTIES_COLUMN), PROPERTIES_COLUMN);
        if (propsResult != null) return propsResult;

        var idColumnValue = config.get(ID_COLUMN);
        if (idColumnValue != null) {
            var idResult = requireSafeIdentifier(idColumnValue, ID_COLUMN);
            if (idResult != null) return idResult;
        }

        var requiredValue = config.get(REQUIRED);
        if (requiredValue != null) {
            var requiredFlagResult = requireBoolean(requiredValue, REQUIRED);
            if (requiredFlagResult != null) return requiredFlagResult;
        }

        return ValidationResult.success();
    }

    private ValidationResult requireNonBlankString(Object value, String key) {
        if (!(value instanceof String stringValue)) {
            return ValidationResult.failure(
                    Violation.violation(format("'%s' must be a non-blank string", key), key));
        }

        if (stringValue.isBlank()) {
            return ValidationResult.failure(
                    Violation.violation(format("'%s' must be a non-blank string", key), key));
        }
        return null;
    }

    private ValidationResult requireSafeIdentifier(Object value, String key) {
        var blankCheck = requireNonBlankString(value, key);
        if (blankCheck != null) return blankCheck;

        if (value instanceof String s && !SAFE_IDENTIFIER.matcher(s).matches()) {
            var message = "'%s' contains unsafe characters. "
                    + "Only alphanumeric, underscores and dots are allowed";
            return ValidationResult.failure(
                    Violation.violation(format(message, key), key));
        }
        return null;
    }

    private ValidationResult requireBoolean(Object value, String key) {
        if (value instanceof Boolean) {
            return null;
        }

        if (value instanceof String stringValue && ("true".equalsIgnoreCase(stringValue) || "false".equalsIgnoreCase(stringValue))) {
            return null;
        }

        return ValidationResult.failure(
                Violation.violation(format("'%s' must be a boolean or a 'true'/'false' string", key), key));
    }
}
