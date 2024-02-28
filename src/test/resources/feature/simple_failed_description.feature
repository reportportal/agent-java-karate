Feature: the test to show last error log in scenario description and with actual description

  Scenario: Verify math

  This is my Scenario description.

    Given def four = 4
    When def actualFour = 2 * 2
    Then assert actualFour != four
