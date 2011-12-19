package experimentalcode.shared.index.subspace.vafile;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2011
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.relation.Relation;

/**
 * DAFile
 * 
 * @author Thomas Bernecker
 * @created 22.09.2009
 * @date 22.09.2009
 */
public class DAFile {
  private int dimension;

  private double[] splitPositions;

  private int selectivityCoeff;

  public DAFile(int dimension) {
    this.dimension = dimension;
    selectivityCoeff = -1;
  }

  public void setPartitions(Relation<? extends NumberVector<?, ?>> relation, int partitions) {
    splitPositions = new double[partitions + 1];
    final int size = relation.size();

    double[] tempdata = new double[size];
    int j = 0;
    for(DBID id : relation.iterDBIDs()) {
      tempdata[j] = relation.get(id).doubleValue(dimension + 1);
      j += 1;
    }
    Arrays.sort(tempdata);

    for(int b = 0; b < partitions; b++) {
      int start = (int) (b * size / (double) partitions);
      splitPositions[b] = tempdata[start];
    }
    // make sure that last object will be included
    splitPositions[partitions] = tempdata[size - 1] + 0.000001;
  }

  /**
   * @return the split positions
   */
  public double[] getSplitPositions() {
    return splitPositions;
  }

  /**
   * @return the dimension
   */
  public int getDimension() {
    return dimension;
  }

  /**
   * @param dimension the dimension to set
   */
  public void setDimension(int dimension) {
    this.dimension = dimension;
  }

  /**
   * @return the selectivityCoeff
   */
  public int getSelectivityCoeff() {
    return selectivityCoeff;
  }

  public void setSelectivityCoeff(int val) {
    selectivityCoeff = val;
  }

  public int getIOCosts() {
    return splitPositions.length * 8 + 4;
  }

  /**
   * 
   * @param daFileList
   * @param query
   * @param epsilon
   */
  public static <V extends NumberVector<?, ?>> void calculateSelectivityCoeffs(List<DAFile> daFiles, V query, double epsilon) {
    final int dimensions = query.getDimensionality();
    double[] lowerVals = new double[dimensions];
    double[] upperVals = new double[dimensions];

    VectorApproximation queryApprox = calculateApproximation(null, query, daFiles);

    for(int i = 0; i < dimensions; i++) {
      lowerVals[i] = query.doubleValue(i + 1) - epsilon;
      upperVals[i] = query.doubleValue(i + 1) + epsilon;
    }

    DoubleVector lowerEpsilon = new DoubleVector(lowerVals);
    VectorApproximation lowerEpsilonPartitions = calculateApproximation(null, lowerEpsilon, daFiles);

    DoubleVector upperEpsilon = new DoubleVector(upperVals);
    VectorApproximation upperEpsilonPartitions = calculateApproximation(null, upperEpsilon, daFiles);

    for(int i = 0; i < daFiles.size(); i++) {
      int coeff = (queryApprox.getApproximation(i) - lowerEpsilonPartitions.getApproximation(i)) + (upperEpsilonPartitions.getApproximation(i) - queryApprox.getApproximation(i)) + 1;
      daFiles.get(i).setSelectivityCoeff(coeff);
    }
  }

  public static VectorApproximation calculateApproximation(DBID id, NumberVector<?, ?> dv, List<DAFile> daFiles) {
    int[] approximation = new int[dv.getDimensionality()];
    for(int i = 0; i < daFiles.size(); i++) {
      double val = dv.doubleValue(i + 1);
      double[] borders = daFiles.get(i).getSplitPositions();
      assert borders != null : "borders are null";
      int lastBorderIndex = borders.length - 1;

      // value is lower outlier
      if(val < borders[0]) {
        approximation[i] = 0;
      } // value is upper outlier
      else if(val > borders[lastBorderIndex]) {
        approximation[i] = lastBorderIndex - 1;
      } // normal case
      else {
        for(int s = 0; s < lastBorderIndex; s++) {
          if(val >= borders[s] && val < borders[s + 1] && approximation[i] != -1) {
            approximation[i] = s;
          }
        }
      }
    }
    return new VectorApproximation(id, approximation);
  }

  public static <V extends NumberVector<?, ?>> List<DAFile> sortBySelectivity(List<DAFile> daFiles) {
    Collections.sort(daFiles, new DAFileSelectivityComparator<V>());
    return daFiles;
  }

  static class DAFileSelectivityComparator<V extends NumberVector<?, ?>> implements Comparator<DAFile> {
    @Override
    public int compare(DAFile a, DAFile b) {
      return Double.compare(a.getSelectivityCoeff(), b.getSelectivityCoeff());
    }
  }
}