package com.example;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class TestBase {

    protected WireMockServer wireMockServer;
    protected ApiClient apiClient;
    protected static final String API_KEY = "qazWSXedc";
    protected static final String APP_URL = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().port(8888));
        wireMockServer.start();
        RestAssured.baseURI = APP_URL;
        apiClient = new ApiClient(APP_URL, API_KEY);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }
}