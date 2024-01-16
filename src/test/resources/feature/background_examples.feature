Feature: test background with Scenario Outline reporting

  Background: Set varb
    Given def varb = 2

  Scenario Outline: Verify different maths
    Given def mathResult = vara + varb
    Then assert mathResult == result

    Examples:
      | vara! | result! |
      | 2     | 4       |
      | 1     | 3       |
