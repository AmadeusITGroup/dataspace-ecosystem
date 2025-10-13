package org.eclipse.edc.test.system;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class PostgresDataVerifier {

    private static final String DB_URL = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:57521/billingdb");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "postgres");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "postgres");

    public static Boolean verifyData(String contractId) {
        String query = "SELECT * FROM telemetry_event WHERE contract_id = ?";


        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {

            preparedStatement.setString(1, contractId);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    System.out.println("No data found for contract ID: " + contractId);
                    return false;
                }
            }
        } catch (Exception e) {
            System.out.println("An error occurred while verifying data for contract ID: " + contractId + " " + e);
        }
        return true;
    }
}