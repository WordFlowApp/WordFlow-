
package com.wordflow.app;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Primera capa de adaptación al español argentino.
 * Se ampliará progresivamente con expresiones revisadas.
 */
public final class ArgentineSpanishAdapter {

    private ArgentineSpanishAdapter() {
    }

    /**
     * Adapta algunas traducciones neutras al habla argentina.
     * No reemplaza indiscriminadamente palabras ambiguas.
     */
    public static String toArgentineSpanish(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        result = replace(result, "(?i)\\b¿?cómo estás\\??",
                "¿Cómo estás?");
        result = replace(result, "(?i)\\b¿?cómo te encuentras\\??",
                "¿Cómo andás?");
        result = replace(result, "(?i)\\b¿?qué pasa\\??",
                "¿Qué onda?");
        result = replace(result, "(?i)\\bestá muy bien\\b",
                "está buenísimo");
        result = replace(result, "(?i)\\bes fantástico\\b",
                "está buenísimo");
        result = replace(result, "(?i)\\bestoy muy cansado\\b",
                "estoy hecho bolsa");
        result = replace(result, "(?i)\\bestoy muy cansada\\b",
                "estoy hecha bolsa");
        result = replace(result, "(?i)\\bordenador\\b",
                "computadora");
        result = replace(result, "(?i)\\bmóvil\\b",
                "celular");
        result = replace(result, "(?i)\\bcoche\\b",
                "auto");
        result = replace(result, "(?i)\\bconducir\\b",
                "manejar");
        result = replace(result, "(?i)\\bvosotros\\b",
                "ustedes");
        result = replace(result, "(?i)\\bvale\\b",
                "dale");

        return result;
    }

    /**
     * Convierte expresiones argentinas conocidas a una forma
     * más explícita antes de enviarlas al traductor genérico.
     */
    public static String prepareArgentineInputForTranslation(
            String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        result = replace(result, "(?i)\\b¿?qué onda\\??",
                "¿Qué pasa?");
        result = replace(result, "(?i)\\bestoy hecho bolsa\\b",
                "estoy muy cansado");
        result = replace(result, "(?i)\\bestoy hecha bolsa\\b",
                "estoy muy cansada");
        result = replace(result, "(?i)\\bestá buenísimo\\b",
                "es excelente");
        result = replace(result, "(?i)\\bestá buenísima\\b",
                "es excelente");
        result = replace(result, "(?i)\\bcelu\\b",
                "celular");
        result = replace(result, "(?i)\\blaburo\\b",
                "trabajo");
        result = replace(result, "(?i)\\bplata\\b",
                "dinero");
        result = replace(result, "(?i)\\bguita\\b",
                "dinero");
        result = replace(result, "(?i)\\bquilombo\\b",
                "un gran problema o desorden");
        result = replace(result, "(?i)\\bde una\\b",
                "por supuesto");

        return result;
    }

    private static String replace(
            String text, String regex, String replacement) {
        return Pattern.compile(regex)
                .matcher(text)
                .replaceAll(replacement);
    }
}
