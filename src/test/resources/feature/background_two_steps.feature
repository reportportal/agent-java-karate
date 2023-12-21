Feature: test background with two steps reporting

  Background: Set variable
    Given def vara = 2
    And def varb = 2

  Scenario: Verify math
    Then assert vara * varb == 4
