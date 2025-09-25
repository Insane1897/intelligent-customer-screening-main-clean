package com.oracle.ofss.sanctions.cs.app;

import com.ibm.icu.text.Transliterator;
import java.util.ArrayList;
import java.util.List;

public class TransliterationUtil {

    public static List<String> transliterate(String input) {
        List<String> out = new ArrayList<>();
        try {
            Transliterator tr = Transliterator.getInstance("Any-Latin");
            out.add(tr.transliterate(input));
        } catch (IllegalArgumentException e) {
            // skip
        }
        return out;
    }
}
