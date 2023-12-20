Feature: test with parameter table
  Scenario: Verify parameter table
    Given def one = 'hello'
    And def two = { baz: 'world' }
    And table json
      | foo     | bar            |
      | one     | { baz: 1 }     |
      | two.baz | ['baz', 'ban'] |
    Then match json == [{ foo: 'hello', bar: { baz: 1 } }, { foo: 'world', bar: ['baz', 'ban'] }]
