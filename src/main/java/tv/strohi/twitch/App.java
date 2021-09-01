package tv.strohi.twitch;

import org.apache.commons.text.StringEscapeUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class App {
    private final HashMap<String, String> replacementTable = new HashMap<>();

    public static void main(String[] args) {
        new App().run();
    }

    private void run() {
        buildCharacterReplacementTable();

        String encoded = "Ｊ໐ᏳԌЕℜ";

        StringBuilder builder = new StringBuilder();
        for (char c : encoded.toCharArray()) {
            String chString = Character.toString(c);
            if (replacementTable.containsKey(chString)) {
                builder.append(replacementTable.get(chString));
            }
        }


        System.out.println(builder.toString());
        System.out.println("");
    }

    private String convertUnicodeToSurrogatePair(String str) {
        int value = Integer.parseInt(str, 16);
        int h = (value - 0x10000) / 0x400 + 0xD800;
        int l = (value - 0x10000) % 0x400 + 0xDC00;

        return String.format("\\u%s\\u%s", Integer.toString(h, 16), Integer.toString(l, 16));
    }

    private void buildCharacterReplacementTable() {
        String[] asciiChars = getCharactersOfCharset();
        List<String[]> yetUnknownReplacements = new ArrayList<>();

        InputStream in = getClass().getResourceAsStream("/confusables-short.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        List<String> lines = new ArrayList<>();
        try {
            while (reader.ready()) {
                lines.add(reader.readLine().replaceAll("\uFEFF", ""));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        for (String line : lines) {
            String[] reps = line.split(";");
            String initialCharacter = getDecodedUtf8String(reps[0]);
            String replacementResult = getDecodedUtf8String(reps[1]).replace("(", "").replace(")", "");

            if (replacementResult.chars().mapToObj(c -> new String(new char[]{(char) c}))
                    .allMatch(str -> Arrays.asList(asciiChars).contains(str))) {
                System.out.println("Add: " + initialCharacter + " -> " + replacementResult);
                replacementTable.put(initialCharacter, replacementResult);
            } else {
                yetUnknownReplacements.add(new String[]{initialCharacter, replacementResult});
            }
        }

        int happened;
        do {
            happened = 0;

            for (int i = 0; i < yetUnknownReplacements.size(); i++) {
                if (replacementTable.containsKey(yetUnknownReplacements.get(i)[1])) {
                    System.out.println("Add: " + yetUnknownReplacements.get(i)[0] + " -> " + yetUnknownReplacements.get(i)[1]);
                    replacementTable.put(yetUnknownReplacements.get(i)[0], replacementTable.get(yetUnknownReplacements.get(i)[1]));

                    yetUnknownReplacements.remove(i);
                    i--;

                    happened++;
                }
            }
        } while (happened > 0);

        for (String[] unknown : yetUnknownReplacements) {
            System.out.println("Add remove: " + unknown[0] + " -> ");
            replacementTable.put(unknown[0], "");
        }
    }

    private String getDecodedUtf8String(String encoded) {
        StringBuilder builder = new StringBuilder();

        String[] parts = encoded.split(" ");
        for (String part : parts) {
            String unicodeEscapeSequence;
            if (part.trim().length() > 4) {
                unicodeEscapeSequence = convertUnicodeToSurrogatePair(part.trim());
            } else {
                unicodeEscapeSequence = "\\u" + part.trim();
            }

            builder.append(StringEscapeUtils.unescapeJava(unicodeEscapeSequence));
        }

        return builder.toString();
    }

    private String[] getCharactersOfCharset() {
        Charset charset = StandardCharsets.US_ASCII;
        SortedMap<BigInteger, String> charsInEncodedOrder = new TreeMap<>();
        for (int i = 32; i < Character.MAX_VALUE; i++) {
            String s = Character.toString((char) i);
            byte[] encoded = s.getBytes(charset);
            String decoded = new String(encoded, charset);
            if (s.equals(decoded)) {
                charsInEncodedOrder.put(new BigInteger(1, encoded), s);
            }
        }

        return charsInEncodedOrder.values().toArray(String[]::new);
    }
}
