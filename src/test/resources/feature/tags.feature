@tag_test
Feature: the very basic to test different tags

  @math @scope=smoke @environment=dev,qa
  Scenario: Verify math
    Given def four = 4
    When def actualFour = 2 * 2
    Then assert actualFour == four
