Feature:

  Scenario: Verify math
    Given def four = 4
    When def actualFour = 2 * 2
    Then assert actualFour == four
