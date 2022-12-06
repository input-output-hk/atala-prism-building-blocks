Feature: Issue Credentials

  @RFC0453 @critical @AcceptanceTest
  Scenario: Issue a credential with the Issuer beginning with an offer
    Given 2 agents
      | name | role   |
      | Acme | issuer |
      | Bob  | holder |
    And "Acme" and "Bob" have an existing connection
    When "Acme" offers a credential
    And "Bob" requests the credential
    And "Acme" issues the credential
    And "Bob" acknowledges the credential issue
    Then "Bob" has the credential issued

#  @RFC0453 @AcceptanceTest
#  Scenario: Issue a credential with the Issuer beginning with an offer
#    Given Acme and Bob have an existing connection
#    When Acme offers a credential
#    And Bob requests the credential
#    And Acme issues the credential
#    Then Bob has the credential issued
