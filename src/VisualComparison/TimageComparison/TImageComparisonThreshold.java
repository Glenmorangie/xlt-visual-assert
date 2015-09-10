package VisualComparison.TimageComparison;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.SystemUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import VisualComparison.ImageComparison;

/**
 * Tests if the threshold is working as expected. 
 * Since the color difference is calculated using the weighted red, green and 
 * blue values, and the weight of red and blue depends on the red level, it is complicated to test.
 * <p>
 * The formula is as follows:
 * 
 * rDiff, gDiff and bDiff: Difference in red, green and blue values.
 * rWeight, gWeight and bWeight: The weight the color differences get.
 * r1, r2: The red values of color 1 and two.
 * <p>
 * rWeight = 2 + ((r1 + r2) / 512)
 * gWeight = 4
 * bWeigth = 2 + ((r1 + r2 - 255) / 256)
 * <p>
 * maxDiff = sqrt(rWeight * rDiff² + gWeight * gDiff² + bWeight * bDiff²)
 * <p>
 * The formula is tested aainst green and blue and two mixes including red.
 * Each time it is tested against a threshold just below the actual difference, exactly the difference
 * and barely above the difference. The first should be false, the other two true.
 * <br>
 * It is also tested against black and white with a threshold of 0, barely below 1 and 1.
 * Here only a threshold of 1 should return true
 * <p>
 * It only tests the pixelFuzzyEqual algorithm, since the fuzzyEqual 
 * algorithm uses the pixelFuzzyEqual algorithm.
 * 
 * @author damian
 *
 */
public class TImageComparisonThreshold {
	private static BufferedImage reference = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
	private static BufferedImage screenshot = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);

	
	private final static File directory = SystemUtils.getJavaIoTmpDir();
	private static File fileMask = new File(directory, "/fileMask.png");
	private static File fileOut = new File(directory, "/fileOut.png");
	
	/**
	 * Tests a black image against a white image with a threshold of 0
	 * 
	 * @throws IOException
	 */
	@Test	
	public void backWhiteTZero() throws IOException {	
		paintItBlackWhite();
		ImageComparison imagecomparison = new ImageComparison(1, 0.0, false, false, "PIXELFUZZYEQUAL");
		boolean result = imagecomparison.isEqual(reference, screenshot, fileMask, fileOut);
		Assert.assertFalse(result);
	}
	
	/**
	 * Tests a black image against a white image with a threshold of almost 1
	 * 
	 * @throws IOException
	 */
	@Test	
	public void backWhiteTAlmostOne() throws IOException {
		paintItBlackWhite();
		ImageComparison imagecomparison = new ImageComparison(1, 0.9999999999999, false, false, "PIXELFUZZYEQUAL");
		boolean result = imagecomparison.isEqual(reference, screenshot, fileMask, fileOut);
		Assert.assertFalse(result);
	}
	
	/**
	 * Tests a black image against a white image with a threshold of 1
	 * 
	 * @throws IOException
	 */
	@Test	
	public void backWhiteTOne() throws IOException {	
		paintItBlackWhite();
		ImageComparison imagecomparison = new ImageComparison(1, 1, false, false, "PIXELFUZZYEQUAL");
		boolean result = imagecomparison.isEqual(reference, screenshot, fileMask, fileOut);
		Assert.assertTrue(result);
	}
	
	/**
	 * Tests a green image against a blue image with a threshold of exactly the expected difference
	 * 
	 * @throws IOException
	 */
	@Test	
	public void greenBlueTExactly() throws IOException {	
		paintItGreenBlue();
		ImageComparison imagecomparison = new ImageComparison(1, 0.8660254037844386, false, false, "PIXELFUZZYEQUAL");
		boolean result = imagecomparison.isEqual(reference, screenshot, fileMask, fileOut);
		Assert.assertTrue(result);
	}
	
	/**
	 * Tests a green image against a blue image with a threshold barely below the difference
	 * 
	 * @throws IOException
	 */
	@Test	
	public void greenBlueTBelow() throws IOException {
		paintItGreenBlue();
		ImageComparison imagecomparison = new ImageComparison(1, 0.8660254037844385, false, false, "PIXELFUZZYEQUAL");
		boolean result = imagecomparison.isEqual(reference, screenshot, fileMask, fileOut);
		Assert.assertFalse(result);
	}
	
	/**
	 * Tests a green image against a blue image with a threshold barely above the difference
	 * 
	 * @throws IOException
	 */
	@Test	
	public void greenBlueTAbove() throws IOException {		
		paintItGreenBlue();
		ImageComparison imagecomparison = new ImageComparison(1, 0.86602540378443861, false, false, "PIXELFUZZYEQUAL");
		boolean result = imagecomparison.isEqual(reference, screenshot, fileMask, fileOut);
		Assert.assertTrue(result);
	}
	
	/**
	 * Tests an image with r, b, g = 100 against an image with r, b, g = 200 
	 * with a threshold of exactly the expected difference
	 * 
	 * @throws IOException
	 */
	@Test	
	public void differentTExactly() throws IOException {	
		paintItDifferent();
		ImageComparison imagecomparison = new ImageComparison(1, 0.39215686274509803, false, false, "PIXELFUZZYEQUAL");
		boolean result = imagecomparison.isEqual(reference, screenshot, fileMask, fileOut);
		Assert.assertTrue(result);
	}
	
	/**
	 * Tests an image with r, b, g = 100 against an image with r, b, g = 200 
	 * with a threshold barely below the difference
	 * 
	 * @throws IOException
	 */
	@Test	
	public void differentTBelow() throws IOException {
		paintItDifferent();
		ImageComparison imagecomparison = new ImageComparison(1, 0.3921568627, false, false, "PIXELFUZZYEQUAL");
		boolean result = imagecomparison.isEqual(reference, screenshot, fileMask, fileOut);
		Assert.assertFalse(result);
	}
	
	/**
	 * Tests an image with r, b, g = 100 against an image with r, b, g = 200 
	 * with a threshold barely above the difference
	 * 
	 * @throws IOException
	 */
	@Test	
	public void differentTAbove() throws IOException {		
		paintItDifferent();
		ImageComparison imagecomparison = new ImageComparison(1, 0.3921568628, false, false, "PIXELFUZZYEQUAL");
		boolean result = imagecomparison.isEqual(reference, screenshot, fileMask, fileOut);
		Assert.assertTrue(result);
	}
	
	private void paintItGreenBlue() {
		reference = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
		int red = 0;
		int green = 255;
		int blue = 0;
		int rgb = (red << 16) | (green << 8) | blue;
		
		for (int w=0; w<reference.getWidth(); w++) { 
			for (int h=0; h<reference.getHeight(); h++) {
				reference.setRGB(w, h, rgb);
			}
		}
		
		screenshot = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
		red = 0;
		green = 0;
		blue = 255;
		rgb = (red << 16) | (green << 8) | blue;
		
		for (int w=0; w<screenshot.getWidth(); w++) { 
			for (int h=0; h<screenshot.getHeight(); h++) {
				screenshot.setRGB(w, h, rgb);
			}
		}
	}
	
	private void paintItDifferent() {
		reference = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
		int red = 100;
		int green = 100;
		int blue = 100;
		int rgb = (red << 16) | (green << 8) | blue;
		
		for (int w=0; w<reference.getWidth(); w++) { 
			for (int h=0; h<reference.getHeight(); h++) {
				reference.setRGB(w, h, rgb);
			}
		}
		
		screenshot = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
		red = 200;
		green = 200;
		blue = 200;
		rgb = (red << 16) | (green << 8) | blue;
		
		for (int w=0; w<screenshot.getWidth(); w++) { 
			for (int h=0; h<screenshot.getHeight(); h++) {
				screenshot.setRGB(w, h, rgb);
			}
		}
	}
	
	@BeforeClass
	public static void paintItBlackWhite() {
		reference = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
		int red = 255;
		int green = 255;
		int blue = 255;
		int rgb = (red << 16) | (green << 8) | blue;
		
		for (int w=0; w<reference.getWidth(); w++) { 
			for (int h=0; h<reference.getHeight(); h++) {
				reference.setRGB(w, h, rgb);
			}
		}
		
		screenshot = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
		red = 0;
		green = 0;
		blue = 0;
		rgb = (red << 16) | (green << 8) | blue;
		
		for (int w=0; w<screenshot.getWidth(); w++) { 
			for (int h=0; h<screenshot.getHeight(); h++) {
				screenshot.setRGB(w, h, rgb);
			}
		}
	}
	
	/**
	 * Deletes the temporary files which were created for this test
	 */
	@AfterClass 
	public static void deleteFiles() {
		fileMask.delete();
		fileOut.delete();
	}
	
}
