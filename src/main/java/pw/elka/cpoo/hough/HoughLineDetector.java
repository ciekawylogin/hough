package pw.elka.cpoo.hough;

/**
 * Detektor linii wykorzystujący algorytm Hougha.
 * Projekt zaliczeniowy z przedmiotu CPOO (cyfrowe przetwarzanie obrazów), EITI PW.
 *
 * Autor: Michał Krawczak
 *
 * -----
 *
 * Line detector using Hough algorithm.
 * Assignment project for Digital Image Processing course.
 *
 * Author: Michał Krawczak
 */

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Vector;
import javax.imageio.ImageIO;

/**
 * Represents a single RGB pixel of an image.
 */
class Pixel {
    Pixel(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }
    private int r;
    private int g;
    private int b;
    public int getR() {
        return r;
    }

    public int getG() {
        return g;
    }

    public int getB() {
        return b;
    }
    public String toString() {
        return "(" + getR() + " " + getG() + " " + getB() + ")";
    }
}

/**
 * Represents a straight line in described in Hough coordinates.
 *
 * The line is described by 2 parameters:
 *  - Its distance from (0, 0) point `r`.
 *  - The angle θ between the X axis and the line connecting (0, 0) with the closest point of the line.
 *
 *  |                    /
 *  +-------------------/--------------->
 *  |.. θ  /           /                  X
 *  |  .. /           /
 *  |    ..          /
 *  |      ..       / the line
 *  |     r  ..    /
 *  |          .. /
 *  |            /
 *  |           /
 *  |          /
 *  | Y       /
 *  V
. */
class Line {
    Line(int r, double θ) {
        this.r = r;
        this.θ = θ;
    }
    private int r;
    private double θ;
    public String toString(){
        return "r = " + getR() + ", θ = " + getΘ();
    }

    public int getR() {
        return r;
    }

    public double getΘ() {
        return θ;
    }
}

public class HoughLineDetector {
    public static void main(String[] args) throws IOException {
        int θSteps;
        int houghThreshold;
        String imgName;
        int binarizationThreshold;

        try {
            θSteps = Integer.valueOf(args[0]);
            houghThreshold = Integer.valueOf(args[1]);
            imgName = args[2];
            binarizationThreshold = Integer.valueOf(args[3]);
        } catch (Exception e) {
            System.out.println("Usage: detector <θSteps> <houghThreshold> <imgName> <binarizationThreshold>");
            System.out.println("  θSteps - how many θ values to check?");
            System.out.println("  houghThreshold - how many points does a line need to be acknowledged?");
            System.out.println("  imgName - name of the input image (it should be located in resources)");
            System.out.println("  binarizationThreshold - binarization threshold, needs to be between 0 and 255");

            return;
        }

        HoughLineDetector detector = new HoughLineDetector();
        detector.detectLines(θSteps, houghThreshold, imgName, binarizationThreshold);
    }

    /**
     * Perform the detection. Produce a new image with detected lines overlay.
     */
    private void detectLines(int θSteps, int houghThreshold, String imgName, int binarizationThreshold) throws IOException {
        BufferedImage image = ImageIO.read(getClass().getResource(imgName));
        Pixel[][] imgArray = bufferedImageToPixelArray(image);
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] binarized = binarize(imgArray, width, height, binarizationThreshold);
        int[][] houghArray = makeHoughArray(binarized, width, height, θSteps);
        Line[] lines = pickLines(houghArray, width, height, θSteps, houghThreshold);
        drawLines(image, width, height, lines);
    }

    /**
     * Given a [[BufferedImage]] convert it to 2D array of [[Pixel]]s.
     */
    private static Pixel[][] bufferedImageToPixelArray(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        Pixel[][] result = new Pixel[height][width];

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int rgb = image.getRGB(col, row);
                result[row][col] = new Pixel(rgb & 0xff, (rgb << 8) & 0xff, (rgb << 16) & 0xff);
            }
        }

        return result;
    }

    /**
     * Given an array of [[Pixel]]s, convert it to an array of booleans of the same size.
     *
     * Given pixel is converted fo `true` if its
     */
    private boolean[][] binarize(Pixel[][] arr, int width, int height, int threshold) {
        boolean[][] result = new boolean[height][width];
        final int numChannels = 3;
        for(int y=0; y<height; y++) {
            for(int x=0; x<width; x++) {
                Pixel p = arr[y][x];
                result[y][x] = (p.getR() + p.getG() + p.getB()) < threshold * numChannels;
            }
        }
        return result;
    }

    /**
     * Given a binarized image, convert it to hough coordinates.
     *
     * @return Array of integers indexed by (r, θ). Each integer represents number
     *         of points on the line described by the (r, θ) pair.
     */
    private int[][] makeHoughArray(boolean[][] binarized, int width, int height, int θSteps) {
        int rSteps = (int)(Math.max(width, height) * Math.sqrt(2));
        double θStep = Math.PI * 2 / θSteps;
        int[][] result = new int[rSteps][θSteps];
        for(int θ = 0; θ < θSteps; θ++) {
            for(int r = 0; r < rSteps; r++) {
                result[r][θ] = 0;
            }
        }
        for(int y=0; y<height; y++) {
            for (int x = 0; x < width; x++) {
                if(binarized[y][x]) {
                    insertPoint(θSteps, rSteps, θStep, result, x, y);
                }
            }
        }
        return result;
    }

    /**
     * Given an array representing lines in hough space, pick lines that are "good enough":
     * - They must be above the given threshold.
     *
     */
    private Line[] pickLines(int[][] houghArray, int width, int height, int θSteps, int houghThreshold) {
        int rSteps = (int)(Math.max(width, height) * Math.sqrt(2));
        double θStep = Math.PI * 2 / θSteps;

        Vector<Line> result = new Vector<Line>();

        for(int θ = 0; θ < θSteps; θ++) {
            for(int r = 0; r < rSteps; r++) {
                int elem = houghArray[r][θ];
                boolean isBiggerThanNeighbors =
                        (r == 0 || θ == 0 || elem >= houghArray[r - 1][θ - 1]) &&
                                (r == rSteps - 1 || θ == 0 || elem >= houghArray[r + 1][θ - 1]) &&
                                (r == 0 || θ == θSteps - 1 || elem >= houghArray[r - 1][θ + 1]) &&
                                (r == rSteps - 1 || θ == θSteps - 1 || elem >= houghArray[r + 1][θ + 1]);
                if(elem > houghThreshold &&
                        isBiggerThanNeighbors) {
                    System.out.print(houghArray[r][θ] + " ");
                    result.add(new Line(r, θ * θStep));
                }
            }
        }
        Line[] arrRes = new Line[result.size()];
        return result.toArray(arrRes);
    }

    /**
     * Given a point in the image space, increment the matching lines in the hough space.
     */
    private void insertPoint(int θSteps, int rSteps, double θStep, int[][] result, int x, int y) {
        for (int ts = 0; ts < θSteps; ++ts) {
            double θ = ts * θStep;
            int r = (int) (x * Math.cos(θ) + y * Math.sin(θ));
            if (r >= 0 && r < rSteps) {
                result[r][ts]++;
            }
        }
    }

    /**
     * Given an image, draw the detected lines on it.
     */
    private void drawLines(BufferedImage image, int width, int height, Line[] lines) throws IOException {
        Graphics2D g2d = image.createGraphics();
        BasicStroke bs = new BasicStroke(1);
        g2d.setColor(Color.BLACK);
        g2d.setStroke(bs);

        for(Line line: lines) {
            drawLine(width, height, g2d, line);
        }
        g2d.drawImage(image, null, null);
        File outputfile = new File("out.jpg");
        ImageIO.write(image, "jpg", outputfile);
    }

    /**
     * Given a 2d graphics, draw the given line on it.
     */
    private void drawLine(int width, int height, Graphics2D g2d, Line line) {
        double x0 = line.getR() / Math.cos(line.getΘ());
        double x1 = x0 - Math.tan(line.getΘ()) * height;
        double y0 = line.getR() / Math.cos(Math.PI / 2 - line.getΘ());
        double y1 = y0 - 1/Math.tan(line.getΘ()) * width;

        boolean drawNothing =
                ((x0 < 0 && x1 < 0)
                        || (y0 < 0 && y1 < 0)
                        || (x0 >= width && x1 >= width)
                        || (y0 >= height && y1 >= height));

        if ((y1 > y0) == (x1 > x0)) {
            double tmp = x0;
            x0 = x1;
            x1 = tmp;
        }

        if (x0 < 0) x0 = 0;
        if (x0 >= width) x0 = width;
        if (x1 < 0) x1 = 0;
        if (x1 >= width) x1 = width;
        if (y0 < 0) y0 = 0;
        if (y0 >= height) y0 = height;
        if (y1 < 0) y1 = 0;
        if (y1 >= height) y1 = height;

        if(!drawNothing) g2d.drawLine((int)x0, (int)y0, (int)x1, (int)y1);
    }
}
