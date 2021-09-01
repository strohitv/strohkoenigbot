package tv.strohi.twitch;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Hello world!
 *
 */
public class App 
{
    private final HashMap<String, String> replacementTable = new HashMap<>();

    public static void main( String[] args )
    {
        new App().run();
    }

    private void run() {
        buildCharacterReplacementTable();
        System.out.println("");
    }

    private void buildCharacterReplacementTable(){
        String[] asciiChars = getCharactersOfCharset();
        List<String[]> yetUnknownReplacements = new ArrayList<>();

        InputStream in = getClass().getResourceAsStream("/confusables-short.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        List<String> lines = new ArrayList<>();
        try {
            while(reader.ready()) {
                lines.add(reader.readLine());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        for (String line : lines) {
            String[] reps = line.split(";");
            String initialCharacter = getDecodedUtf8String(reps[0]);
            String replacementResult = getDecodedUtf8String(reps[1]).replace("(", "").replace(")", "");

            if (replacementResult.chars().mapToObj(c -> new String(new char[] {(char)c}))
                    .allMatch(str -> Arrays.asList(asciiChars).contains(str))) {
                System.out.println("Add: " + initialCharacter + " -> " + replacementResult);
                replacementTable.put(initialCharacter, replacementResult);
            } else {
                yetUnknownReplacements.add(new String[] {initialCharacter, replacementResult});
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
        } while(happened > 0);

        for (String[] unknown : yetUnknownReplacements) {
            System.out.println("Add: " + unknown[0] + " -> " + unknown[1]);
            replacementTable.put(unknown[0], "");
        }
    }

    private String getDecodedUtf8String(String encoded) {
        List<Byte> bytes = new ArrayList<>();

        String[] parts = encoded.split(" ");
        for (String part : parts) {
            try {
                String fixedpart = part.replaceAll("\uFEFF", "").trim();
                if (fixedpart.length() % 2 != 0) {
                    fixedpart = "0" + fixedpart;
                }

                byte[] decoded = Hex.decodeHex(fixedpart);
                for (byte b : decoded) {
                    bytes.add(b);
                }
            } catch (DecoderException e) {
                e.printStackTrace();
            }
        }

        while (bytes.size() > 0 && bytes.get(0) == 0) {
            bytes.remove(0);
        }

        byte[] result = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            if (bytes.get(i) != 0) {
                result[i] = bytes.get(i);
            }
        }

        return new String(result, StandardCharsets.UTF_8);
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
