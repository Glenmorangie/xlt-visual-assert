package com.xceptance.xlt.ai.pre_processing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import com.xceptance.xlt.ai.core.FeaturePoint;
import com.xceptance.xlt.ai.corner.FastCornersDetector;
import com.xceptance.xlt.ai.image.FastBitmap;
import com.xceptance.xlt.ai.image.Metric;
import com.xceptance.xlt.ai.image.MetricCurator;
import com.xceptance.xlt.ai.util.Constants;
import com.xceptance.xlt.ai.util.Helper;

/***
 * Second part of the image transformation, the first part is {@link FastCornersDetector},
 * the found edges get grouped with all points which are in the radius of the given {@link Constants#THRESHOLD}
 * Also the artifacts (in this case groups under a minimum value {@link Constants#MINGROUPSIZE}) are ignored and discarded.
 * @author Thomas Volkmann
 */
public class PreProcessing 
{
	/***
	 * Constructor which will automatically start the grouping of the edges {@link #findGroups(List)}.
	 * @param pictureTag String name of the image
	 * @param edges List of {@link FeaturePoint}
	 */
	public PreProcessing(List<FeaturePoint> edges, FastBitmap currentImage)
	{	
		met = new MetricCurator(currentImage.getTagName());
		findGroups(edges, currentImage);
	}
	
	/***
	 * Get the curator for the metric {@link MetricCurator}.
	 * @return met MetricCurator
	 */
	public MetricCurator getMetricCurator()
	{
		return met;
	}
	
	// Detector aka other Algorithm like FREAK ...
	// 
	// artifacts under a specific threshold will ignored
	// Parameter for regulation are THRESHOLD and MINGROUPSIZE
	/***
	 * Segmentation and grouping of all edges, artifacts under a specific threshold will ignored.
	 * Parameter are {@link Constants#THRESHOLD}, {@link Constants#MINGROUPSIZE}. 
	 * @param edges List of {@link FeaturePoint}
	 */
	private void findGroups(List<FeaturePoint> edges, FastBitmap currentImage)
	{		
		ArrayList<HashSet<FeaturePoint>> allGroups = new ArrayList<>();
		// MainLoop for grouping
		while (!edges.isEmpty())
		{
			int index = 0;		
			int internIndex = 0;
			// current list for the group and all its elements
			HashSet<FeaturePoint> todoList = new HashSet<FeaturePoint>();
			// initiation point
			todoList.add(edges.get(0));
			Iterator<FeaturePoint> iter = todoList.iterator();
			FeaturePoint currentPoint = iter.next();
			// check if the current list is in border of the edges list	
			while (internIndex < todoList.size())
			{
				// mark element as visited
				currentPoint.visited = true;
				// check if there are more points around the current point
				while (currentPoint.x + Constants.THRESHOLD >= edges.get(index).x)
				{
					// range check around the current point
					if (Helper.inBetween(currentPoint.y, edges.get(index).y, Constants.THRESHOLD))
					{						
						todoList.add(edges.get(index));
					}
					++index;
					// safety check for overflow error
					if (edges.size() == index)
					{
						break;
					}
				}					
				index = 0;		
				++internIndex;				
				iter = todoList.iterator();
				
				// double check if there are more points in the list and all of them visited
				if (iter.hasNext())
				{
					currentPoint = iter.next();	
					while(currentPoint.visited && iter.hasNext())
					{
						currentPoint = iter.next();
					}
				}
				// when all points are visited so must the last one
				if (currentPoint.visited)
				{
					break;
				}
			}
			// filter if the HashSet is too small, in case it is to small it will be ignored and discarded				
			if (!todoList.isEmpty() && todoList.size() >= Constants.MINGROUPSIZE)
			{
				allGroups.add(todoList);
			}
			edges.removeAll(todoList);								
		}		
			
		// change the HashSet to an ArrayList for further working
		ArrayList<ArrayList<FeaturePoint>> returnList = new ArrayList<ArrayList<FeaturePoint>>();
		// add all found group lists to an ArrayList		
		for (int index = 0; index < allGroups.size(); ++index)
		{	
			returnList.add(new ArrayList<>(allGroups.get(index)));
			// sort the elements in ascending order for score(intensity) values
			Collections.sort(returnList.get(index));
			// create the metrics for the found groups and save them in the curator
			met.metricList.add(new Metric(returnList.get(index), currentImage ));
			// sort the Metrics after group size
			Collections.sort(met.metricList);
		}		 
		allGroups = null;	
	}
	
	/***
	 * Curator for the image metrics.
	 */
	private MetricCurator met;
}