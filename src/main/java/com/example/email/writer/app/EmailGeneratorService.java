package com.example.email.writer.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class EmailGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(EmailGeneratorService.class);

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String geminiModel;
    private final String apiKey;

    public EmailGeneratorService(WebClient.Builder webClientBuilder,
                                 @Value("${gemini.api.url}") String geminiBaseUrl,
                                 @Value("${gemini.api.key}") String geminiApiKey,
                                 @Value("${gemini.model:gemini-1.5-flash}") String geminiModel) {

        this.webClient = webClientBuilder
                .baseUrl(geminiBaseUrl)
                .build();

        this.geminiModel = geminiModel;
        this.apiKey = geminiApiKey;
    }

    public String generateEmailReply(EmailRequest emailRequest) {
        try {
            String prompt = buildPrompt(emailRequest);
            log.info("Prompt length: {}", prompt == null ? 0 : prompt.length());

            // Correct request body for Gemini
            Map<String, Object> payload = Map.of(
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(Map.of("text", prompt))
                            )
                    )
            );

            String modelEndpoint = String.format("/v1beta/models/%s:generateContent?key=%s",
                    geminiModel, apiKey);

            String responseBody = webClient.post()
                    .uri(modelEndpoint)
                    .header("Content-Type", "application/json")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (responseBody == null) {
                log.error("Empty response from Gemini");
                return "Empty response from Gemini";
            }

            String extracted = extractResponseContent(responseBody);
            return extracted != null ? extracted : "Could not extract content from Gemini response.";
        } catch (Exception ex) {
            log.error("Error while generating email reply", ex);
            return "Error generating email reply: " + ex.getMessage();
        }
    }

    private String buildPrompt(EmailRequest r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate an email reply for the following email content. ");
        sb.append("Do not include a subject line.\n\n");
        if (r.getTone() != null && !r.getTone().isBlank()) {
            sb.append("Use a ").append(r.getTone()).append(" tone.\n\n");
        }
        sb.append("Original email:\n");
        sb.append(r.getEmailContent() == null ? "" : r.getEmailContent());
        return sb.toString();
    }

    private String extractResponseContent(String response) {
        try {
            JsonNode root = mapper.readTree(response);

            // Gemini response structure: candidates[0].content.parts[0].text
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    JsonNode textNode = parts.get(0).path("text");
                    if (!textNode.isMissingNode()) {
                        return textNode.asText();
                    }
                }
            }

            // fallback if parsing fails
            return root.toString();
        } catch (Exception e) {
            log.error("Error parsing Gemini response", e);
            return "Error parsing response: " + e.getMessage();
        }
    }
}
