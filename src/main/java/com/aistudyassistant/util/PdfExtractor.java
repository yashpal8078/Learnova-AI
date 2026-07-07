package com.aistudyassistant.util;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
public class PdfExtractor {

    private final Tika tika = new Tika();

    public String extractText(String filePath) throws IOException, TikaException {
        File file = new File(filePath);
        return tika.parseToString(file);
    }
}