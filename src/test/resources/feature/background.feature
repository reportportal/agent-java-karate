Feature: the test to show item description reporting

  Background: Set variable
    Given def four = 4

  Scenario: Verify math
    When def acualFour = 2 * 2
    Then assert acualFour == four
