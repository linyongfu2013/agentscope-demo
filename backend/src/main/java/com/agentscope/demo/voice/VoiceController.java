package com.agentscope.demo.voice;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class VoiceController {

    private final VoiceService service;

    public VoiceController(VoiceService service) {
        this.service = service;
    }

    @PostMapping(value = "/voice/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<VoiceTranscriptionResponse> transcribe(
            @RequestPart("file") FilePart file,
            @RequestParam(name = "sttModelId", required = false) UUID sttModelId
    ) {
        return DataBufferUtils.join(file.content())
                .map(this::toBytes)
                .map(bytes -> service.transcribe(
                        file.filename(),
                        file.headers().getContentType() == null
                                ? "application/octet-stream"
                                : file.headers().getContentType().toString(),
                        bytes,
                        sttModelId
                ));
    }

    @PostMapping("/voice/speech")
    public VoiceSpeechResponse speech(@Valid @RequestBody VoiceSpeechRequest request) {
        return service.speech(request);
    }

    @GetMapping("/audio-artifacts/{id}")
    public AudioArtifactResponse findArtifact(@PathVariable UUID id) {
        return service.findArtifact(id);
    }

    @GetMapping("/audio-artifacts/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID id) {
        VoiceService.AudioContent content = service.readContent(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, content.mimeType())
                .body(content.bytes());
    }

    private byte[] toBytes(DataBuffer dataBuffer) {
        byte[] bytes = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(bytes);
        DataBufferUtils.release(dataBuffer);
        return bytes;
    }
}
