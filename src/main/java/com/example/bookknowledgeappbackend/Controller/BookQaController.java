package com.example.bookknowledgeappbackend.Controller;

import com.example.bookknowledgeappbackend.Service.OpenApiModelImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Slf4j
@RequiredArgsConstructor
@CrossOrigin("*")
public class BookQaController {

    @Autowired
    private OpenApiModelImpl openApiModel;
    private static final String DOWNLOAD_DIR = new File(System.getProperty("user.dir"), "downloads").getAbsolutePath();


    @PostMapping(value = "/ask", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> askQuestion(
            @RequestPart("pdf") Mono<FilePart> pdfPartMono,
            @RequestPart("audio") Mono<FilePart> audioPartMono
    ) {
        return pdfPartMono.flatMap(pdf -> {
            Path tempPdfPath = Paths.get(DOWNLOAD_DIR, UUID.randomUUID() + "-" + pdf.filename());

            return pdf.transferTo(tempPdfPath)
                    .then(openApiModel.uploadFileToOpenAI(tempPdfPath.toFile())
                            .doOnNext(fileId -> System.out.println(" File uploaded, OpenAI File ID: {}"+ fileId))
                    )
                    .flatMap(fileId -> openApiModel.transcribeAudio(audioPartMono)
                            .doOnNext(text -> System.out.println(" Transcribed Question Text: {}"+ text))
                            .flatMap(questionText -> openApiModel.getDragonAnswer(fileId, questionText)
                                    .doOnNext(answer -> System.out.println(" GPT Answer: {}"+ answer))
                                    .flatMap(answer -> openApiModel.synthesizeAudio(answer)
                                            .doOnNext(audioUrl -> System.out.println("Synthesized Audio URL: {}"+ audioUrl))
                                            .map(audioUrl -> {
                                                Map<String, Object> response = new HashMap<>();
                                                response.put("answer", answer);
                                                response.put("ttsAudio", audioUrl);
                                                return ResponseEntity.ok(response);
                                            })
                                    )
                            )
                    );
        });
    }

//    @PostMapping(value = "/ask", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public Mono<ResponseEntity<Map<String, Object>>> askQuestion(
//            @RequestPart("pdf") Mono<FilePart> pdfPartMono,
//            @RequestPart("audio") Mono<FilePart> audioPartMono
//    ) {
//        return pdfPartMono.flatMap(pdf -> {
//            Path tempPdfPath = Paths.get(DOWNLOAD_DIR, UUID.randomUUID() + "-" + pdf.filename());
//
//            return pdf.transferTo(tempPdfPath)
//                    .then(openApiModel.uploadFileToOpenAI(tempPdfPath.toFile()))
//                    .doOnNext(fileId -> System.out.println("📄 File uploaded, OpenAI File ID: " + fileId))
//                    .flatMap(fileId ->
//                            openApiModel.transcribeAudio(audioPartMono)
//                                    .doOnNext(text -> System.out.println("🎙️ Transcribed Question: " + text))
//                                    .flatMap(question -> openApiModel.createAssistant(fileId)
//                                            .flatMap(assistantId -> openApiModel.createThread()
//                                                    .flatMap(threadId -> openApiModel.postMessageToThread(threadId, question)
//                                                            .flatMap(messageId -> openApiModel.runAssistant(threadId, assistantId)
//                                                                    .flatMap(runId -> openApiModel.getFinalResponse(threadId, runId)
//                                                                            .flatMap(answer -> {
//                                                                                System.out.println("💬 GPT Answer: " + answer);
//                                                                                return openApiModel.synthesizeAudio(answer)
//                                                                                        .map(audioUrl -> {
//                                                                                            Map<String, Object> response = new HashMap<>();
//                                                                                            response.put("answer", answer);
//                                                                                            response.put("ttsAudio", audioUrl);
//                                                                                            return ResponseEntity.ok(response);
//                                                                                        });
//                                                                            })
//                                                                    )
//                                                            )
//                                                    )
//                                            )
//                                    )
//                    );
//        });
//    }

}
