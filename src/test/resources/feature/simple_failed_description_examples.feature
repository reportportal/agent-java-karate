Feature: the test to show last error log in scenario description and with actual description and examples

  Scenario Outline: Verify math

  This is my Scenario description.

    Given def mathResult = <vara> + <varb>
    Then assert mathResult == <result>

    Examples:
      | vara | varb | result |
      | 2    | 2    | 4      |
      | 1    | 2    | 5      |
