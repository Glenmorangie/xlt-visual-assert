package com.xceptance.xlt.ai.pre_processing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.xceptance.xlt.ai.core.FloatPoint;
import com.xceptance.xlt.ai.corner.Fast9;
import com.xceptance.xlt.ai.corner.FastCornersDetector;
import com.xceptance.xlt.ai.core.FeaturePoint;
import com.xceptance.xlt.ai.corner.FastCornersDetector.Algorithm;
import com.xceptance.xlt.ai.image.AverageMetric;
import com.xceptance.xlt.ai.image.Convolution;
import com.xceptance.xlt.ai.image.FastBitmap;
import com.xceptance.xlt.ai.image.MetricCurator;
import com.xceptance.xlt.ai.image.PatternHelper;
import com.xceptance.xlt.ai.util.Constants;
import com.xceptance.xlt.ai.util.Helper;

/***
 * Main entry point to load, analyze and compare images. 
 * Load Images out of an folder and hold them for analyze.
 * Analyze to find the ROI's (region of interest) in the image. They are saved in groups and each point is a {@link FeaturePoint}.
 * Make use of machine learning in the {@link #computeAverageMetric} method. 
 * @author Thomas Volkmann 
 */
public class ImageTransformation 
{
	/***
	 * Constructor if there is a network which can be used for further learning or comparison. 
	 * If the trainingFlag is on false there is just a comparison to the already learned average metric.
	 * @param imgList ArrayList of {@link FastBitmap} with all found images.
	 * @param averageMet Loaded {@link AverageMetric} from the network.
	 * @param trainingFlag boolean value if the network need further training or not.
	 */
	public ImageTransformation(ArrayList<FastBitmap> imgList, Map<Integer, AverageMetric> averageMet, boolean trainingFlag)
	{		
		maxSize 			= 0;
		averMet 			= averageMet;
		this.trainingFlag 	= trainingFlag;
		recognizeFlag 		= false;
		pp 					= new ArrayList<>();
		pictureList 		= imgList;
		applyTransformation(); 
	}
	
	/***
	 * Constructor for first initialization, in this case the program is run for the first time.
	 * Load the images out of a folder and start the analyze with {@link #applyTransformation()}
	 * @param imgList ArrayList {@link FastBitmap} current and eventually other FastBitmaps. 
	 */
	public ImageTransformation(ArrayList<FastBitmap> imgList)
	{
		averMet 				= new HashMap<Integer, AverageMetric>();
		maxSize 				= 0;
		trainingFlag			= true;
		recognizeFlag 			= false;
		pp 						= new ArrayList<>();
		pictureList				= imgList;
		Constants.NETWORK_MODE 	= true;
		applyTransformation();
	}
	
	/***
	 * Constructor for NetworkTrainer tool.
	 * Load the images out of a folder and start the analyze with {@link #applyTransformation()}
	 * @param path String to the folder.
	 */
	public ImageTransformation(String path)
	{
		averMet 			= new HashMap<Integer, AverageMetric>();
		maxSize 			= 0;
		trainingFlag		= true;
		recognizeFlag 		= false;
		pp = new ArrayList<>();
		load(path);
		applyTransformation();
	}
	
	/***
	 * ArrayList of all preprocessed images.
	 * Get the current MetricCurator for an image. 
	 * @return pp ArrayList of MetricCurator for every image.
	 */
	public ArrayList<PreProcessing> getCurator()
	{
		return pp;
	}
	
	/***
	 * Dictionary which hold the average metric from all seen images.
	 * @return averMet Dictionary of the found average metric
	 */
	public Map<Integer, AverageMetric> getAverageMetric()
	{
		return averMet;
	}
	
	/***
	 * Compute the pattern for the network in relevance to the average metric.
	 * Provide the data for the neural network and transform the image in a way it is linear seperable.
	 * This step is important so the perceptron network can learn the patterns and distinguish the different images.
	 * @return foundPattern ArrayList of {@link PatternHelper} of all found pattern if there was more than one image in the folder.
	 */
	public ArrayList<PatternHelper> computeAverageMetric()
	{
		int groupSize 								= 0;
		double boundingBoxSize						= 0.0;
		double distanceMin							= 0.0;
		double distanceMax							= 0.0;
		double histoRedMean							= 1.0;
		double histoGreenMean						= 1.0;
		double histoBlueMean						= 1.0; 
		FloatPoint centerOfGravity 					= new FloatPoint(0,0);		
		boolean isEmptyFlag 						= true;
		ArrayList<PatternHelper> foundPattern 		= new ArrayList<>();
		ArrayList<Integer> keyList					= new ArrayList<>();
		PatternHelper pattern;
		
		// if no network was loaded
		if (!averMet.isEmpty())
		{
			isEmptyFlag = false;
		}
		
		// main loop for all images which are stored in PreProcessing
		for (int index = 0; index < pp.size(); ++index)			
		{
			MetricCurator mc = pp.get(index).getMetricCurator();
			maxSize = mc.metricList.size();
			pattern = new PatternHelper(mc.getTagName());
			// empty initialization to ensure the capacity for the pattern 
			for (int inde = 0; inde < averMet.keySet().size(); ++inde)
			{
				pattern.addElementToPattern(0);
				keyList.add(inde);
			}			
			// loop to create the pattern
			// also compare the pattern in respective to found or not found
			for (int ind = 0; ind < maxSize; ++ind)
			{
				int key 			= 0;
				groupSize 		 	= pp.get(index).getMetricCurator().metricList.get(ind).getGroupSize();
				boundingBoxSize 	= pp.get(index).getMetricCurator().metricList.get(ind).getBoundingBoxDistance();
				distanceMin 		= pp.get(index).getMetricCurator().metricList.get(ind).getMinDistanceToZero();
				distanceMax 		= pp.get(index).getMetricCurator().metricList.get(ind).getMaxDistanceToZero();		
				centerOfGravity.Add(pp.get(index).getMetricCurator().metricList.get(ind).getCenterOfGravity());
				
				if (Constants.USE_COLOR_FOR_COMPARISON)
				{
					histoRedMean		= pp.get(index).getMetricCurator().metricList.get(ind).getImageStatistic().getHistogramRed().getMean();
					histoGreenMean		= pp.get(index).getMetricCurator().metricList.get(ind).getImageStatistic().getHistogramGreen().getMean();
					histoBlueMean		= pp.get(index).getMetricCurator().metricList.get(ind).getImageStatistic().getHistogramBlue().getMean();
				}
	
				Iterator<Integer> iter = averMet.keySet().iterator();
				
				// first run take all inputs as new average metric and set the pattern to 1s for learning and weighting
				if (isEmptyFlag)
				{
					averMet.put(ind, new AverageMetric(groupSize, boundingBoxSize, distanceMin, distanceMax, centerOfGravity, histoRedMean, histoGreenMean, histoBlueMean));
					recognizeFlag = true;
				}
				// if training is activated and the average metric is not empty analyze the new metric in relevance to the average metric					
				else if (trainingFlag && !isEmptyFlag)
				{						
					while (iter.hasNext())
					{
						// check for the independent metrics (in respect of screen coordinates) which are groupSize and BoundingBox 
						// and if the new metric match the average metric (with a percentage difference) the average metric get an update
						// and mark them as recognize in the pattern indicated as 1
						key = iter.next();
						if (Helper.isInRange(averMet.get(key).getAverageGroupSize(), groupSize) && 
							Helper.isInRange(averMet.get(key).getAverageBoundingBoxSize(),boundingBoxSize) 
							&&
							Helper.isInRange(averMet.get(key).getAverageHistogramRedMean(), histoRedMean) &&
							Helper.isInRange(averMet.get(key).getAverageHistogramGreenMean(), histoGreenMean) &&
							Helper.isInRange(averMet.get(key).getAverageHistogramBlueMean(), histoBlueMean))
						{	
							if (keyList.contains(key))
							{
								averMet.get(key).update(groupSize, boundingBoxSize, distanceMin, distanceMax, centerOfGravity, histoRedMean, histoGreenMean, histoBlueMean);
								recognizeFlag = true;
								keyList.remove(keyList.indexOf(key));
								break;
							}							
						}
					}												
					// expand the pattern and update average metric for further comparing, set the pattern to 0 for not recognized 
					if (!recognizeFlag)
					{
						averMet.put(key + 1, new AverageMetric(groupSize, boundingBoxSize, distanceMin, distanceMax, centerOfGravity, histoRedMean, histoGreenMean, histoBlueMean));
						recognizeFlag = false;
						keyList.add(key + 1);
					}		
				}
				// alternative path if the network has already learned and now just compare the new metrics to the found average metric
				else 
				{
					while (iter.hasNext())
					{
						key = iter.next();
						if (Helper.isInRange(averMet.get(key).getAverageGroupSize(), groupSize) && 
							Helper.isInRange(averMet.get(key).getAverageBoundingBoxSize(), boundingBoxSize) 
							&& 
							Helper.isInRange(averMet.get(key).getAverageHistogramRedMean(), histoRedMean)&&
							Helper.isInRange(averMet.get(key).getAverageHistogramGreenMean(), histoGreenMean) &&
							Helper.isInRange(averMet.get(key).getAverageHistogramBlueMean(), histoBlueMean))
						{
							if (keyList.contains(key))
							{
								recognizeFlag = true;
								keyList.remove(keyList.indexOf(key));
								break;
							}
						}						
					}
				}
				// set the pattern in respect to the recognizeFlag
				if (pattern.getSize() < averMet.keySet().size())
				{
					pattern.addElementToPattern(recognizeFlag ? 1 : 0);
				}
				else if (pattern.getSize() < ind && !trainingFlag)
				{
					pattern.addElementToPattern(recognizeFlag ? 1 : 0);
				}
				else
				{
					pattern.setElement(key, recognizeFlag ? 1 : 0);					
				}
				recognizeFlag = false;								
			} 
			groupSize 			= 0;
			boundingBoxSize 	= 0.0;
			distanceMin 		= 0.0;
			distanceMax 		= 0.0;
			centerOfGravity.x 	= 0;
			centerOfGravity.y 	= 0;
			histoRedMean		= 1.0;
			histoGreenMean		= 1.0;
			histoBlueMean		= 1.0; 
			recognizeFlag 		= false;
			isEmptyFlag 		= false;
			foundPattern.add(pattern);			
		}
		return foundPattern;
	}
	
	public ArrayList<PatternHelper> updateInternalPattern(Map<Integer, AverageMetric> averMetr, ArrayList<PreProcessing> ppList)
	{
		int groupSize 								= 0;
		double boundingBoxSize						= 0.0;
		double distanceMin							= 0.0;
		double distanceMax							= 0.0;
		double histoRedMean							= 1.0;
		double histoGreenMean						= 1.0;
		double histoBlueMean						= 1.0; 
		FloatPoint centerOfGravity 					= new FloatPoint(0,0);		
		ArrayList<PatternHelper> foundPattern 		= new ArrayList<>();
		ArrayList<Integer> keyList					= new ArrayList<>();
		PatternHelper pattern;
		
		// main loop for all images which are stored in PreProcessing
		for (int index = 0; index < ppList.size(); ++index)			
		{
			MetricCurator mc = ppList.get(index).getMetricCurator();
			maxSize = mc.metricList.size();
			pattern = new PatternHelper(mc.getTagName());
			// empty initialization to ensure the capacity for the pattern 
			for (int inde = 0; inde < averMetr.keySet().size(); ++inde)
			{
				pattern.addElementToPattern(0);
				keyList.add(inde);
			}			
			// loop to create the pattern
			// also compare the pattern in respective to found or not found
			for (int ind = 0; ind < maxSize; ++ind)
			{
				int key 			= 0;
				groupSize 		 	= ppList.get(index).getMetricCurator().metricList.get(ind).getGroupSize();
				boundingBoxSize 	= ppList.get(index).getMetricCurator().metricList.get(ind).getBoundingBoxDistance();
				distanceMin 		= ppList.get(index).getMetricCurator().metricList.get(ind).getMinDistanceToZero();
				distanceMax 		= ppList.get(index).getMetricCurator().metricList.get(ind).getMaxDistanceToZero();		
				centerOfGravity.Add(ppList.get(index).getMetricCurator().metricList.get(ind).getCenterOfGravity());
				
				if (Constants.USE_COLOR_FOR_COMPARISON)
				{
					histoRedMean		= ppList.get(index).getMetricCurator().metricList.get(ind).getImageStatistic().getHistogramRed().getMean();
					histoGreenMean		= ppList.get(index).getMetricCurator().metricList.get(ind).getImageStatistic().getHistogramGreen().getMean();
					histoBlueMean		= ppList.get(index).getMetricCurator().metricList.get(ind).getImageStatistic().getHistogramBlue().getMean();
				}
	
				Iterator<Integer> iter = averMetr.keySet().iterator();
				// alternative path if the network has already learned and now just compare the new metrics to the found average metric
				while (iter.hasNext())
				{
					key = iter.next();
					if (Helper.isInRange(averMetr.get(key).getAverageGroupSize(), groupSize) && 
						Helper.isInRange(averMetr.get(key).getAverageBoundingBoxSize(), boundingBoxSize) 
						&& 
						Helper.isInRange(averMetr.get(key).getAverageHistogramRedMean(), histoRedMean)&&
						Helper.isInRange(averMetr.get(key).getAverageHistogramGreenMean(), histoGreenMean) &&
						Helper.isInRange(averMetr.get(key).getAverageHistogramBlueMean(), histoBlueMean))
					{
						if (keyList.contains(key))
						{
							recognizeFlag = true;
							keyList.remove(keyList.indexOf(key));
							break;
						}
					}						
				}
				// set the pattern in respect to the recognizeFlag
				if (pattern.getSize() < ind)
				{
					pattern.addElementToPattern(recognizeFlag ? 1 : 0);
				}
				else
				{
					pattern.setElement(key, recognizeFlag ? 1 : 0);
				}
				recognizeFlag = false;								
			} 
			groupSize 			= 0;
			boundingBoxSize 	= 0.0;
			distanceMin 		= 0.0;
			distanceMax 		= 0.0;
			centerOfGravity.x 	= 0;
			centerOfGravity.y 	= 0;
			histoRedMean		= 1.0;
			histoGreenMean		= 1.0;
			histoBlueMean		= 1.0; 
			recognizeFlag 		= false;
			foundPattern.add(pattern);			
		}
		return foundPattern;
	}
	
	/***
	 * Transformation of the image for further use.
	 * First a {@link Convolution} with a Laplace and Gaussian kernel is performed.
	 * Furthermore the edge detection via {@link FastCornersDetector} with {@link Fast9} is performed.
	 * Next the found corners get sorted after x values and the last step is realized with {@link PreProcessing}.	 
	 */
	private void applyTransformation()
	{
		//Convolution conv = new Convolution(ConvolutionKernel.LaplacianOfGaussian);
		FastCornersDetector fcd = new FastCornersDetector(Algorithm.FAST_9);		
		fcd.setSuppression(false);
		List<FeaturePoint> tempList = null;
		int index = 1;
		for (FastBitmap element : pictureList)
		{
			long startTime = System.nanoTime();
			// convolution take too much time and is therefore disabled, improve the performance up to 3 times
			// but also decrease accuracy 
			// conv.applyInPlace(element);			
			// apply the fast corner detection to the image
			tempList = fcd.ProcessImage(element);
			// sorting with the comparator set in FeaturePoints, sorting order is ascending x and y values
			Collections.sort(tempList, new FeaturePoint());			
			pp.add(new PreProcessing(tempList, element));			
			long estimatedTime = System.nanoTime() - startTime;
			Helper.updatePercentageBar((double)index / (double)pictureList.size(), estimatedTime, pictureList.size(), index);
			index++;
		}	
		System.out.println("");
	}
	
	/***
	 * Load all images out of a given folder path.
	 * @param path String full path name to folder.
	 */
	private void load(String path)
	{
		pictureList = Helper.loadAllImagesScaled_FastBitmap(path, Constants.IMAGE_HEIGHT, Constants.IMAGE_WIDTH);
		Constants.NETWORK_MODE 	= true;
		
		if (!pictureList.isEmpty())
		{			
			Helper.setImageParameter();							
		}
		else
		{			
			ArrayList<FastBitmap> temp 	= new ArrayList<>();
			process(temp);
		}
	}
	
	/**
	 * New initialization, create and load everything into the list for the images, for further use.
	 * @param imgList list of all found images in the corresponding folder.
	 */
	private void process(ArrayList<FastBitmap> imgList)
	{
		pictureList = new ArrayList<>();
		pictureList.addAll(imgList);	
	}
	
	/***
	 * List of all loaded images, saved as FastBitmap.
	 */
	private ArrayList<FastBitmap> pictureList;
	
	/***
	 * Maximum size of the pattern.
	 */
	private int maxSize;
	
	/***
	 * Boolean flag for recognition.
	 */
	private boolean recognizeFlag;
	
	/***
	 * Learning modus enabled or disabled. If disabled it will compare to already learned pattern.
	 */
	private boolean trainingFlag;
	
	/***
	 * List of rehashed images.
	 */
	private ArrayList<PreProcessing> pp;
	
	/**
	 * Representation of average metric.
	 */
	private Map<Integer, AverageMetric> averMet;
}
