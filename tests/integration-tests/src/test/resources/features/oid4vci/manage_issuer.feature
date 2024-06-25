@oid4vci
Feature: Manage OID4VCI credential issuer

Scenario: Successfully create credential issuer
    When Issuer creates an oid4vci issuer
    Then Issuer sees the oid4vci issuer exists on the agent
    And Issuer sees the oid4vci issuer on IssuerMetadata endpoint

Scenario: Successfully update credential issuer
    Given Issuer has an existing oid4vci issuer
    When Issuer updates the oid4vci issuer
    Then Issuer sees the oid4vci issuer updated with new values
    And Issuer sees the oid4vci IssuerMetadata endpoint updated with new values

Scenario: Successfully delete credential issuer
    Given Issuer has an existing oid4vci issuer
    When Issuer deletes the oid4vci issuer
    Then Issuer cannot see the oid4vci issuer on the agent
    And Issuer cannot see the oid4vci IssuerMetadata endpoint

@dev
Scenario: Successfully create credential configuration
    Given Issuer has a published DID for JWT
    And Issuer has published STUDENT_SCHEMA schema
    And Issuer has an existing oid4vci issuer
    When Issuer uses STUDENT_SCHEMA to create a credential configuration "StudentProfile"
    Then Issuer sees the "StudentProfile" credential configuration on IssuerMetadata endpoint
