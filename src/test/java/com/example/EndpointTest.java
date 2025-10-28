package com.example;

import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("Тестирование эндпоинта /endpoint")
@Feature("Авторизация и действия пользователя")
public class EndpointTest extends TestBase {

    @Test
    @DisplayName("Успешная аутентификация (LOGIN) при 200 от /auth")
    @Description("При корректном токене и ответе 200 от внешнего сервиса /auth — возвращается OK")
    public void shouldLoginSuccessfullyWhenAuthReturns200() {
        String token = "A1B2C3D4E5F678901234567890ABCDEF";

        wireMockServer.stubFor(
                post(urlEqualTo("/auth"))
                        .withRequestBody(containing("token=" + token))
                        .willReturn(aResponse().withStatus(200))
        );

        Response response = apiClient.sendRequest(token, "LOGIN");

        verifyResponse(response, 200, "OK");
    }

    @Test
    @DisplayName("Ошибка при LOGIN, если /auth возвращает 500")
    @Description("Согласно ТЗ, должен вернуться 200 + JSON с ошибкой. Фактически приложение возвращает 500 → баг.")
    public void shouldReturnErrorOnLoginWhenAuthFails() {
        String token = "B2C3D4E5F678901234567890ABCDEF1A";

        wireMockServer.stubFor(
                post(urlEqualTo("/auth"))
                        .willReturn(aResponse()
                                .withStatus(500)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{}")
                        )
        );

        Response response = apiClient.sendRequest(token, "LOGIN");

        // Ожидаем 200 по ТЗ, но приложение возвращает 500 → фиксируем баг
        int expectedStatus = 200;
        int actualStatus = response.statusCode();
        String responseBody = response.getBody().asString();

        Allure.addAttachment("Ожидаемый статус (по ТЗ)", String.valueOf(expectedStatus));
        Allure.addAttachment("Фактический статус", String.valueOf(actualStatus));
        Allure.addAttachment("Тело ответа", responseBody);

        assertThat(actualStatus)
                .as("Ожидается %d согласно ТЗ, но приложение возвращает %d (баг)", expectedStatus, actualStatus)
                .isEqualTo(expectedStatus);
    }

    @Test
    @DisplayName("ACTION разрешён только после успешного LOGIN")
    @Description("После успешной аутентификации пользователь может выполнять действия")
    public void shouldAllowActionOnlyAfterLogin() {
        String token = "C3D4E5F678901234567890ABCDEF1AB2";

        wireMockServer.stubFor(post(urlEqualTo("/auth")).willReturn(aResponse().withStatus(200)));
        apiClient.sendRequest(token, "LOGIN");

        wireMockServer.stubFor(post(urlEqualTo("/doAction")).willReturn(aResponse().withStatus(200)));

        Response response = apiClient.sendRequest(token, "ACTION");

        verifyResponse(response, 200, "OK");
    }

    @Test
    @DisplayName("ACTION запрещён без предварительного LOGIN")
    @Description("Попытка выполнить действие без аутентификации должна вернуть ошибку")
    public void shouldDenyActionWithoutLogin() {
        String token = "D4E5F678901234567890ABCDEF1AB2C3";

        Response response = apiClient.sendRequest(token, "ACTION");

        String responseBody = response.getBody().asString();

        Allure.addAttachment("Тело ответа", responseBody);

        assertThat(responseBody)
                .as("Тело ответа должно содержать 'result':'ERROR'")
                .contains("\"result\":\"ERROR\"");

        assertThat(responseBody)
                .as("Ошибка не должна быть связана с форматом токена")
                .doesNotContain("соответствовать")
                .doesNotContain("token:");
    }

    @Test
    @DisplayName("LOGOUT удаляет токен из хранилища")
    @Description("После LOGOUT токен становится недействительным")
    public void shouldLogoutAndInvalidateToken() {
        String token = "E5F678901234567890ABCDEF1AB2C3D4";

        wireMockServer.stubFor(post(urlEqualTo("/auth")).willReturn(aResponse().withStatus(200)));
        apiClient.sendRequest(token, "LOGIN");

        Response logoutResponse = apiClient.sendRequest(token, "LOGOUT");
        verifyResultField(logoutResponse, "OK");

        Response actionResponse = apiClient.sendRequest(token, "ACTION");
        verifyResultField(actionResponse, "ERROR");
    }

    @Test
    @DisplayName("Запрос без X-Api-Key возвращает 401 Unauthorized")
    @Description("Доступ к эндпоинту без API-ключа запрещён")
    public void shouldRejectRequestWithoutApiKey() {
        String token = "F678901234567890ABCDEF1AB2C3D4E5";

        Response response = given()
                .contentType("application/x-www-form-urlencoded")
                .accept("application/json")
                .body("token=" + token + "&action=LOGIN")
                .post(APP_URL + "/endpoint");

        int actualStatus = response.statusCode();
        Allure.addAttachment("Фактический HTTP-статус", String.valueOf(actualStatus));

        assertThat(actualStatus)
                .as("Ожидается 401 при отсутствии X-Api-Key")
                .isEqualTo(401);
    }

    @Step("Проверяем ответ: статус = {expectedStatus}, result = {expectedResult}")
    private void verifyResponse(Response response, int expectedStatus, String expectedResult) {
        int actualStatus = response.statusCode();
        String actualResult = response.jsonPath().getString("result");
        String body = response.getBody().asString();

        Allure.addAttachment("Ожидаемый статус", String.valueOf(expectedStatus));
        Allure.addAttachment("Фактический статус", String.valueOf(actualStatus));
        Allure.addAttachment("Ожидаемое значение 'result'", expectedResult);
        Allure.addAttachment("Фактическое значение 'result'", actualResult);
        Allure.addAttachment("Тело ответа", body);

        assertThat(actualStatus)
                .as("Неверный HTTP-статус")
                .isEqualTo(expectedStatus);

        assertThat(actualResult)
                .as("Неверное значение поля 'result'")
                .isEqualTo(expectedResult);
    }

    @Step("Проверяем, что поле 'result' = {expected}")
    private void verifyResultField(Response response, String expected) {
        String actual = response.jsonPath().getString("result");
        Allure.addAttachment("Ожидаемое значение 'result'", expected);
        Allure.addAttachment("Фактическое значение 'result'", actual);
        assertThat(actual).as("Поле 'result' не совпадает").isEqualTo(expected);
    }
}