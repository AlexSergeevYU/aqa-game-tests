package com.example;

import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

public class ApiClient {
    private final String baseUrl;
    private final String apiKey;

    public ApiClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public Response sendRequest(String token, String action) {
        return given()
                .header("X-Api-Key", apiKey)
                .contentType("application/x-www-form-urlencoded")
                .accept("application/json")
                .body("token=" + token + "&action=" + action)
                .post(baseUrl + "/endpoint");
    }
}