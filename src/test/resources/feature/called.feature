@ignore
Feature: a feature which is called with parameters

  Scenario: Verify different maths
    Given def varb = 2
    Given def mathResult = vara + varb
    Then assert mathResult == result
