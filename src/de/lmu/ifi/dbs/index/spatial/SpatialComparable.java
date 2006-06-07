package de.lmu.ifi.dbs.index.spatial;

/**
 * Defines the required methods needed for comparison of spatial objects.
 *
 * @author Elke Achtert (<a href="mailto:achtert@dbs.ifi.lmu.de">achtert@dbs.ifi.lmu.de</a>)
 */
public interface SpatialComparable {
  /**
   * Returns the dimensionality of the object.
   *
   * @return the dimensionality
   */
  int getDimensionality();

  /**
   * Returns the minimum coordinate at the specified dimension.
   *
   * @param dimension the dimension for which the coordinate should be returned,
   *                  where 1 &le; dimension &le; <code>getDimensionality()</code>
   * @return the minimum coordinate at the specified dimension
   */
  double getMin(int dimension);

  /**
   * Returns the maximum coordinate at the specified dimension.
   *
   * @param dimension the dimension for which the coordinate should be returned,
   *                  where 1 &le; dimension &le; <code>getDimensionality()</code>
   * @return the maximum coordinate at the specified dimension
   */
  double getMax(int dimension);
}
