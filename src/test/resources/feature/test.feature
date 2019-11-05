Feature: Quote generator
  Scenario: Fetch random quote
    Given url 'http://jsonplaceholder.typicode.com/posts'
    Given path '1'
    When method GET
    Then status 200
