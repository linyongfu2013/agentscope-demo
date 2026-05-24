package com.agentscope.demo.rag;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

@Component
public class TikaDocumentParser implements DocumentParser {

    private final AutoDetectParser parser = new AutoDetectParser();

    @Override
    public List<String> parse(byte[] content, String filename) {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        BodyContentHandler handler = new BodyContentHandler(-1);
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            parser.parse(input, handler, metadata, new ParseContext());
            return chunk(handler.toString());
        } catch (TikaException | SAXException | java.io.IOException error) {
            throw new IllegalArgumentException("Unable to parse document: " + filename, error);
        }
    }

    private List<String> chunk(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        String[] sections = normalized.split("(?m)(?=^#{1,6}\\s)|\\n\\s*\\n");
        List<String> chunks = new ArrayList<>();
        for (String section : sections) {
            String clean = section.replaceAll("[ \\t]+", " ").trim();
            if (!clean.isEmpty()) {
                chunks.add(clean);
            }
        }
        return chunks;
    }
}
