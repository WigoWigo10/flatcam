/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.flatcam.cam.ncc.geosbuffer;

import org.locationtech.jts.algorithm.Distance;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateList;

/**
 * Forked from JTS 1.20.0's {@code BufferInputLineSimplifier} (EPL-2.0/EDL-1.0,
 * same license as the {@code jts-core} dependency this project already uses)
 * to match GEOS 3.10.3's input simplification instead - see this package's
 * {@link GeosOffsetSegmentGenerator} and {@code INVESTIGACAO_CAM.md} for why.
 * Two behavioural changes from upstream JTS, both in GEOS's favour:
 * <ul>
 * <li>{@link #deleteShallowConcavities}: GEOS never special-cases rings - the
 * sliding window always starts at index 1, even though a ring's index 0 is
 * just an arbitrary rotation point, not a true "end" the way a line's is.
 * JTS starts at index 0 for rings, simplifying one more vertex pair.</li>
 * <li>{@link #isShallowSampled}: GEOS and JTS pass the same two points into
 * this sampled check (the window's start and its middle/apex vertex), but
 * swap which one plays which role inside the per-sample distance test -
 * GEOS measures each sample's distance from the apex, JTS measures the
 * apex's distance from each sample's own chord. Different concavities end
 * up surviving simplification as a result.</li>
 * </ul>
 *
 * <p>Simplifies a buffer input line to
 * remove concavities with shallow depth.
 * <p>
 * The major benefit of doing this
 * is to reduce the number of points and the complexity of
 * shape which will be buffered.
 * This improve performance and robustness.
 * It also reduces the risk of gores created by
 * the quantized fillet arcs (although this issue
 * should be eliminated by the 
 * offset curve generation logic).
 * <p>
 * A key aspect of the simplification is that it
 * affects inside (concave or inward) corners only.  
 * Convex (outward) corners are preserved, since they
 * are required to ensure that the generated buffer curve
 * lies at the correct distance from the input geometry.
 * <p>
 * Another important heuristic used is that the end segments
 * of linear inputs are never simplified.  This ensures that
 * the client buffer code is able to generate end caps faithfully.
 * Ring inputs can have end segments removed by simplification.
 * <p>
 * No attempt is made to avoid self-intersections in the output.
 * This is acceptable for use for generating a buffer offset curve,
 * since the buffer algorithm is insensitive to invalid polygonal
 * geometry.  However, 
 * this means that this algorithm
 * cannot be used as a general-purpose polygon simplification technique.
 * 
 * @author Martin Davis
 *
 */
public class GeosBufferInputLineSimplifier 
{
  /**
   * Simplify the input coordinate list.
   * If the distance tolerance is positive, 
   * concavities on the LEFT side of the line are simplified.
   * If the supplied distance tolerance is negative,
   * concavities on the RIGHT side of the line are simplified.
   * 
   * @param inputLine the coordinate list to simplify
   * @param distanceTol simplification distance tolerance to use
   * @return the simplified coordinate list
   */
  public static Coordinate[] simplify(Coordinate[] inputLine, double distanceTol)
  {
    GeosBufferInputLineSimplifier simp = new GeosBufferInputLineSimplifier(inputLine);
    return simp.simplify(distanceTol);
  }
  
  private static final int DELETE = 1;
  
  private Coordinate[] inputLine;
  private double distanceTol;
  private boolean[] isDeleted;
  private int angleOrientation = Orientation.COUNTERCLOCKWISE;


  public GeosBufferInputLineSimplifier(Coordinate[] inputLine) {
    this.inputLine = inputLine;
  }

  /**
   * Simplify the input coordinate list.
   * If the distance tolerance is positive, 
   * concavities on the LEFT side of the line are simplified.
   * If the supplied distance tolerance is negative,
   * concavities on the RIGHT side of the line are simplified.
   * 
   * @param distanceTol simplification distance tolerance to use
   * @return the simplified coordinate list
   */
  public Coordinate[] simplify(double distanceTol)
  {
    this.distanceTol = Math.abs(distanceTol);
    angleOrientation = Orientation.COUNTERCLOCKWISE;
    if (distanceTol < 0)
      angleOrientation = Orientation.CLOCKWISE;
    
    // rely on fact that boolean array is filled with false values
    isDeleted = new boolean[inputLine.length];
    
    boolean isChanged = false;
    do {
      isChanged = deleteShallowConcavities();
    } while (isChanged);
    
    return collapseLine();
  }
  
  /**
   * Uses a sliding window containing 3 vertices to detect shallow angles
   * in which the middle vertex can be deleted, since it does not
   * affect the shape of the resulting buffer in a significant way.
   * 
   * @return true if any vertices were deleted
   */
  private boolean deleteShallowConcavities()
  {
    /**
     * Do not simplify end line segments of lines.
     * This ensures that end caps are generated consistently.
     * GEOS never special-cases rings here - always start at 1 (see class doc).
     */
    int index = 1;

    int midIndex = nextIndex(index);
    int lastIndex = nextIndex(midIndex);
    
    boolean isChanged = false;
    while (lastIndex < inputLine.length) {
      // test triple for shallow concavity
    	boolean isMiddleVertexDeleted = false;
      if (isDeletable(index, midIndex, lastIndex, 
          distanceTol)) {
        isDeleted[midIndex] = true;
        isMiddleVertexDeleted = true;
        isChanged = true;
      }
      // move simplification window forward
      if (isMiddleVertexDeleted)
      	index = lastIndex;
      else 
      	index = midIndex;
      
      midIndex = nextIndex(index);
      lastIndex = nextIndex(midIndex);
    }
    return isChanged;
  }
  
  /**
   * Finds the next non-deleted index, or the end of the point array if none
   * @param index
   * @return the next non-deleted index, if any
   * or inputLine.length if there are no more non-deleted indices
   */
  private int nextIndex(int index)
  {
    int next = index + 1;
    while (next < inputLine.length && isDeleted[next])
      next++;
    return next;  
  }
  
  private Coordinate[] collapseLine()
  {
    CoordinateList coordList = new CoordinateList();
    for (int i = 0; i < inputLine.length; i++) {
      if (! isDeleted[i])
        coordList.add(inputLine[i]);
    }
    return coordList.toCoordinateArray();
  }
  
  private boolean isDeletable(int i0, int i1, int i2, double distanceTol)
  {
  	Coordinate p0 = inputLine[i0];
  	Coordinate p1 = inputLine[i1];
  	Coordinate p2 = inputLine[i2];
  	
  	if (! isConcave(p0, p1, p2)) return false;
  	if (! isShallow(p0, p1, p2, distanceTol)) return false;
  	
  	return isShallowSampled(p0, p1, i0, i2, distanceTol);
  }
  
  private static final int NUM_PTS_TO_CHECK = 10;
  
  /**
   * Checks for shallowness over a sample of points in the given section.
   * This helps prevents the simplification from incrementally
   * "skipping" over points which are in fact non-shallow.
   * 
   * @param p0 start coordinate of section
   * @param p2 end coordinate of section
   * @param i0 start index of section
   * @param i2 end index of section
   * @param distanceTol distance tolerance
   * @return
   */
  private boolean isShallowSampled(Coordinate p0, Coordinate p2, int i0, int i2, double distanceTol)
  {
    // check every n'th point to see if it is within tolerance
  	int inc = (i2 - i0) / NUM_PTS_TO_CHECK;
  	if (inc <= 0) inc = 1;

    // GEOS measures the apex's (p2) distance from the segment (p0, sample) - see class doc;
    // JTS measures the sample's distance from the segment (p0, p2) instead.
  	for (int i = i0; i < i2; i += inc) {
  		if (! isShallow(p0, p2, inputLine[i], distanceTol)) return false;
  	}
  	return true;
  }
  
  private static boolean isShallow(Coordinate p0, Coordinate p1, Coordinate p2, double distanceTol)
  {
    double dist = Distance.pointToSegment(p1, p0, p2);
    return dist < distanceTol;
  }
  
  private boolean isConcave(Coordinate p0, Coordinate p1, Coordinate p2)
  {
    int orientation = Orientation.index(p0, p1, p2);
    boolean isConcave = (orientation == angleOrientation);
    return isConcave;
  }
}
