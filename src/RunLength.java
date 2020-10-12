import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * The {@code RunLength} class provides static methods for compressing
 * and expanding a binary input using run-length coding with 8-bit
 * run lengths.
 *
 * @author Spyros Dellas
 */
public class RunLength {

    private static final int R = 256;
    private static final int LG_R = 8;

    // Do not instantiate.
    private RunLength() {
    }

    /**
     * Print a binary file to Standard Output
     *
     * @param fileName The name of the binary file
     */
    public static void show(String fileName, int bitsPerLine) {
        File f = new File(fileName);
        try (FileInputStream fileInputStream = new FileInputStream(f)) {
            BinaryIn in = new BinaryIn(fileInputStream);
            int count;
            for (count = 0; !in.isEmpty(); count++) {
                if (count != 0 && count % 8 == 0)
                    System.out.print(" ");
                if (count != 0 && count % bitsPerLine == 0)
                    System.out.println();
                if (in.readBoolean())
                    System.out.print(1);
                else
                    System.out.print(0);
            }
            System.out.println("\n" + count + " bits");
        } catch (IOException ioException) {
            System.err.println("Could not open " + fileName);
        }
    }

    /**
     * Compress the given file using run-length coding with 8-bit run lengths.
     *
     * @param fileName The name of the file to compress
     */
    public static void compress(String fileName) {
        File f = new File(fileName);
        File fCompressed = new File(getCompressedFileName(f));
        try (FileInputStream fileInputStream = new FileInputStream(f);
             FileOutputStream fileOutputStream = new FileOutputStream(fCompressed)) {
            BinaryIn in = new BinaryIn(fileInputStream);
            BinaryOut out = new BinaryOut(fileOutputStream);
            char run = 0;
            boolean old = false;
            while (!in.isEmpty()) {
                boolean b = in.readBoolean();
                if (b != old) {
                    out.write(run, LG_R);
                    run = 1;
                    old = !old;
                } else {
                    if (run == R - 1) {
                        out.write(run, LG_R);
                        run = 0;
                        out.write(run, LG_R);
                    }
                    run++;
                }
            }
            out.write(run, LG_R);
            out.close();
        } catch (IOException ioException) {
            System.err.println("Could not open " + fileName);
        }
    }

    private static String getCompressedFileName(File file) {
        String path = file.getPath();
        if (path.lastIndexOf(".") == -1)
            return path + ".rle";
        return path.substring(0, path.lastIndexOf(".")) + ".rle";
    }

    /**
     * Expand the given compressed file (encoded using run-length encoding
     * with 8-bit run lengths);
     *
     * @param fileName The compressed file to be expanded
     */
    public static void expand(String fileName) {
        File f = new File(fileName);
        File fExpanded = new File(getExpandedFileName(f));
        try (FileInputStream fileInputStream = new FileInputStream(f);
             FileOutputStream fileOutputStream = new FileOutputStream(fExpanded)) {
            BinaryIn in = new BinaryIn(fileInputStream);
            BinaryOut out = new BinaryOut(fileOutputStream);
            boolean b = false;
            while (!in.isEmpty()) {
                int run = in.readInt(LG_R);
                for (int i = 0; i < run; i++)
                    out.write(b);
                b = !b;
            }
            out.close();
        } catch (IOException ioException) {
            System.err.println("Could not open " + fileName);
        }
    }

    private static String getExpandedFileName(File file) {
        String path = file.getPath();
        if (path.lastIndexOf(".") == -1)
            return path + ".b";
        return path.substring(0, path.lastIndexOf(".")) + ".b";
    }

    public static void main(String[] args) {
        String fileName = "D:/Algorithms II/data-compression/data/4runs.bin";
        show(fileName, 80);
        compress(fileName);
        String compressedFileName = "D:/Algorithms II/data-compression/data/4runs.rle";
        show(compressedFileName, 80);
        expand(compressedFileName);
        String expandedFileName = "D:/Algorithms II/data-compression/data/4runs.b";
        show(expandedFileName, 80);

        fileName = "D:/Algorithms II/data-compression/data/q32x48.bin";
        show(fileName, 32);
        compress(fileName);
        compressedFileName = "D:/Algorithms II/data-compression/data/q32x48.rle";
        show(compressedFileName, 32);
        expand(compressedFileName);
        expandedFileName = "D:/Algorithms II/data-compression/data/q32x48.b";
        show(expandedFileName, 32);
    }
}
