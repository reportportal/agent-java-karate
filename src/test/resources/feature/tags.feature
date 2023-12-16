@tag_test
Feature: the very basic to test different tags

  @math @scope=smoke @environment=dev,qa
  Scenario: Verify math
    Given def four = 4
    When def acualFour = 2 * 2
    Then assert acualFour == four
