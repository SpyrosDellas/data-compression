import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

/**
 * The {@code LZW} class provides static methods for compressing
 * and expanding any byte-encoded file using LZW compression.
 *
 * @author Spyros Dellas
 */
public class LZW {

    private static final short RADIX = 256;  // = 2^8
    private static final short EOF = RADIX;  // Code to mark the End Of File
    private static final int CODE_BITS = 15;
    private static final int NUMBER_OF_CODES = 32767; // = 2^15

    /*
     * Inner private class defining the LZW ternary search trie (TST)
     *
     * In the LZW TST each node has a one-byte character, a code representing the word ending
     * at the node, the length of the word ending at the node and three links.
     * The three links correspond to keys whose current characters are less than, equal
     * to, or greater than the node’s character. We find characters corresponding to keys only
     * when we are traversing the middle links.
     *
     * In LZW tries every prefix of a string key is also a key
     */
    private static class TSTNode {
        private final byte letter;
        private final short code;
        private final short length;
        private TSTNode left;
        private TSTNode middle;
        private TSTNode right;

        public TSTNode(byte letter, short code, short length, TSTNode left, TSTNode middle, TSTNode right) {
            this.letter = letter;
            this.code = code;
            this.length = length;
            this.left = left;
            this.middle = middle;
            this.right = right;
        }
    }

    /**
     * Do not instantiate
     */
    private LZW() {
    }

    /**
     * Compress the given byte-encoded file using LZW encoding;
     *
     * @param fileName The name of the file to be compressed
     */
    public static void compress(String fileName) {
        File inputFile = new File(fileName);
        File outputFile = new File(getCompressedFileName(inputFile));
        try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
            // Read the file
            byte[] input = Files.readAllBytes(inputFile.toPath());
            int fileSizeInBytes = input.length;
            BinaryOut out = new BinaryOut(fileOutputStream);
            // Initialize the LZW trie
            TSTNode[] roots = initializeTrie();
            short code = EOF + 1;
            // Build the compressed file while populating the trie
            int index = 0;
            while (index < fileSizeInBytes) {
                TSTNode root = roots[input[index] & 0xff];
                // Find the longest prefix of the input to be processed that matches a word encoded in the trie
                TSTNode longestPrefix = getLongestPrefix(input, root, index);
                // Write the prefix's code
                out.write(longestPrefix.code, CODE_BITS);
                // Update the index to the next byte after the encoded prefix
                index += longestPrefix.length;
                /*
                // If we've reached the maximum number of codes that can be encoded in CODE_BITS bits,
                // re-initialize the LZW trie
                if (code == NUMBER_OF_CODES) {
                    roots = initializeTrie();
                    code = EOF + 1;
                }
                 */
                // Put the already encoded prefix plus the next character as a new word in the trie
                if (code < NUMBER_OF_CODES && index < fileSizeInBytes) {
                    longestPrefix.middle = put(longestPrefix.middle, input[index], code, (short) (longestPrefix.length + 1));
                    code++;
                }
            }
            out.write(EOF, CODE_BITS);
            out.close();
        } catch (IOException ioException) {
            System.err.println("Could not open " + fileName);
        }
    }

    private static String getCompressedFileName(File file) {
        String path = file.getPath();
        if (path.lastIndexOf(".") == -1)
            return path + ".lzw";
        return path.substring(0, path.lastIndexOf(".")) + ".lzw";
    }

    private static TSTNode[] initializeTrie() {
        TSTNode[] roots = new TSTNode[RADIX];
        short unitLength = 1;
        for (short index = 0; index < roots.length; index++) {
            roots[index] = new TSTNode((byte) index, index, unitLength, null, null, null);
        }
        return roots;
    }

    private static TSTNode getLongestPrefix(byte[] input, TSTNode x, int index) {
        if (x == null || index == input.length)
            return null;
        if (input[index] == x.letter) {
            TSTNode result = getLongestPrefix(input, x.middle, index + 1);
            if (result == null)
                return x;
            return result;
        } else if (input[index] < x.letter) {
            return getLongestPrefix(input, x.left, index);
        } else {
            return getLongestPrefix(input, x.right, index);
        }
    }

    private static TSTNode put(TSTNode x, byte key, short code, short length) {
        if (x == null) {
            return new TSTNode(key, code, length, null, null, null);
        }
        if (key < x.letter) {
            x.left = put(x.left, key, code, length);
        } else {
            x.right = put(x.right, key, code, length);
        }
        return x;
    }

    /**
     * Expand the given compressed file (encoded using LZW encoding);
     *
     * @param fileName The name of the compressed file to be expanded
     */
    public static void expand(String fileName) {
        File inputFile = new File(fileName);
        File outputFile = new File(getExpandedFileName(inputFile));
        try (FileInputStream fileInputStream = new FileInputStream(inputFile);
             FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
            BinaryIn in = new BinaryIn(fileInputStream);
            BinaryOut out = new BinaryOut(fileOutputStream);
            // initialize the code-indexed array that stores the original bytes
            byte[][] codeWords = initializeCodeWords();
            int nextAvailableCode = EOF + 1;
            // read the first codeword and write it in the output file
            int code = in.readChar(CODE_BITS);
            byte[] previousCodeWord = codeWords[code];
            out.write(previousCodeWord[0]);
            // read the remaining codes until the end of file is reached and write
            // the original bytes in the output file
            while (true) {
                code = in.readChar(CODE_BITS);
                if (code == EOF)
                    break;
                byte[] currentCodeWord = codeWords[code];
                // If the codeword doesn't exist, make it from the last one
                if (currentCodeWord == null) {
                    currentCodeWord = new byte[previousCodeWord.length + 1];
                    System.arraycopy(previousCodeWord, 0, currentCodeWord, 0, previousCodeWord.length);
                    currentCodeWord[currentCodeWord.length - 1] = previousCodeWord[0];
                }
                // Write the codeword
                for (byte b : currentCodeWord) {
                    out.write(b);
                }
                /*
                // if the codewords table is full, re-initialize it
                if (nextAvailableCode == NUMBER_OF_CODES) {
                    codeWords = initializeCodeWords();
                    nextAvailableCode = EOF + 1;
                }
                 */
                // Add a new entry to the codewords table
                if (nextAvailableCode < NUMBER_OF_CODES) {
                    byte[] newBytes = new byte[previousCodeWord.length + 1];
                    System.arraycopy(previousCodeWord, 0, newBytes, 0, previousCodeWord.length);
                    newBytes[newBytes.length - 1] = currentCodeWord[0];
                    codeWords[nextAvailableCode] = newBytes;
                    nextAvailableCode++;
                }
                // update previousCodeWord
                previousCodeWord = currentCodeWord;
            }
            out.close();
        } catch (IOException ioException) {
            System.err.println("Could not open " + fileName);
        }
    }

    private static String getExpandedFileName(File file) {
        String path = file.getPath();
        if (path.lastIndexOf(".") == -1)
            return path + ".expanded";
        return path.substring(0, path.lastIndexOf(".")) + ".expanded";
    }

    private static byte[][] initializeCodeWords() {
        byte[][] codeTable = new byte[NUMBER_OF_CODES][];
        for (char index = 0; index < RADIX; index++) {
            codeTable[index] = new byte[]{(byte) index};
        }
        return codeTable;
    }

    /*
     * Test that the original and decompressed files are the same
     */
    private static void assertEqual(String file1, String file2) {
        File f1 = new File(file1);
        File f2 = new File(file2);
        byte[] content1;
        byte[] content2;
        try {
            content1 = Files.readAllBytes(f1.toPath());
            content2 = Files.readAllBytes(f2.toPath());
        } catch (IOException ioException) {
            System.err.println("Original vs decompressed file check failed");
            return;
        }
        if (content1.length != content2.length) {
            System.err.println("Original vs decompressed file check failed");
            return;
        }
        for (int i = 0; i < content1.length; i++) {
            if (content1[i] != content2[i]) {
                System.err.println("Original vs decompressed file check failed");
                return;
            }
        }
        System.out.println("Original vs decompressed file check passed!");
    }

    /*
     *  Unit testing
     */
    public static void main(String[] args) {
        String fileName = "./data/etext99.txt";
        compress(fileName);
        String compressedFileName = "./data/etext99.lzw";
        expand(compressedFileName);
        String expandedFileName = "./data/etext99.expanded";
        assertEqual(fileName, expandedFileName);

    }
}
