Feature: calling another feature file

  Scenario: calling a feature with parameters
    * def result = call read('called.feature') { vara: 2, result: 4 }
