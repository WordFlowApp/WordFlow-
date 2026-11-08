package com.wordflow.app;

import java.util.regex.Pattern;

/**
 * Normaliza expresiones argentinas conocidas antes de traducirlas al inglés.
 * Solo modifica frases explícitas para evitar cambiar palabras con otros sentidos.
 */
public final class ArgentineSpanishAdapter {

    private ArgentineSpanishAdapter() { }

    /**
     * Adapta unas pocas expresiones de español neutro al uso argentino.
     * Se mantiene conservador: no sustituye vocabulario aislado de forma general.
     */
    public static String toArgentineSpanish(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = text;
        result = replace(result, "(?i)\\b¿?cómo te encuentras\\??", "¿Cómo andás?");
        result = replace(result, "(?i)\\b¿?qué pasa\\??", "¿Qué onda?");
        result = replace(result, "(?i)\\bestoy muy cansado\\b", "estoy hecho bolsa");
        result = replace(result, "(?i)\\bestoy muy cansada\\b", "estoy hecha bolsa");
        result = replace(result, "(?i)\\bordenador\\b", "computadora");
        result = replace(result, "(?i)\\bmóvil\\b", "celular");
        result = replace(result, "(?i)\\bcoche\\b", "auto");
        result = replace(result, "(?i)\\bconducir\\b", "manejar");
        result = replace(result, "(?i)\\bvosotros\\b", "ustedes");
        return result;
    }

    /**
     * Expande expresiones coloquiales argentinas cuyo significado es bastante
     * estable. Evita reemplazos ambiguos como "plata", "de una" o "vale".
     */
    public static String prepareArgentineInputForTranslation(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = text;
        result = replace(result, "(?i)\\bno tengo un mango\\b", "no tengo nada de dinero");
        result = replace(result, "(?i)\\bestoy sin un mango\\b", "no tengo nada de dinero");
        result = replace(result, "(?i)\\bestoy seco\\b", "no tengo dinero");
        result = replace(result, "(?i)\\bqué onda\\b", "qué pasa");
        result = replace(result, "(?i)\\bque onda\\b", "qué pasa");
        result = replace(result, "(?i)\\bestoy hecho bolsa\\b", "estoy muy cansado");
        result = replace(result, "(?i)\\bestoy hecha bolsa\\b", "estoy muy cansada");
        result = replace(result, "(?i)\\bqué bajón\\b", "qué mala noticia");
        result = replace(result, "(?i)\\bque bajon\\b", "qué mala noticia");
        result = replace(result, "(?i)\\bme mandé una macana\\b", "cometí un error");
        result = replace(result, "(?i)\\bme mande una macana\\b", "cometí un error");
        result = replace(result, "(?i)\\bme estás cargando\\b", "me estás tomando el pelo");
        result = replace(result, "(?i)\\bme estas cargando\\b", "me estás tomando el pelo");
        result = replace(result, "(?i)\\bni en pedo\\b", "de ninguna manera");
        result = replace(result, "(?i)\\bestoy al horno\\b", "estoy en un problema serio");
        result = replace(result, "(?i)\\bqué quilombo\\b", "qué lío");
        result = replace(result, "(?i)\\bque quilombo\\b", "qué lío");
        result = replace(result, "(?i)\\bestá todo piola\\b", "está todo bien");
        result = replace(result, "(?i)\\besta todo piola\\b", "está todo bien");
        result = replace(result, "(?i)\\btodo piola\\b", "todo bien");
        result = replace(result, "(?i)\\bcelu\\b", "celular");
        result = replace(result, "(?i)\\blaburo\\b", "trabajo");
        return result;
    }

    private static String replace(String text, String regex, String replacement) {
        return Pattern.compile(regex).matcher(text).replaceAll(replacement);
    }
}
