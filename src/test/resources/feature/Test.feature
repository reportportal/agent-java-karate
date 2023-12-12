Feature: Quote generator

  @To_run
  Scenario: Fetch random quote 1
    Given url 'http://jsonplaceholder.typicode.com/posts'
    Given path '1'
    When method GET
    Then status 200

  @To_run
  Scenario: Fetch random quote 2
    Given url 'http://jsonplaceholder.typicode.com/posts'
    Given path '1'
    When method GET
    Then status 200

  @Ignore
  Scenario: Fetch random quote 3
    Given url 'http://jsonplaceholder.typicode.com/posts'
    Given path '1'
    When method GET
    Then status 200