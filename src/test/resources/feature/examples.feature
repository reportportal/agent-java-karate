Feature: math tests with examples

  Scenario Outline: Verify different maths
    Given def mathResult = vara + varb
    Then assert mathResult == result

    Examples:
      | vara! | varb! | result! |
      | 2     | 2     | 4       |
      | 1     | 2     | 3       |
