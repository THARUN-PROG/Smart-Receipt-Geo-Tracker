package org.example.util;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;

public class TesseractConfig {
    public static ITesseract getTesseractInstance() {
        Tesseract tesseract = new Tesseract();
        // Use your actual installed Tesseract tessdata path
        tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata");
        tesseract.setLanguage("eng");
        tesseract.setOcrEngineMode(1); // 0..3; 1 is LSTM only often good for printed text
        return tesseract;
    }
}
