Feature: the very basic test to run by Karate

  @ignore @execute-test
  Scenario: Power of two
    * def expected = expectedValue
    * def powValue = testValue
    * def actual = powValue * powValue
    * assert actual == expected

  Scenario: Verify math
    * def expectedValue = 4
    * def testValue = 2
    * call read('@execute-test') { expectedValue: #(expectedValue), testValue: #(testValue) }
