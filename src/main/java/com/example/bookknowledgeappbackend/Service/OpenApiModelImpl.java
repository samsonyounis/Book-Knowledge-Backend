package com.example.bookknowledgeappbackend.Service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenApiModelImpl{
    private WebClient webClient;
    private WebClient privateWebClient;

    private WebClient grokWebClient;
    @Value("${api.key}")
    private String apiKey;
    @Value("${api.key2}")
    private String apiKey2;
    private static final String DOWNLOAD_DIR = new File(System.getProperty("user.dir"), "downloads").getAbsolutePath();
    private String remoteUrl ="https://job-ai-model.onrender.com";
    private String localHurl ="http://localhost:7000";


    @PostConstruct
    public void init() {
        webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .build();
        privateWebClient = WebClient.builder().baseUrl(remoteUrl)
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)).build();
        grokWebClient = WebClient.builder()
                .baseUrl("https://api.x.ai/v1")
                .build();
    }

     public Mono<String> uploadFileToOpenAI(File file) {
        FileSystemResource resource = new FileSystemResource(file);
        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("file", resource);
        formData.add("purpose", "assistants");
       return webClient
                .post()
                .uri("/files")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(formData))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> json.get("id").asText()); // Return file_id

    }

    public Mono<String> transcribeAudio(Mono<FilePart> audioPartMono) {
        return audioPartMono.flatMap(audioPart -> {
            Path tempAudioPath = Paths.get(DOWNLOAD_DIR, UUID.randomUUID() + "-" + audioPart.filename());
            return audioPart.transferTo(tempAudioPath)
                    .thenReturn(tempAudioPath.toFile());
        }).flatMap(audioFile -> {
            MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
            formData.add("file", new FileSystemResource(audioFile));
            formData.add("model", "whisper-1");

            return webClient
                    .post()
                    .uri("/audio/transcriptions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(formData))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(json -> json.get("text").asText()); // return transcription
        });
    }

public Mono<String> askGptWithVectorStore(String question, String vectorStoreId) {
    Map<String, Object> payload = Map.of(
        "model", "gpt-4o",  // or "gpt-4-turbo" or another compatible model
        "messages", List.of(
            Map.of("role", "user", "content", question)
        ),
        "tools", List.of(
            Map.of("type", "file_search")
        ),
        "tool_choice", "auto",
        "vector_store_ids", List.of(vectorStoreId)  // ✅ this must be at root level
    );

    return webClient.post()
            .uri("/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(json -> json.path("choices").get(0).path("message").path("content").asText());
}
    public Mono<String> getDragonAnswer(String fileId, String promptText) {
        HashMap<String, String> payload = new HashMap<>();
        payload.put("file_id",fileId);
        payload.put("question",promptText);

        return privateWebClient.post()
                .uri("/ask/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class);
    }


    public Mono<String> createAssistant(String fileId) {
        Map<String, Object> payload = Map.of(
                "instructions", "You answer questions based on the uploaded file.",
                "model", "gpt-4-1106-preview"

        );
        return webClient.post()
                .uri("/assistants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> json.get("id").asText());
    }
    public Mono<String> createThread() {
        return webClient.post()
                .uri("/threads")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> json.get("id").asText());
    }
    public Mono<String> postMessageToThread(String threadId, String question) {
        Map<String, Object> payload = Map.of(
                "role", "user",
                "content", question
        );

        return webClient.post()
                .uri("/threads/{threadId}/messages", threadId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> json.get("id").asText());
    }
    public Mono<String> runAssistant(String threadId, String assistantId) {
        Map<String, Object> payload = Map.of("assistant_id", assistantId);

        return webClient.post()
                .uri("/threads/{threadId}/runs", threadId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> json.get("id").asText());
    }
    public Mono<String> getFinalResponse(String threadId, String runId) {
        return Mono.defer(() ->
                webClient.get()
                        .uri("/threads/{threadId}/runs/{runId}", threadId, runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .flatMap(runJson -> {
                            String status = runJson.path("status").asText();
                            if ("completed".equals(status)) {
                                return webClient.get()
                                        .uri("/threads/{threadId}/messages", threadId)
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                                        .retrieve()
                                        .bodyToMono(JsonNode.class)
                                        .map(messagesJson -> messagesJson
                                                .path("data").get(0)
                                                .path("content").get(0)
                                                .path("text").path("value").asText()
                                        );
                            } else if ("failed".equals(status)) {
                                return Mono.error(new RuntimeException("Assistant run failed"));
                            } else {
                                // Wait a bit then try again
                                return Mono.delay(Duration.ofSeconds(2))
                                        .flatMap(t -> getFinalResponse(threadId, runId));
                            }
                        })
        );
    }




    public Mono<String> synthesizeAudio(String text) {
        return privateWebClient
                .post()
                .uri("/api/tts/")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(Map.of("text", text))
                .retrieve()
                .bodyToMono(byte[].class)
                .map(bytes -> {
                    // Convert to base64 audio URI
                    String base64 = Base64.getEncoder().encodeToString(bytes);
                    return "data:audio/wav;base64," + base64;
                });
    }

    @Scheduled(fixedRate = 1800000) // 30 minutes in milliseconds
    public void deleteFile() {
        File folder = new File(DOWNLOAD_DIR);
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        file.delete();
                    }
                }
            }
        }
    }


}
