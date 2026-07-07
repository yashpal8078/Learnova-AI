package com.aistudyassistant.util;

import java.util.List;

public class VectorUtils {

    /**
     * Cosine similarity between float[] and List<Double>
     */
    public static double cosineSimilarity(float[] v1, List<Double> v2) {

        int len = Math.min(v1.length, v2.size());

        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < len; i++) {
            double val2 = v2.get(i);
            dot += v1[i] * val2;
            norm1 += v1[i] * (double) v1[i];
            norm2 += val2 * val2;
        }

        double denominator = Math.sqrt(norm1) * Math.sqrt(norm2);

        if (denominator == 0) return 0.0;

        return dot / denominator;
    }

    /**
     * Cosine similarity between two float arrays
     */
    public static double cosineSimilarity(float[] v1, float[] v2) {

        int len = Math.min(v1.length, v2.length);

        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < len; i++) {
            dot += v1[i] * v2[i];
            norm1 += v1[i] * (double) v1[i];
            norm2 += v2[i] * (double) v2[i];
        }

        double denominator = Math.sqrt(norm1) * Math.sqrt(norm2);

        if (denominator == 0) return 0.0;

        return dot / denominator;
    }
}