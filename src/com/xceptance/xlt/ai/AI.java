package com.xceptance.xlt.ai;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.junit.Assert;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.xceptance.xlt.ai.machine_learning.ActivationNetwork;
import com.xceptance.xlt.ai.machine_learning.BipolarSigmoidFunction;
import com.xceptance.xlt.ai.pre_processing.ImageTransformation;
import com.xceptance.xlt.ai.machine_learning.PerceptronLearning;
import com.xceptance.xlt.ai.image.FastBitmap;
import com.xceptance.xlt.ai.image.PatternHelper;
import com.xceptance.xlt.ai.util.Constants;
import com.xceptance.xlt.ai.util.Helper;
import com.xceptance.xlt.api.engine.Session;
import com.xceptance.xlt.api.engine.scripting.WebDriverCustomModule;
import com.xceptance.xlt.api.util.XltProperties;

/**
 * Module for the visual assertion of changes in a browser page. The module is called in an
 * action and takes a screenshot of the current page. This screenshot is then compared to already taken
 * reference images of the same page, or stored as reference image.
 *
 * The configurations for this module are done in the visualassertion.properties under /config
 * There are different algorithms that can be used for the comparison of the images and different ways to
 * visualize those differences for the evaluation.
 */
public class AI implements WebDriverCustomModule
{
    /**
     * Counter for the current screenshots
     */
    private static ThreadLocal<Integer> indexCounter = new ThreadLocal<>();

    private final String ALL = "all";
	private final String PREFIX = "com.xceptance.xlt.ai.";
    private final String RESULT_DIRECTORY = "results" + File.separator + "ai";

    // subdirectories
    private final String RESULT_DIRECTORY_TRAINING 		= "training";
    private final String RESULT_DIRECTORY_NETWORKS 		= "networks";
    private final String RESULT_DIRECTORY_UNRECOGNIZED 	= "unrecognized";
    private final String RESULT_DIRECTORY_RECOGNIZED 	= "recognized";

    // the property names
    public final String PROPERTY_ENABLED = PREFIX + "enabled";
    public final String PROPERTY_RESULT_DIRECTORY = PREFIX + "resultDirectory";
    public final String PROPERTY_ID = PREFIX + "ID";
    public final String PROPERTY_WAITING_TIME = PREFIX + "waitingTime";
    public final String PROPERTY_USE_ORIGINAL_SIZE = PREFIX + "USE_ORIGINAL_SIZE";
    public final String PROPERTY_USE_COLOR_FOR_COMPARISON = PREFIX + "USE_COLOR_FOR_COMPARISON";
    public final String PROPERTY_LEARNING_RATE = PREFIX + "LEARNING_RATE"; 
    public final String PROPERTY_INTENDED_PERCENTAGE_MATCH = PREFIX + "INTENDED_PERCENTAGE_MATCH";
    public final String PROPERTY_PERCENTAGE_DIFFERENCE = PREFIX + "PERCENTAGE_DIFFERENCE";
    public final String PROPERTY_IMAGE_HEIGHT = PREFIX + "IMAGE_HEIGHT";
    public final String PROPERTY_IMAGE_WIDTH = PREFIX + "IMAGE_WIDTH";
    public final String PROPERTY_FORMAT = PREFIX + "FORMAT";

    @Override
    public void execute(final WebDriver webdriver, final String... arguments)
    {
        final XltProperties props = XltProperties.getInstance();

        // check if we have to do anything?
        final boolean enabled = props.getProperty(PROPERTY_ENABLED, true);
        if (!enabled)
        {
            // skipped silently
            return;
        }
        //--------------------------------------------------------------------------------
        // Get Properties and convert them from String if necessary
        //--------------------------------------------------------------------------------

        // Identification of the current environment for this test
        final String id = props.getProperty(PROPERTY_ID, ALL);
        // Parent directory of the visual assertion results
        final String resultDirectory = props.getProperty(PROPERTY_RESULT_DIRECTORY, RESULT_DIRECTORY);

        // Wait time for the page to load completely
        final int waitTime = props.getProperty(PROPERTY_WAITING_TIME, Constants.WAITINGTIME);
        final int percentageDifferenceValue = props.getProperty(PROPERTY_PERCENTAGE_DIFFERENCE, Constants.PERCENTAGE_DIFFERENCE);
        
        Constants.IMAGE_HEIGHT 				= props.getProperty(PROPERTY_IMAGE_HEIGHT, Constants.IMAGE_HEIGHT);
        Constants.IMAGE_WIDTH 				= props.getProperty(PROPERTY_IMAGE_WIDTH, Constants.IMAGE_WIDTH);
        Constants.FORMAT 					= props.getProperty(PROPERTY_FORMAT, Constants.FORMAT);        
        Constants.USE_ORIGINAL_SIZE 		= props.getProperty(PROPERTY_USE_ORIGINAL_SIZE, Constants.USE_COLOR_FOR_COMPARISON);
        Constants.USE_COLOR_FOR_COMPARISON 	= props.getProperty(PROPERTY_USE_COLOR_FOR_COMPARISON, Constants.USE_COLOR_FOR_COMPARISON);
        
        final String learningRateValue = props.getProperty(PROPERTY_LEARNING_RATE, Constants.LEARNING_RATE);
        final double learningRate = Double.parseDouble(learningRateValue);
        
        final String indentedPercentageMatchValue = props.getProperty(PROPERTY_INTENDED_PERCENTAGE_MATCH, Constants.INTENDED_PERCENTAGE_MATCH);
        final double indentedPercentageMatch = Double.parseDouble(indentedPercentageMatchValue);

        //--------------------------------------------------------------------------------
        // Get the current environment
        //--------------------------------------------------------------------------------

        // Get the name of the test case for the correct folder identifier
        final String currentTestCaseName = Session.getCurrent().getUserName();

        // Get browser name and browser version for the subfolders
        final String browserName = getBrowserName(webdriver);

        // Get the name of the action that called the visual assertion
        final String currentActionName = Session.getCurrent().getCurrentActionName();

        //--------------------------------------------------------------------------------
        // Initialize the directory and file paths, create the directories if necessary
        //--------------------------------------------------------------------------------

        // Generate the child directories for the current environment in the parent result folder
        final File targetDirectory = new File(new File(new File(resultDirectory, id),
                currentTestCaseName),
                browserName);
                
        targetDirectory.mkdirs();

        // Retrieve current index counter for the image file names
        Integer index = indexCounter.get();
        if (index == null)
        {
            index = 1;
        }
        else
        {
            index = index + 1;
        }
        // Update the index
        indexCounter.set(index);

        // Name of the image file for the screenshot
        final String screenshotName = String.format("%03d", index) + "-" + currentActionName;

        // Directory for the training images
        final File trainingDirectory = new File(new File(targetDirectory, RESULT_DIRECTORY_TRAINING), screenshotName);
        
        // Path of the screenshot image file
        final String exactScreenshotName = screenshotName + Session.getCurrent().getID() + "." + Constants.FORMAT;
        final File trainingScreenShotFile = new File(trainingDirectory, exactScreenshotName);
        
        // Directory of the network file
        final File networkDirectoryPath = new File(targetDirectory, RESULT_DIRECTORY_NETWORKS);
        networkDirectoryPath.mkdirs();
        // Path of the network file
        final File networkFile = new File(networkDirectoryPath, screenshotName);

        //--------------------------------------------------------------------------------
        // Wait for the page to fully load, so that a correct screenshot can be taken
        //--------------------------------------------------------------------------------

        try
        {
            TimeUnit.MILLISECONDS.sleep(waitTime);
        }
        catch (final InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }

        //--------------------------------------------------------------------------------
        // Make the screenshot and load the network
        //--------------------------------------------------------------------------------
        
        // initialization 
        final FastBitmap screenshot;
        ActivationNetwork an = new ActivationNetwork(new BipolarSigmoidFunction(), 1); 
        ImageTransformation im;
        ArrayList<PatternHelper> patternList = new ArrayList<>();

        if (networkFile.exists())
        {
        	// load the corresponding network and all settings which are saved 
          	an = (ActivationNetwork) an.Load(networkFile.getPath()); 
          	an.setConstants();
           	ArrayList<FastBitmap> imgList = new ArrayList<>();   
            screenshot = new FastBitmap(takeScreenshot(webdriver), exactScreenshotName, Constants.USE_ORIGINAL_SIZE);
            
            if (screenshot == null)
            {
            	// TODO Has this to be handled in a different way?
                // webdriver cannot take the screenshot -> RETURN
                return;
            }
           	
           	imgList.add(screenshot);
           	if (an.getModusFlag())
           	{
           		trainingDirectory.mkdirs();
           		imgList.addAll(an.scanFolderForChanges(
           				trainingScreenShotFile.getParent(), 
           				exactScreenshotName));
           	}
            // transform the new screenshot
            im = new ImageTransformation(
               		imgList,                		
               		an.getAverageMetric(), 
               		an.getModusFlag());                
            imgList = null;
        }
        else
        {  
        	trainingDirectory.mkdirs();
       	
          	an.scanFolderForChanges(
          			trainingScreenShotFile.getParent(), 
           			exactScreenshotName);
          	
          	 screenshot = new FastBitmap(takeScreenshot(webdriver), exactScreenshotName, Constants.USE_ORIGINAL_SIZE);

            if (screenshot == null)
            {
            	// TODO Has this to be handled in a different way?
                // webdriver cannot take the screenshot -> RETURN
                return;
            } 
          	
          	// load all images from the directory
            im = new ImageTransformation(
               		screenshot,
               		trainingScreenShotFile.getParent());
        }
        patternList = im.computeAverageMetric(percentageDifferenceValue);
        // internal list in network for self testing and image confirmation        
        an.setInternalList(patternList);            
    	PerceptronLearning pl = new PerceptronLearning(an, learningRate);
    	pl.setLearningRate(learningRate);	
    	
    	if (an.getModusFlag())
    	{
			for (PatternHelper pattern : patternList)
			{
				pl.Run(pattern.getPatternList());
			}
    	}
    	// test the corresponding network the new and all therefore seen pattern
    	// if the self test is correct there is no further training necessary
		boolean selfTest = an.onSelfTest(indentedPercentageMatch);
			
		double result = 2.0;
		
		if (!selfTest)
		{	
			// ensure to get the last element in the list, which is always the current screenshot
			result = an.checkForRecognitionAsDouble(patternList.get(patternList.size() - 1).getPatternList());
		}
			
		if (!selfTest)
		{
			System.out.println("Network result: " + result);
			
		}
		else
		{
			System.out.println("Network not ready");
			System.out.println("result: " + result);
		}
		// Save the network
		an.Save(networkFile.toString(), im.getAverageMetric());
			
		// Save the screenshot
		if (indentedPercentageMatch > result && !selfTest)
		{
			// Directory for the unrecognized images of the current test run
			final File unrecognizedInstanceDirectory = new File(new File(targetDirectory, RESULT_DIRECTORY_UNRECOGNIZED), screenshotName);
			// Path of the unrecognized image file
		    final File unrecognizedImageFile = new File(unrecognizedInstanceDirectory, exactScreenshotName);
		    unrecognizedImageFile.mkdirs();
			Helper.saveImage(screenshot.toBufferedImage(), unrecognizedImageFile);
		}
		else if (indentedPercentageMatch < result && selfTest)
		{
			Helper.saveImage(screenshot.toBufferedImage(), trainingScreenShotFile);				
		}
		else
		{
			final File recognizedInstanceDirectory = new File(new File(targetDirectory, RESULT_DIRECTORY_RECOGNIZED), screenshotName);
			final File recognizedImageFile = new File(recognizedInstanceDirectory, exactScreenshotName);
			recognizedInstanceDirectory.mkdirs();
			Helper.saveImage(screenshot.toBufferedImage(), recognizedImageFile);
		}
}

    /**
     * Takes a screenshot if the underlying web driver instance is capable of doing it. Fails with a message only in
     * case the webdriver cannot take screenshots. Avoids issue when certain drivers are used.
     * 
     * @param webDriver
     *            the web driver to use
     * @return {@link BufferedImage} if the webdriver supports taking screenshots, null otherwise
     * @throws RuntimeException
     *             In case the files cannot be written
     */
    private BufferedImage takeScreenshot(final WebDriver webDriver)
    {
        if (webDriver instanceof TakesScreenshot)
        {
            final byte[] bytes = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
            try
            {
                return ImageIO.read(new ByteArrayInputStream(bytes));
            }
            catch (final IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        else
        {
            return null;
        }
    }

    /**
     * Returns the browser name using Selenium methods
     * 
     * @param webDriver
     *            the WebDriver to query
     * @return the browser name
     */
    private String getBrowserName(final WebDriver webDriver)
    {
        final Capabilities capabilities = ((RemoteWebDriver) webDriver).getCapabilities();
        final String browserName = capabilities.getBrowserName();

        return browserName == null ? "unknown" : browserName;
    }
}
