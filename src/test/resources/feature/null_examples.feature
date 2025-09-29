Feature: a test with null value in examples

  Scenario Outline: Verify example null value
    Then assert vara == null

    Examples:
      | vara! |
      | null  |
