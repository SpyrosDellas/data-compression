import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.PriorityQueue;

/**
 * The {@code Huffman} class provides static methods for compressing
 * and expanding any byte-encoded file using Huffman compression.
 *
 * @author Spyros Dellas
 */
public class Huffman {

    private static final int RADIX = 256;

    /*
     * Inner private class defining the Huffman trie
     *
     * Note: This class has a natural ordering that is inconsistent with equals
     */
    private static class Node implements Comparable<Node> {

        private final char letter;  // unused for internal nodes
        private final int frequency;      // used for the trie construction during the compression phase
        private Node left;
        private Node right;

        public Node(char letter, int frequency, Node left, Node right) {
            this.letter = letter;
            this.frequency = frequency;
            this.left = left;
            this.right = right;
        }

        public boolean isLeaf() {
            return left == null && right == null;
        }

        @Override
        public int compareTo(Node that) {
            return this.frequency - that.frequency;
        }
    }

    /**
     * Do not instantiate
     */
    private Huffman() {

    }

    /**
     * Compress the given byte-encoded file using Huffman encoding;
     *
     * @param fileName The name of the file to be compressed
     */
    public static void compress(String fileName) {
        File inputFile = new File(fileName);
        File outputFile = new File(getCompressedFileName(inputFile));
        try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
            // --------------- READ THE FILE INTO A BYTE ARRAY ---------------
            byte[] input = Files.readAllBytes(inputFile.toPath());
            if (input.length == 0)
                return;
            // ------------ CREATE THE HUFFMAN CODE TRIE AND LOOKUP TABLE ------------
            // Compute the frequency counts for each byte
            int[] frequencies = new int[RADIX];
            for (byte c : input) {
                frequencies[c & 0xff]++;
            }
            // Build the trie
            Node root = buildTrie(frequencies);
            // Build the lookup table to speed-up compression
            String[] codes = buildCode(root);
            // --------------- CREATE THE COMPRESSED FILE ---------------
            BinaryOut out = new BinaryOut(fileOutputStream);
            // Part 1 - Write the trie
            writeTrie(root, out);
            // Part 2 - Write the number of characters
            out.write(input.length);
            // Part 3 - Write the compressed data
            for (byte c : input) {
                String code = codes[c & 0xff];
                for (int i = 0; i < code.length(); i++) {
                    out.write(code.charAt(i) == '1');
                }
            }
            out.close();
        } catch (IOException ioException) {
            System.err.println("Could not open " + fileName);
        }
    }

    private static String getCompressedFileName(File file) {
        String path = file.getPath();
        if (path.lastIndexOf(".") == -1)
            return path + ".huf";
        return path.substring(0, path.lastIndexOf(".")) + ".huf";
    }

    private static Node buildTrie(int[] frequencies) {
        PriorityQueue<Node> priorityQueue = new PriorityQueue<>();
        for (char i = 0; i < frequencies.length; i++) {
            if (frequencies[i] > 0)
                priorityQueue.add(new Node(i, frequencies[i], null, null));
        }
        while (priorityQueue.size() > 1) {
            Node left = priorityQueue.poll();
            Node right = priorityQueue.poll();
            Node parent = new Node('\0', left.frequency + right.frequency, left, right);
            priorityQueue.add(parent);
        }
        return priorityQueue.poll();
    }

    /*
     * Traverses the trie in preorder: when we visit an internal node, we write a single 0 bit;
     * when we visit a leaf, we write a 1 bit followed by the 8-bit ASCII code of the character
     * in the leaf
     */
    private static void writeTrie(Node x, BinaryOut out) {
        if (x.isLeaf()) {
            out.write(true);
            out.write(x.letter);
            return;
        } else {
            out.write(false);
        }
        writeTrie(x.left, out);
        writeTrie(x.right, out);
    }

    /*
     * Make a lookup table from the trie for efficiency during compression
     *
     * The lookup table is implemented as a character indexed array that
     * associates a String with each character.
     * */
    private static String[] buildCode(Node root) {
        String[] code = new String[RADIX];
        buildCode(root, code, "");
        return code;
    }

    private static void buildCode(Node x, String[] code, String prefix) {
        if (x.isLeaf()) {
            code[x.letter] = prefix;
            return;
        }
        buildCode(x.left, code, prefix + "0");
        buildCode(x.right, code, prefix + "1");
    }

    /**
     * Expand the given compressed file (encoded using Huffman encoding);
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
            // First read the trie stored in the input file
            Node root = readTrie(in);
            // Read the number of the characters encoded
            int size = in.readInt();
            // Expand the file
            for (int i = 0; i < size; i++) {
                Node current = root;
                while (!current.isLeaf()) {
                    boolean bit = in.readBoolean();
                    if (bit)
                        current = current.right;
                    else
                        current = current.left;
                }
                out.write(current.letter);
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

    private static Node readTrie(BinaryIn in) {
        boolean bit = in.readBoolean();
        if (bit)
            return new Node(in.readChar(), 0, null, null);
        Node parent = new Node('\0', 0, null, null);
        parent.left = readTrie(in);
        parent.right = readTrie(in);
        return parent;
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
        String compressedFileName = "./data/etext99.huf";
        expand(compressedFileName);
        String expandedFileName = "./data/etext99.expanded";
        assertEqual(fileName, expandedFileName);
    }

}
