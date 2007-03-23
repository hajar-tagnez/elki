package de.lmu.ifi.dbs.index.tree.metrical.mtreevariants;

import de.lmu.ifi.dbs.data.DatabaseObject;
import de.lmu.ifi.dbs.database.Database;
import de.lmu.ifi.dbs.distance.Distance;
import de.lmu.ifi.dbs.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.distance.distancefunction.EuklideanDistanceFunction;
import de.lmu.ifi.dbs.index.tree.BreadthFirstEnumeration;
import de.lmu.ifi.dbs.index.tree.*;
import de.lmu.ifi.dbs.index.tree.metrical.MetricalIndex;
import de.lmu.ifi.dbs.index.tree.metrical.mtreevariants.util.Assignments;
import de.lmu.ifi.dbs.index.tree.metrical.mtreevariants.util.PQNode;
import de.lmu.ifi.dbs.properties.Properties;
import de.lmu.ifi.dbs.utilities.*;
import de.lmu.ifi.dbs.utilities.heap.DefaultHeap;
import de.lmu.ifi.dbs.utilities.heap.Heap;
import de.lmu.ifi.dbs.utilities.optionhandling.AttributeSettings;
import de.lmu.ifi.dbs.utilities.optionhandling.ClassParameter;
import de.lmu.ifi.dbs.utilities.optionhandling.ParameterException;
import de.lmu.ifi.dbs.utilities.optionhandling.WrongParameterValueException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Abstract super class for all M-Tree variants.
 *
 * @author Elke Achtert (<a
 *         href="mailto:achtert@dbs.ifi.lmu.de">achtert@dbs.ifi.lmu.de</a>)
 */
public abstract class AbstractMTree<O extends DatabaseObject, D extends Distance<D>, N extends AbstractMTreeNode<O, D, N, E>, E extends MTreeEntry<D>>
    extends MetricalIndex<O, D, N, E> {
  /**
   * The default distance function.
   */
  public static final String DEFAULT_DISTANCE_FUNCTION = EuklideanDistanceFunction.class.getName();

  /**
   * Parameter for distance function.
   */
  public static final String DISTANCE_FUNCTION_P = "distancefunction";

  /**
   * Description for parameter distance function.
   */
  public static final String DISTANCE_FUNCTION_D = "the distance function to determine the distance between database objects "
                                                   + Properties.KDD_FRAMEWORK_PROPERTIES.restrictionString(DistanceFunction.class) + ". Default: "
                                                   + AbstractMTree.DEFAULT_DISTANCE_FUNCTION;

  /**
   * The distance function.
   */
  private DistanceFunction<O, D> distanceFunction;

  /**
   * Empty constructor.
   */
  public AbstractMTree() {
    super();
    ClassParameter<DistanceFunction> distFunction = new ClassParameter<DistanceFunction>(DISTANCE_FUNCTION_P,
                                                                                         DISTANCE_FUNCTION_D,
                                                                                         DistanceFunction.class);
    distFunction.setDefaultValue(DEFAULT_DISTANCE_FUNCTION);
    optionHandler.put(distFunction);
  }

  /**
   * Inserts the specified object into this M-Tree.
   *
   * @param object the object to be inserted todo: in subclasses
   */
  public void insert(O object) {
    this.insert(object, true);
  }

  /**
   * Inserts the specified objects into this index sequentially. <p/> todo: in
   * subclasses todo: bulk load method
   *
   * @param objects the objects to be inserted
   */
  public void insert(List<O> objects) {
    for (O object : objects) {
      insert(object, true);
    }

    if (debug) {
      getRoot().test(this, getRootEntry());
    }

  }

  /**
   * Deletes the specified obect from this index.
   *
   * @param o the object to be deleted
   * @return true if this index did contain the object, false otherwise
   */
  public final boolean delete(O o) {
    throw new UnsupportedOperationException("Deletion of objects is not yet supported by an M-Tree!");
  }

  /**
   * Performs necessary operations after deleting the specified object.
   *
   * @param o the object to be deleted
   */
  protected final void postDelete(O o) {
    // do nothing
  }

  /**
   * Performs a range query for the given spatial object with the given
   * epsilon range and the according distance function. The query result is in
   * ascending order to the distance to the query object.
   *
   * @param object  the query object
   * @param epsilon the string representation of the query range
   * @return a List of the query results
   */
  public List<QueryResult<D>> rangeQuery(O object, String epsilon) {
    D range = distanceFunction.valueOf(epsilon);
    final List<QueryResult<D>> result = new ArrayList<QueryResult<D>>();

    doRangeQuery(null, getRoot(), object.getID(), range, result);

    // sort the result according to the distances
    Collections.sort(result);
    return result;
  }

  /**
   * Performs a k-nearest neighbor query for the given NumberVector with the
   * given parameter k and the according distance function. The query result
   * is in ascending order to the distance to the query object.
   *
   * @param object the query object
   * @param k      the number of nearest neighbors to be returned
   * @return a List of the query results
   */
  public List<QueryResult<D>> kNNQuery(O object, int k) {
    if (k < 1) {
      throw new IllegalArgumentException("At least one object has to be requested!");
    }

    final KNNList<D> knnList = new KNNList<D>(k, distanceFunction.infiniteDistance());
    doKNNQuery(object.getID(), knnList);
    return knnList.toList();
  }

  /**
   * Performs a reverse k-nearest neighbor query for the given object ID. The
   * query result is in ascending order to the distance to the query object.
   *
   * @param object the query object
   * @param k      the number of nearest neighbors to be returned
   * @return a List of the query results
   */
  public List<QueryResult<D>> reverseKNNQuery(O object, int k) {
    throw new UnsupportedOperationException("Not yet supported!");
  }

  /**
   * Returns the distance function.
   *
   * @return the distance function
   */
  public final DistanceFunction<O, D> getDistanceFunction() {
    return distanceFunction;
  }

  /**
   * Returns a string representation of this RTree.
   *
   * @return a string representation of this RTree
   */
  public String toString() {
    StringBuffer result = new StringBuffer();
    int dirNodes = 0;
    int leafNodes = 0;
    int objects = 0;
    int levels = 0;

    N node = getRoot();

    while (!node.isLeaf()) {
      if (node.getNumEntries() > 0) {
        E entry = node.getEntry(0);
        node = getNode(entry);
        levels++;
      }
    }

    BreadthFirstEnumeration<O, N, E> enumeration = new BreadthFirstEnumeration<O, N, E>(this, getRootPath());
    while (enumeration.hasMoreElements()) {
      TreeIndexPath path = enumeration.nextElement();
      Entry entry = path.getLastPathComponent().getEntry();
      if (entry.isLeafEntry()) {
        objects++;
        result.append("\n    " + entry.toString());
      }
      else {
        node = file.readPage(entry.getID());
        result.append("\n\n" + node + ", numEntries = " + node.getNumEntries());
        result.append("\n" + entry.toString());

        if (node.isLeaf()) {
          leafNodes++;
        }
        else {
          dirNodes++;
        }
      }
    }

    result.append(getClass().getName()).append(" hat ").append((levels + 1)).append(" Ebenen \n");
    result.append("DirCapacity = ").append(dirCapacity).append("\n");
    result.append("LeafCapacity = ").append(leafCapacity).append("\n");
    result.append(dirNodes).append(" Directory Nodes \n");
    result.append(leafNodes).append(" Leaf Nodes \n");
    result.append(objects).append(" Objects \n");

    result.append("Logical Page Access: ").append(file.getLogicalPageAccess()).append("\n");
    result.append("Physical Read Access: ").append(file.getPhysicalReadAccess()).append("\n");
    result.append("Physical Write Access: ").append(file.getPhysicalWriteAccess()).append("\n");
    result.append("File ").append(file.getClass()).append("\n");

    return result.toString();
  }

  /**
   * @see de.lmu.ifi.dbs.utilities.optionhandling.Parameterizable#setParameters(String[])
   */
  public String[] setParameters(String[] args) throws ParameterException {
    String[] remainingParameters = super.setParameters(args);

    String className = (String) optionHandler.getOptionValue(AbstractMTree.DISTANCE_FUNCTION_P);

    try {
      // noinspection unchecked
      distanceFunction = Util.instantiate(DistanceFunction.class, className);
    }
    catch (UnableToComplyException e) {
      throw new WrongParameterValueException(AbstractMTree.DISTANCE_FUNCTION_P, className, AbstractMTree.DISTANCE_FUNCTION_D, e);
    }

    remainingParameters = distanceFunction.setParameters(remainingParameters);
    setParameters(args, remainingParameters);
    return remainingParameters;
  }

  /**
   * Returns the parameter setting of the attributes.
   *
   * @return the parameter setting of the attributes
   */
  public List<AttributeSettings> getAttributeSettings() {
    List<AttributeSettings> attributeSettings = super.getAttributeSettings();
    attributeSettings.addAll(distanceFunction.getAttributeSettings());
    return attributeSettings;
  }

  /**
   * Sets the databse in the distance function of this index (if existing).
   *
   * @param database the database
   */
  public final void setDatabase(Database<O> database) {
    distanceFunction.setDatabase(database, false, false);
  }

  /**
   * todo: do a bulk load for M-Tree and remove this method Inserts the
   * specified object into this M-Tree.
   *
   * @param object        the object to be inserted
   * @param withPreInsert if this flag is true, the preInsert method will be called
   *                      before inserting the object
   */
  protected void insert(O object, boolean withPreInsert) {
    if (this.debug) {
      debugFine("insert " + object.getID() + " " + object + "\n");
    }

    if (!initialized) {
      initialize(object);
    }

    // choose subtree for insertion
    TreeIndexPath<E> subtree = choosePath(object.getID(), getRootPath());
    if (this.debug) {
      debugFine("\ninsertion-subtree " + subtree + "\n");
    }
    // determine parent distance
    E parentEntry = subtree.getLastPathComponent().getEntry();
    D parentDistance = distance(parentEntry.getRoutingObjectID(), object.getID());
    // create leaf entry and do pre insert
    E entry = createNewLeafEntry(object, parentDistance);
    if (withPreInsert)
      preInsert(entry);
    // get parent node
    N parent = getNode(parentEntry);
    parent.addLeafEntry(entry);
    file.writePage(parent);

    // adjust the tree from subtree to root
    adjustTree(subtree);

    // test
    if (debug && withPreInsert) {
      getRoot().test(this, getRootEntry());
    }
  }

  /**
   * @see de.lmu.ifi.dbs.index.tree.TreeIndex#createEmptyRoot(de.lmu.ifi.dbs.data.DatabaseObject)
   */
  protected final void createEmptyRoot(O object) {
    N root = createNewLeafNode(leafCapacity);
    file.writePage(root);
  }

  /**
   * Performs a k-nearest neighbor query for the given NumberVector with the
   * given parameter k and the according distance function. The query result
   * is in ascending order to the distance to the query object.
   *
   * @param q       the id of the query object
   * @param knnList the query result list
   */
  protected final void doKNNQuery(Integer q, KNNList<D> knnList) {
    final Heap<D, Identifiable> pq = new DefaultHeap<D, Identifiable>();

    // push root
    pq.addNode(new PQNode<D>(distanceFunction.nullDistance(), getRootEntry().getID(), null));
    D d_k = knnList.getKNNDistance();

    // search in tree
    while (!pq.isEmpty()) {
      PQNode<D> pqNode = (PQNode<D>) pq.getMinNode();

      if (pqNode.getKey().compareTo(d_k) > 0) {
        return;
      }

      N node = getNode(pqNode.getValue().getID());
      Integer o_p = pqNode.getRoutingObjectID();

      // directory node
      if (!node.isLeaf()) {
        for (int i = 0; i < node.getNumEntries(); i++) {
          E entry = node.getEntry(i);
          Integer o_r = entry.getRoutingObjectID();
          D r_or = entry.getCoveringRadius();
          D d1 = o_p != null ? distanceFunction.distance(o_p, q) : distanceFunction.nullDistance();
          D d2 = o_p != null ? distanceFunction.distance(o_r, o_p) : distanceFunction.nullDistance();

          D diff = d1.compareTo(d2) > 0 ? d1.minus(d2) : d2.minus(d1);

          D sum = d_k.plus(r_or);

          if (diff.compareTo(sum) <= 0) {
            D d3 = distance(o_r, q);
            D d_min = Util.max(d3.minus(r_or), distanceFunction.nullDistance());
            if (d_min.compareTo(d_k) <= 0) {
              pq.addNode(new PQNode<D>(d_min, entry.getID(), o_r));
            }
          }
        }

      }

      // data node
      else {
        for (int i = 0; i < node.getNumEntries(); i++) {
          E entry = node.getEntry(i);
          Integer o_j = entry.getRoutingObjectID();

          D d1 = o_p != null ? distanceFunction.distance(o_p, q) : distanceFunction.nullDistance();
          D d2 = o_p != null ? distanceFunction.distance(o_j, o_p) : distanceFunction.nullDistance();

          D diff = d1.compareTo(d2) > 0 ? d1.minus(d2) : d2.minus(d1);

          if (diff.compareTo(d_k) <= 0) {
            D d3 = distanceFunction.distance(o_j, q);
            if (d3.compareTo(d_k) <= 0) {
              QueryResult<D> queryResult = new QueryResult<D>(o_j, d3);
              knnList.add(queryResult);
              d_k = knnList.getKNNDistance();
            }
          }
        }
      }
    }
  }

  /**
   * Chooses the best path of the specified subtree for insertion of the given
   * object.
   *
   * @param subtree  the subtree to be tested for insertion
   * @param objectID the id of the obbject to be inserted
   * @return the path of the appropriate subtree to insert the given object
   *         todo: private?
   */
  protected TreeIndexPath<E> choosePath(Integer objectID, TreeIndexPath<E> subtree) {
    N node = getNode(subtree.getLastPathComponent().getEntry());

    // leaf
    if (node.isLeaf()) {
      return subtree;
    }

    D nullDistance = distanceFunction.nullDistance();
    List<DistanceEntry<D, E>> candidatesWithoutExtension = new ArrayList<DistanceEntry<D, E>>();
    List<DistanceEntry<D, E>> candidatesWithExtension = new ArrayList<DistanceEntry<D, E>>();

    for (int i = 0; i < node.getNumEntries(); i++) {
      E entry = node.getEntry(i);
      D distance = distance(objectID, entry.getRoutingObjectID());
      D enlrg = distance.minus(entry.getCoveringRadius());

      if (enlrg.compareTo(nullDistance) <= 0) {
        candidatesWithoutExtension.add(new DistanceEntry<D, E>(entry, distance, i));
      }
      else {
        candidatesWithExtension.add(new DistanceEntry<D, E>(entry, enlrg, i));
      }
    }

    DistanceEntry<D, E> bestCandidate;
    if (!candidatesWithoutExtension.isEmpty()) {
      bestCandidate = Collections.min(candidatesWithoutExtension);
    }
    else {
      Collections.sort(candidatesWithExtension);
      bestCandidate = Collections.min(candidatesWithExtension);
      E entry = bestCandidate.getEntry();
      D cr = entry.getCoveringRadius();
      entry.setCoveringRadius(cr.plus(bestCandidate.getDistance()));
    }

    return choosePath(objectID, subtree
        .pathByAddingChild(new TreeIndexPathComponent<E>(bestCandidate.getEntry(), bestCandidate.getIndex())));
  }

  /**
   * Performs a batch knn query.
   *
   * @param node     the node for which the query should be performed
   * @param ids      the ids of th query objects
   * @param knnLists the knn lists of the query objcets
   */
  protected final void batchNN(N node, List<Integer> ids, Map<Integer, KNNList<D>> knnLists) {
    if (node.isLeaf()) {
      for (int i = 0; i < node.getNumEntries(); i++) {
        E p = node.getEntry(i);
        for (Integer q : ids) {
          KNNList<D> knns_q = knnLists.get(q);
          D knn_q_maxDist = knns_q.getKNNDistance();

          D dist_pq = distanceFunction.distance(p.getRoutingObjectID(), q);
          if (dist_pq.compareTo(knn_q_maxDist) <= 0) {
            knns_q.add(new QueryResult<D>(p.getRoutingObjectID(), dist_pq));
          }
        }
      }
    }
    else {
      List<DistanceEntry<D, E>> entries = getSortedEntries(node, ids);
      for (DistanceEntry<D, E> distEntry : entries) {
        D minDist = distEntry.getDistance();
        for (Integer q : ids) {
          KNNList<D> knns_q = knnLists.get(q);
          D knn_q_maxDist = knns_q.getKNNDistance();

          if (minDist.compareTo(knn_q_maxDist) <= 0) {
            E entry = distEntry.getEntry();
            N child = getNode(entry);
            batchNN(child, ids, knnLists);
            break;
          }
        }
      }
    }
  }

  /**
   * @see de.lmu.ifi.dbs.index.tree.TreeIndex#initializeCapacities(DatabaseObject,boolean)
   */
  protected void initializeCapacities(O object, boolean verbose) {
    D dummyDistance = distanceFunction.nullDistance();
    int distanceSize = dummyDistance.externalizableSize();

    // overhead = index(4), numEntries(4), id(4), isLeaf(0.125)
    double overhead = 12.125;
    if (pageSize - overhead < 0) {
      throw new RuntimeException("Node size of " + pageSize + " Bytes is chosen too small!");
    }

    // dirCapacity = (pageSize - overhead) / (nodeID + objectID +
    // coveringRadius + parentDistance) + 1
    dirCapacity = (int) (pageSize - overhead) / (4 + 4 + distanceSize + distanceSize) + 1;

    if (dirCapacity <= 1) {
      throw new RuntimeException("Node size of " + pageSize + " Bytes is chosen too small!");
    }

    if (dirCapacity < 10) {
      warning("Page size is choosen too small! Maximum number of entries " + "in a directory node = " + (dirCapacity - 1));
    }
    // leafCapacity = (pageSize - overhead) / (objectID + parentDistance) +
    // 1
    leafCapacity = (int) (pageSize - overhead) / (4 + distanceSize) + 1;

    if (leafCapacity <= 1) {
      throw new RuntimeException("Node size of " + pageSize + " Bytes is chosen too small!");
    }

    if (leafCapacity < 10) {
      warning("Page size is choosen too small! Maximum number of entries " + "in a leaf node = " + (leafCapacity - 1));
    }

    if (verbose) {
      verbose("Directory Capacity: " + (dirCapacity - 1) + "\nLeaf Capacity:    " + (leafCapacity - 1));
    }
  }

  /**
   * Sorts the entries of the specified node according to their minimum
   * distance to the specified object.
   *
   * @param node the node
   * @param q    the id of the object
   * @return a list of the sorted entries
   */
  protected final List<DistanceEntry<D, E>> getSortedEntries(N node, Integer q) {
    List<DistanceEntry<D, E>> result = new ArrayList<DistanceEntry<D, E>>();

    for (int i = 0; i < node.getNumEntries(); i++) {
      E entry = node.getEntry(i);
      D distance = distance(entry.getRoutingObjectID(), q);
      D minDist = entry.getCoveringRadius().compareTo(distance) > 0 ? getDistanceFunction().nullDistance() : distance.minus(entry
          .getCoveringRadius());

      result.add(new DistanceEntry<D, E>(entry, minDist, i));
    }

    Collections.sort(result);
    return result;
  }

  /**
   * Sorts the entries of the specified node according to their minimum
   * distance to the specified objects.
   *
   * @param node the node
   * @param ids  the ids of the objects
   * @return a list of the sorted entries
   */
  protected final List<DistanceEntry<D, E>> getSortedEntries(N node, Integer[] ids) {
    List<DistanceEntry<D, E>> result = new ArrayList<DistanceEntry<D, E>>();

    for (int i = 0; i < node.getNumEntries(); i++) {
      E entry = node.getEntry(i);

      D minMinDist = getDistanceFunction().infiniteDistance();
      for (Integer q : ids) {
        D distance = getDistanceFunction().distance(entry.getRoutingObjectID(), q);
        D minDist = entry.getCoveringRadius().compareTo(distance) > 0 ?
                    getDistanceFunction().nullDistance() :
                    distance.minus(entry.getCoveringRadius());
        minMinDist = Util.max(minMinDist, minDist);
      }
      result.add(new DistanceEntry<D, E>(entry, minMinDist, i));
    }

    Collections.sort(result);
    return result;
  }

  /**
   * Creates a new leaf entry representing the specified data object in the
   * specified subtree.
   *
   * @param object         the data object to be represented by the new entry
   * @param parentDistance the distance from the object to the routing object of the
   *                       parent node
   * @return the newly created leaf entry
   */
  abstract protected E createNewLeafEntry(O object, D parentDistance);

  /**
   * Creates a new directory entry representing the specified node.
   *
   * @param node            the node to be represented by the new entry
   * @param routingObjectID the id of the routing object of the node
   * @param parentDistance  the distance from the routing object of the node to the
   *                        routing object of the parent node
   * @return the newly created directory entry
   */
  abstract protected E createNewDirectoryEntry(N node, Integer routingObjectID, D parentDistance);

  /**
   * Splits the specified node and returns the split result.
   *
   * @param node the node to be splitted
   * @return the split result
   */
  private SplitResult split(N node) {
    // do the split
    // todo split stratgey
    MTreeSplit<O, D, N, E> split = new MLBDistSplit<O, D, N, E>(node, distanceFunction);
    Assignments<D, E> assignments = split.getAssignments();
    N newNode = node.splitEntries(assignments.getFirstAssignments(), assignments.getSecondAssignments());

    // write changes to file
    file.writePage(node);
    file.writePage(newNode);

    if (this.debug) {
      String msg = "Split Node " + node.getID() + " (" + this.getClass() + ")\n" + "      newNode " + newNode.getID() + "\n"
                   + "      firstPromoted " + assignments.getFirstRoutingObject() + "\n" + "      firstAssignments(" + node.getID() + ") "
                   + assignments.getFirstAssignments() + "\n" + "      firstCR " + assignments.getFirstCoveringRadius() + "\n"
                   + "      secondPromoted " + assignments.getSecondRoutingObject() + "\n" + "      secondAssignments(" + newNode.getID()
                   + ") " + assignments.getSecondAssignments() + "\n" + "      secondCR " + assignments.getSecondCoveringRadius() + "\n";
      debugFine(msg);
    }

    return new SplitResult(split, newNode);
  }

  /**
   * Sorts the entries of the specified node according to their minimum
   * distance to the specified objects.
   *
   * @param node the node
   * @param ids  the ids of the objects
   * @return a list of the sorted entries
   */
  private List<DistanceEntry<D, E>> getSortedEntries(N node, List<Integer> ids) {
    List<DistanceEntry<D, E>> result = new ArrayList<DistanceEntry<D, E>>();

    for (int i = 0; i < node.getNumEntries(); i++) {
      E entry = node.getEntry(i);

      D minMinDist = distanceFunction.infiniteDistance();
      for (Integer q : ids) {
        D distance = distance(entry.getRoutingObjectID(), q);
        D minDist = entry.getCoveringRadius().compareTo(distance) > 0 ? distanceFunction.nullDistance() : distance.minus(entry
            .getCoveringRadius());
        if (minDist.compareTo(minMinDist) < 0) {
          minMinDist = minDist;
        }
      }
      result.add(new DistanceEntry<D, E>(entry, minMinDist, i));
    }

    Collections.sort(result);
    return result;
  }

  /**
   * Performs a range query. It starts from the root node and recursively
   * traverses all paths, which cannot be excluded from leading to
   * qualififying objects.
   *
   * @param o_p    the routing object of the specified node
   * @param node   the root of the subtree to be traversed
   * @param q      the id of the query object
   * @param r_q    the query range
   * @param result the list holding the query results
   */
  private void doRangeQuery(Integer o_p, N node, Integer q, D r_q, List<QueryResult<D>> result) {
    if (!node.isLeaf()) {
      for (int i = 0; i < node.getNumEntries(); i++) {
        E entry = node.getEntry(i);
        Integer o_r = entry.getRoutingObjectID();

        D r_or = entry.getCoveringRadius();
        D d1 = o_p != null ? distanceFunction.distance(o_p, q) : distanceFunction.nullDistance();
        D d2 = o_p != null ? distanceFunction.distance(o_r, o_p) : distanceFunction.nullDistance();

        D diff = d1.compareTo(d2) > 0 ? d1.minus(d2) : d2.minus(d1);

        D sum = r_q.plus(r_or);

        if (diff.compareTo(sum) <= 0) {
          D d3 = distanceFunction.distance(o_r, q);
          if (d3.compareTo(sum) <= 0) {
            N child = getNode(entry.getID());
            doRangeQuery(o_r, child, q, r_q, result);
          }
        }

      }
    }

    else {
      for (int i = 0; i < node.getNumEntries(); i++) {
        MTreeEntry<D> entry = node.getEntry(i);
        Integer o_j = entry.getRoutingObjectID();

        D d1 = o_p != null ? distanceFunction.distance(o_p, q) : distanceFunction.nullDistance();
        D d2 = o_p != null ? distanceFunction.distance(o_j, o_p) : distanceFunction.nullDistance();

        D diff = d1.compareTo(d2) > 0 ? d1.minus(d2) : d2.minus(d1);

        if (diff.compareTo(r_q) <= 0) {
          D d3 = distanceFunction.distance(o_j, q);
          if (d3.compareTo(r_q) <= 0) {
            QueryResult<D> queryResult = new QueryResult<D>(o_j, d3);
            result.add(queryResult);
          }
        }
      }
    }
  }

  /**
   * Adjusts the tree after insertion of some nodes.
   *
   * @param subtree the subtree to be adjusted
   */
  private void adjustTree(TreeIndexPath<E> subtree) {
    if (this.debug) {
      debugFine("\nAdjust tree " + subtree + "\n");
    }

    // get the root of the subtree
    Integer nodeIndex = subtree.getLastPathComponent().getIndex();
    N node = getNode(subtree.getLastPathComponent().getEntry());

    // overflow in node; split the node
    if (hasOverflow(node)) {
      SplitResult splitResult = split(node);
      N splitNode = splitResult.newNode;
      Assignments<D, E> assignments = splitResult.split.getAssignments();

      // if root was split: create a new root that points the two split
      // nodes
      if (node.getID() == getRootEntry().getID()) {
        adjustTree(createNewRoot(node, splitNode, assignments.getFirstRoutingObject(), assignments.getSecondRoutingObject()));
      }
      // node is not root
      else {
        // get the parent and add the new split node
        E parentEntry = subtree.getParentPath().getLastPathComponent().getEntry();
        N parent = getNode(parentEntry);
        if (this.debug) {
          debugFine("\nparent " + parent);
        }
        D parentDistance2 = distance(parentEntry.getRoutingObjectID(), assignments.getSecondRoutingObject());
        parent.addDirectoryEntry(createNewDirectoryEntry(splitNode, assignments.getSecondRoutingObject(), parentDistance2));

        // adjust the entry representing the (old) node, that has been
        // splitted
        D parentDistance1 = distance(parentEntry.getRoutingObjectID(), assignments.getFirstRoutingObject());
        node.adjustEntry(parent.getEntry(nodeIndex), assignments.getFirstRoutingObject(), parentDistance1, this);

        // write changes in parent to file
        file.writePage(parent);
        adjustTree(subtree.getParentPath());
      }
    }
    // no overflow, only adjust parameters of the entry representing the
    // node
    else {
      if (node.getID() != getRootEntry().getID()) {
        E parentEntry = subtree.getParentPath().getLastPathComponent().getEntry();
        N parent = getNode(parentEntry);
        int index = subtree.getLastPathComponent().getIndex();
        E entry = parent.getEntry(index);
        node.adjustEntry(entry, entry.getRoutingObjectID(), entry.getParentDistance(), this);
        // write changes in parent to file
        file.writePage(parent);
        adjustTree(subtree.getParentPath());
      }
      // root level is reached
      else {
        E rootEntry = getRootEntry();
        node.adjustEntry(rootEntry, rootEntry.getRoutingObjectID(), rootEntry.getParentDistance(), this);
      }
    }
  }

  /**
   * Returns true if in the specified node an overflow has occured, false
   * otherwise.
   *
   * @param node the node to be tested for overflow
   * @return true if in the specified node an overflow has occured, false
   *         otherwise
   */
  private boolean hasOverflow(N node) {
    if (node.isLeaf()) {
      return node.getNumEntries() == leafCapacity;
    }

    return node.getNumEntries() == dirCapacity;
  }

  /**
   * Creates a new root node that points to the two specified child nodes and
   * return the path to the new root.
   *
   * @param oldRoot the old root of this RTree
   * @param newNode the new split node
   * @param firstRoutingObjectID the id of the routing objects of the first child node
   * @param secondRoutingObjectID the id of the routing objects of the second child node
   * @return the path to the new root node that points to the two specified
   *         child nodes
   */
  private TreeIndexPath<E> createNewRoot(final N oldRoot,
                                         final N newNode,
                                         Integer firstRoutingObjectID,
                                         Integer secondRoutingObjectID) {

    N root = createNewDirectoryNode(dirCapacity);
    file.writePage(root);

    // switch the ids
    oldRoot.setID(root.getID());
    if (!oldRoot.isLeaf()) {
      for (int i = 0; i < oldRoot.getNumEntries(); i++) {
        N node = getNode(oldRoot.getEntry(i));
        file.writePage(node);
      }
    }

    root.setID(getRootEntry().getID());
    D parentDistance1 = distance(getRootEntry().getRoutingObjectID(), firstRoutingObjectID);
    D parentDistance2 = distance(getRootEntry().getRoutingObjectID(), secondRoutingObjectID);
    E oldRootEntry = createNewDirectoryEntry(oldRoot, firstRoutingObjectID, parentDistance1);
    E newRootEntry = createNewDirectoryEntry(newNode, secondRoutingObjectID, parentDistance2);
    root.addDirectoryEntry(oldRootEntry);
    root.addDirectoryEntry(newRootEntry);

    file.writePage(root);
    file.writePage(oldRoot);
    file.writePage(newNode);
    if (this.debug) {
      String msg = "\nCreate new Root: ID=" + root.getID();
      msg += "\nchild1 " + oldRoot;
      msg += "\nchild2 " + newNode;
      msg += "\n";
      debugFine(msg);
    }

    return new TreeIndexPath<E>(new TreeIndexPathComponent<E>(getRootEntry(), null));
  }

  /**
   * Returns the distance between the two specified ids.
   *
   * @param id1 the first id
   * @param id2 the second id
   * @return the distance between the two specified ids
   */
  protected D distance(Integer id1, Integer id2) {
    if (id1 == null || id2 == null)
      return distanceFunction.undefinedDistance();
    return distanceFunction.distance(id1, id2);
  }

  /**
   * Encapsulates a split object and the newly created node-
   */
  private class SplitResult {
    private MTreeSplit<O, D, N, E> split;

    private N newNode;

    public SplitResult(MTreeSplit<O, D, N, E> split, N newNode) {
      this.split = split;
      this.newNode = newNode;
    }
  }

}