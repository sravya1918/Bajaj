package com.example.bfhqualifier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import java.util.Map;

@SpringBootApplication
public class Application {

    @Value("${hfx.api.generate}")
    private String generateUrl;

    @Value("${hfx.api.submit}")
    private String submitUrl;

    @Value("${hfx.payload.name}")
    private String name;

    @Value("${hfx.payload.regNo}")
    private String regNo;

    @Value("${hfx.payload.email}")
    private String email;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }

    @Bean
    public ApplicationRunner runner(WebClient webClient) {
        return args -> {
            // Step 1: Generate webhook
            Map<String, String> reqBody = Map.of(
                "name", name,
                "regNo", regNo,
                "email", email
            );

            webClient.post()
                .uri(generateUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reqBody)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(resp -> {
                    String webhook = resp.get("webhook").toString();
                    String token = resp.get("accessToken").toString();

                    // Decide odd/even from regNo
                    int lastTwo = extractLastTwoDigits(regNo);
                    boolean odd = lastTwo % 2 == 1;

                    String finalQuery;
                    if (odd) {
                        finalQuery = "-- Your SQL query for Question 1 here";
                    } else {
                        finalQuery = "SELECT e1.EMP_ID, e1.FIRST_NAME, e1.LAST_NAME, " +
                                     "d.DEPARTMENT_NAME, COUNT(e2.EMP_ID) AS YOUNGER_EMPLOYEES_COUNT " +
                                     "FROM EMPLOYEE e1 " +
                                     "JOIN DEPARTMENT d ON e1.DEPARTMENT = d.DEPARTMENT_ID " +
                                     "LEFT JOIN EMPLOYEE e2 ON e1.DEPARTMENT = e2.DEPARTMENT " +
                                     "AND e2.DOB > e1.DOB " +
                                     "GROUP BY e1.EMP_ID, e1.FIRST_NAME, e1.LAST_NAME, d.DEPARTMENT_NAME " +
                                     "ORDER BY e1.EMP_ID DESC;";
                    }

                    Map<String, String> submitBody = Map.of("finalQuery", finalQuery);

                    // Step 2: Submit SQL query
                    return webClient.post()
                        .uri(submitUrl)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(submitBody)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .doOnNext(r -> System.out.println("Submission Response: " + r));
                })
                .block();
        };
    }

    private int extractLastTwoDigits(String regNo) {
        String digits = regNo.replaceAll("\\D+", "");
        if (digits.length() >= 2) {
            return Integer.parseInt(digits.substring(digits.length() - 2));
        } else if (digits.length() == 1) {
            return Integer.parseInt(digits);
        }
        return 0;
    }
}
