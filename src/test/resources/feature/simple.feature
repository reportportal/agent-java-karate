Feature: the very basic test to run by Karate

  Scenario: Verify response code
    Given url 'https://example.com/'
    When method GET
    Then status 200
