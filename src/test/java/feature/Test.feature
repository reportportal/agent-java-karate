Feature: Quote generator

Background:
* url https://jsonplaceholder.typicode.com/todos

Scenario: Fetch random quote

Given path '/1'
When method GET
Then status 200
And match $ == {quote:'#notnull'}