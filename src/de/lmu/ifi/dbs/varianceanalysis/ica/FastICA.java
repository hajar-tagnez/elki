package de.lmu.ifi.dbs.varianceanalysis.ica;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.*;

import de.lmu.ifi.dbs.data.RealVector;
import de.lmu.ifi.dbs.database.Database;
import de.lmu.ifi.dbs.logging.LogLevel;
import de.lmu.ifi.dbs.logging.ProgressLogRecord;
import de.lmu.ifi.dbs.math.linearalgebra.EigenvalueDecomposition;
import de.lmu.ifi.dbs.math.linearalgebra.Matrix;
import de.lmu.ifi.dbs.math.linearalgebra.Vector;
import de.lmu.ifi.dbs.properties.Properties;
import de.lmu.ifi.dbs.utilities.Progress;
import de.lmu.ifi.dbs.utilities.UnableToComplyException;
import de.lmu.ifi.dbs.utilities.Util;
import de.lmu.ifi.dbs.utilities.optionhandling.*;
import de.lmu.ifi.dbs.varianceanalysis.PercentageEigenPairFilter;

/**
 * Implementation of the FastICA algorithm.
 * 
 * @author Elke Achtert (<a
 *         href="mailto:achtert@dbs.ifi.lmu.de">achtert@dbs.ifi.lmu.de</a>)
 */
public class FastICA extends AbstractParameterizable {
	/**
	 * The number format for debugging purposes.
	 */
	private static NumberFormat NF = NumberFormat.getInstance(Locale.US);

	static {
		NF.setMinimumFractionDigits(4);
		NF.setMaximumFractionDigits(4);
	}

	/**
	 * The approach.
	 */
	public enum Approach {
		SYMMETRIC, DEFLATION
	}

	/**
	 * Parameter for ic.
	 */
	public static final String IC_P = "ic";

	/**
	 * Description for parameter ic.
	 */
	public static final String IC_D = "the maximum number of independent components (ics) to be found. "
			+ "The number of ics to be found must be less to or equal than " + "the dimensionality of the feature space.";

	/**
	 * Parameter for initial unit matrix.
	 */
	public static final String UNIT_F = "unit";

	/**
	 * Description for parameter initial unit matrix.
	 */
	public static final String UNIT_D = "flag that indicates that the unit matrix " + "is used as initial weight matrix. If this flag "
			+ "is not set, the initial weight matrix will be " + "generated randomly.";

	/**
	 * Parameter for maxIter.
	 */
	public static final String MAX_ITERATIONS_P = "maxIter";

	/**
	 * The default value for parameter maxIter.
	 */
	public static final int DEFAULT_MAX_ITERATIONS = 1000;

	/**
	 * Description for parameter maxIter.
	 */
	public static final String MAX_ITERATIONS_D = "the number of maximum iterations. " + "Default: " + DEFAULT_MAX_ITERATIONS;

	/**
	 * Parameter for approach.
	 */
	public static final String APPROACH_P = "app";

	/**
	 * The default approach.
	 */
	public static final Approach DEFAULT_APPROACH = Approach.DEFLATION;

	/**
	 * Description for parameter approach.
	 */
	public static final String APPROACH_D = "the approach to be used, available approaches are: [" + Approach.DEFLATION + "| "
			+ Approach.SYMMETRIC + "]" + ". Default: " + DEFAULT_APPROACH + ")";

	/**
	 * Parameter for contrastfunction g.
	 */
	public static final String G_P = "g";

	/**
	 * The default g.
	 */
	public static final String DEFAULT_G = KurtosisBasedContrastFunction.class.getName();

	/**
	 * Description for parameter g.
	 */
	public static final String G_D = "the contrast function to be used to estimate negentropy "
			+ Properties.KDD_FRAMEWORK_PROPERTIES.restrictionString(ContrastFunction.class) + ". Default: " + DEFAULT_G;

	/**
	 * Parameter for epsilon.
	 */
	public static final String EPSILON_P = "epsilon";

	/**
	 * The default epsilon.
	 */
	public static final double DEFAULT_EPSILON = 0.001;

	/**
	 * Description for parameter epsilon.
	 */
	public static final String EPSILON_D = "a positive value defining the criterion for convergence of weight vector w_p: "
			+ "if the difference of the values of w_p after two iterations " + "is less than or equal to epsilon. " + "Default: "
			+ DEFAULT_EPSILON;

	/**
	 * Option string for parameter alpha.
	 */
	public static final String ALPHA_P = "alpha";

	/**
	 * Description for parameter alpha.
	 */
	public static final String ALPHA_D = "a double between 0 and 1 specifying " + "the threshold for strong eigenvectors of the pca "
			+ "performed as a preprocessing step: " + "the strong eigenvectors explain a "
			+ "portion of at least alpha of the total variance " + "Default: " + PercentageEigenPairFilter.DEFAULT_ALPHA + ")";

	/**
	 * The input data.
	 */
	private Matrix inputData;

	/**
	 * The centered pca data.
	 */
	private Matrix centeredData;

	/**
	 * The whitened centered data.
	 */
	private Matrix whitenedData;

	/**
	 * The centroid of the input data.
	 */
	private Vector centroid;

	/**
	 * The whitening matrix.
	 */
	private Matrix whiteningMatrix;

	/**
	 * The dewhitening matrix.
	 */
	private Matrix dewhiteningMatrix;

	/**
	 * The mixing matrix of the whitened data.
	 */
	private Matrix mixingMatrix;

	/**
	 * The separating matrix of the whitened data.
	 */
	private Matrix separatingMatrix;

	/**
	 * The number of independent components to be found.
	 */
	private Integer numICs;

	/**
	 * The (current) weight matrix.
	 */
	private Matrix weightMatrix;

	/**
	 * The independent components
	 */
	private Matrix ics;

	/**
	 * The maximum number of iterations to be performed.
	 */
	private int maximumIterations;

	/**
	 * The approach to be used.
	 */
	private Approach approach;

	/**
	 * The contrast function;
	 */
	private ContrastFunction contrastFunction;

	/**
	 * The convergence criterion.
	 */
	private double epsilon;

	/**
	 * True, if the initial weight matrix is a unit matrix, false if the initial
	 * weight matrix is generated randomly.
	 */
	private boolean initialUnitWeightMatrix;

	/**
	 * The alpha parameter for the preprocessing pca.
	 */
	private double alpha;

	/**
	 * Provides the fast ica algorithm.
	 */
	public FastICA() {
		super();
		optionHandler.put(UNIT_F, new Flag(UNIT_F, UNIT_D));

		// parameter ics
		IntParameter ic = new IntParameter(IC_P, IC_D, new GreaterConstraint(0));
		ic.setOptional(true);
		optionHandler.put(IC_P, ic);

		// parameter max iteration
		IntParameter maxIt = new IntParameter(MAX_ITERATIONS_P, MAX_ITERATIONS_D, new GreaterConstraint(0));
		maxIt.setDefaultValue(DEFAULT_MAX_ITERATIONS);
		optionHandler.put(MAX_ITERATIONS_P, maxIt);

		// parameter approach
		StringParameter app = new StringParameter(APPROACH_P, APPROACH_D, new EqualStringConstraint(new String[] {
				Approach.DEFLATION.toString(), Approach.SYMMETRIC.toString() }));
		app.setDefaultValue(DEFAULT_APPROACH.getClass().getName());
		optionHandler.put(APPROACH_P, app);

		// parameter constrast function
		ClassParameter contFunc = new ClassParameter(G_P, G_D, ContrastFunction.class);
		contFunc.setDefaultValue(DEFAULT_G);
		optionHandler.put(G_P, contFunc);

		// parameter epsilon
		DoubleParameter eps = new DoubleParameter(EPSILON_P, EPSILON_D, new GreaterConstraint(0));
		eps.setDefaultValue(DEFAULT_EPSILON);
		optionHandler.put(EPSILON_P, eps);

		// parameter alpha
		ArrayList<ParameterConstraint<Number>> alphaCons = new ArrayList<ParameterConstraint<Number>>();
		alphaCons.add(new GreaterConstraint(0));
		alphaCons.add(new LessEqualConstraint(1));
		DoubleParameter alpha = new DoubleParameter(ALPHA_P, ALPHA_D, alphaCons);
		alpha.setDefaultValue(PercentageEigenPairFilter.DEFAULT_ALPHA);
		optionHandler.put(ALPHA_P, alpha);

		this.debug = true;
	}

	/**
	 * @see de.lmu.ifi.dbs.utilities.optionhandling.Parameterizable#setParameters(String[])
	 */
	public String[] setParameters(String[] args) throws ParameterException {
		String[] remainingParameters = super.setParameters(args);

		// initial mixing matrix
		initialUnitWeightMatrix = optionHandler.isSet(UNIT_F);

		// ics
		if (optionHandler.isSet(IC_P)) {
			numICs = (Integer) optionHandler.getOptionValue(IC_P);
		}

		// maximum iterations
		maximumIterations = (Integer) optionHandler.getOptionValue(MAX_ITERATIONS_P);

		// approach

		String approachString = (String) optionHandler.getOptionValue(APPROACH_P);
		if (approachString.equals(Approach.DEFLATION.toString())) {
			approach = Approach.DEFLATION;
		} else if (approachString.equals(Approach.SYMMETRIC.toString())) {
			approach = Approach.SYMMETRIC;
		} else
			throw new WrongParameterValueException(APPROACH_P, approachString, APPROACH_P);

		// contrast function
		String className = (String)optionHandler.getOptionValue(G_P);
		try {
			// noinspection unchecked
			contrastFunction = Util.instantiate(ContrastFunction.class, className);
		} catch (UnableToComplyException e) {
			throw new WrongParameterValueException(G_P, className, G_D, e);
		}
		remainingParameters = contrastFunction.setParameters(remainingParameters);
		setParameters(args, remainingParameters);

		// epsilon
		epsilon = (Double)optionHandler.getOptionValue(EPSILON_P);

		// alpha
		alpha = (Double)optionHandler.getOptionValue(ALPHA_P);

		return remainingParameters;
	}

	/**
	 * @see de.lmu.ifi.dbs.utilities.optionhandling.Parameterizable#getAttributeSettings()
	 */
	public List<AttributeSettings> getAttributeSettings() {
		List<AttributeSettings> settings = super.getAttributeSettings();

		AttributeSettings mySettings = settings.get(0);
		mySettings.addSetting(IC_P, Integer.toString(numICs));
		mySettings.addSetting(UNIT_F, Boolean.toString(initialUnitWeightMatrix));
		mySettings.addSetting(MAX_ITERATIONS_P, Integer.toString(maximumIterations));
		mySettings.addSetting(APPROACH_P, approach.toString());
		mySettings.addSetting(G_P, contrastFunction.getClass().getName());
		mySettings.addSetting(EPSILON_P, Double.toString(epsilon));
		mySettings.addSetting(ALPHA_P, Double.toString(alpha));

		return settings;
	}

	/**
	 * Runs the fast ica algorithm on the specified database.
	 * 
	 * @param database
	 *            the database containing the data vectors
	 * @param verbose
	 *            flag that allows verbode messages
	 */
	public void run(Database<RealVector> database, boolean verbose) {
		if (verbose) {
			verbose("database size: " + database.size() + " x " + database.dimensionality());
			verbose("preprocessing and data whitening...");
		}
		preprocessAndWhitenData(database);

		// set number of independent components to be found
		int dim = whitenedData.getRowDimensionality();
		if (numICs == null || numICs > dim) {
			numICs = dim;
		}
		if (verbose) {
			verbose("\n numICs = " + numICs);
		}

		// initialize the weight matrix
		weightMatrix = new Matrix(dim, numICs);

		// determine the weights
		if (approach.equals(Approach.SYMMETRIC)) {
			symmetricOrthogonalization(verbose);
		} else if (approach.equals(Approach.DEFLATION)) {
			deflationaryOrthogonalization(verbose);
		}

		// compute mixing and separating matrix
		mixingMatrix = dewhiteningMatrix.times(weightMatrix);
		separatingMatrix = weightMatrix.transpose().times(whiteningMatrix);

		// compute ics
		ics = separatingMatrix.times(inputData);

		if (debug) {
			// StringBuffer msg = new StringBuffer();
			// msg.append("\nweight " + weightMatrix);
			// msg.append("\nmix " + mixingMatrix.toString(NF));
			// msg.append("\nsep " + separatingMatrix);
			// msg.append("\nics " + ics.transpose());
			// debugFine(msg.toString());
			generate(weightMatrix, Util.centroid(whitenedData).getColumnPackedCopy(), "w");
			Matrix ic = ics.transpose();
			for (int i = 0; i < ic.getRowDimensionality(); i++) {
				// output(ic.getRow(i), "ic"+i);

			}
			output(ics.transpose(), "ics");
			generate(mixingMatrix, Util.centroid(inputData).getColumnPackedCopy(), "a");
		}
	}

	/**
	 * Returns the resulting independent components.
	 * 
	 * @return the resulting independent components
	 */
	public Matrix getICVectors() {
		return ics;
	}

	/**
	 * Returns the assumed mixing matrix.
	 * 
	 * @return the assumed mixing matrix
	 */
	public Matrix getMixingMatrix() {
		return mixingMatrix;
	}

	/**
	 * Returns the assumed seperating matrix.
	 * 
	 * @return the assumed seperating matrix
	 */
	public Matrix getSeparatingMatrix() {
		return separatingMatrix;
	}

	/**
	 * Returns the weight matrix.
	 * 
	 * @return the weight matrix
	 */
	public Matrix getWeightMatrix() {
		return weightMatrix;
	}

	/**
	 * Returns the matrix of the original input data.
	 * 
	 * @return the matrix of the original input data
	 */
	public Matrix getInputData() {
		return inputData;
	}

	/**
	 * Returns the centered pca data.
	 * 
	 * @return the centered pca data
	 */
	public Matrix getCenteredData() {
		return centeredData;
	}

	/**
	 * Returns the whitened data.
	 * 
	 * @return the whitened data
	 */
	public Matrix getWhitenedData() {
		return whitenedData;
	}

	/**
	 * Performs the FastICA algorithm using deflationary orthogonalization.
	 * 
	 * @param verbose
	 *            flag indicating verbose messages
	 */
	private void deflationaryOrthogonalization(boolean verbose) {
		Progress progress = new Progress("Deflationary Orthogonalization ", numICs);

		for (int p = 0; p < numICs; p++) {
			if (verbose) {
				progress.setProcessed(p);
				progress(new ProgressLogRecord(LogLevel.PROGRESS, Util.status(progress), progress.getTask(), progress.status()));
			}

			int iterations = 0;
			boolean converged = false;

			// init w_p
			int dimensionality = whitenedData.getRowDimensionality();
			Vector w_p = initialUnitWeightMatrix ? Vector.unitVector(dimensionality, p) : Vector.randomNormalizedVector(dimensionality);

			while ((iterations < maximumIterations) && (!converged)) {
				// determine w_p
				Vector w_p_old = w_p.copy();
				w_p = updateWeight(w_p);

				// orthogonalize w_p
				Vector sum = new Vector(dimensionality);
				for (int j = 0; j < p; j++) {
					Vector w_j = weightMatrix.getColumnVector(j);
					sum = sum.plus(w_j.times(w_p.scalarProduct(w_j)));
				}
				w_p = w_p.minus(sum);
				w_p.normalize();

				// test if good approximation
				converged = isVectorConverged(w_p_old, w_p);
				iterations++;

				if (verbose) {
					progress(new ProgressLogRecord(LogLevel.PROGRESS, Util.status(progress) + " - " + iterations, progress.getTask(),
							progress.status()));
				}

				if (debug) {
					debugFine("\nw_" + p + " " + w_p + "\n");
					generate(w_p, Util.centroid(whitenedData).getColumnPackedCopy(), "w_" + p + iterations);
				}
			}

			// write new vector to the matrix
			weightMatrix.setColumn(p, w_p);
		}
	}

	/**
	 * Performs the FastICA algorithm using symmetric orthogonalization.
	 * 
	 * @param verbose
	 *            flag indicating verbose messages
	 */
	private void symmetricOrthogonalization(boolean verbose) {
		Progress progress = new Progress("Symmetric Orthogonalization ", numICs);

		// choose initial values for w_p
		int dimensionality = whitenedData.getRowDimensionality();
		for (int p = 0; p < numICs; ++p) {
			Vector w_p = initialUnitWeightMatrix ? Vector.unitVector(dimensionality, p) : Vector.randomNormalizedVector(dimensionality);

			weightMatrix.setColumn(p, w_p);
		}
		// ortogonalize weight matrix
		weightMatrix = symmetricOrthogonalizationOfWeightMatrix();
		generate(weightMatrix, Util.centroid(whitenedData).getColumnPackedCopy(), "w_0");

		int iterations = 0;
		boolean converged = false;
		while ((iterations < maximumIterations) && (!converged)) {
			Matrix w_old = weightMatrix.copy();
			for (int p = 0; p < numICs; p++) {
				Vector w_p = updateWeight(weightMatrix.getColumnVector(p));
				weightMatrix.setColumn(p, w_p);
			}
			// orthogonalize
			weightMatrix = symmetricOrthogonalizationOfWeightMatrix();

			// test if good approximation
			converged = isMatrixConverged(w_old, weightMatrix);
			iterations++;

			if (verbose) {
				progress(new ProgressLogRecord(LogLevel.PROGRESS, Util.status(progress) + " - " + iterations, progress.getTask(), progress
						.status()));
			}

			if (debug) {
				generate(weightMatrix, Util.centroid(whitenedData).getColumnPackedCopy(), "w_" + (iterations + 1));
			}
		}
	}

	/**
	 * Returns the weight matrix after symmetric orthogonalzation.
	 * 
	 * @return the weight matrix after symmetric orthogonalzation
	 */
	private Matrix symmetricOrthogonalizationOfWeightMatrix() {
		Matrix W = weightMatrix.transpose();
		EigenvalueDecomposition decomp = new EigenvalueDecomposition(W.times(W.transpose()));
		Matrix E = decomp.getV();
		Matrix D = decomp.getD();
		for (int i = 0; i < D.getRowDimensionality(); i++) {
			D.set(i, i, 1.0 / Math.sqrt(D.get(i, i)));
		}

		W = E.times(D).times(E.transpose()).times(W);
		return W.transpose();
	}

	/**
	 * Updates the weight vector w_p according to the FastICA algorithm.
	 * 
	 * @param w_p
	 *            the weight vector to be updated
	 * @return the new value of w_p
	 */
	private Vector updateWeight(Vector w_p) {
		int n = whitenedData.getColumnDimensionality();
		int d = whitenedData.getRowDimensionality();

		// E(z*(g(w_p*z))
		Vector E_zg = new Vector(d);
		// E(g'(w_p*z))
		double E_gd = 0.0;

		for (int i = 0; i < n; i++) {
			Vector z = whitenedData.getColumnVector(i);
			// w_p * z
			double wz = w_p.scalarProduct(z);
			// g(w_p * z)
			double g = contrastFunction.function(wz);
			// g'(w_p * z)
			double gd = contrastFunction.derivative(wz);

			E_zg = E_zg.plus(z.times(g));
			E_gd += gd;
		}

		// E(z*(g(w_p*z))
		E_zg = E_zg.times(1.0 / n);
		// E(g'(w_p*z)) * w_p
		E_gd /= n;
		Vector E_gdw = w_p.times(E_gd);

		// w_p = E_zg - E_gd * w_p
		w_p = E_zg.minus(E_gdw);
		return w_p;
	}

	/**
	 * Performs in a preprocessing step a pca on the data and afterwards the
	 * data whitening.
	 * 
	 * @param database
	 *            the database storing the vector objects
	 */
	private void preprocessAndWhitenData(Database<RealVector> database) {
		// center data
		inputData = inputMatrix(database);
		centroid = Util.centroid(inputData);
		centeredData = new Matrix(inputData.getRowDimensionality(), inputData.getColumnDimensionality());
		for (int i = 0; i < inputData.getColumnDimensionality(); i++) {
			centeredData.setColumn(i, inputData.getColumnVector(i).minus(centroid));
		}

		// whiten data
		Matrix cov = Util.covarianceMatrix(centeredData);
		EigenvalueDecomposition evd = cov.eig();
		// eigen vectors
		Matrix E = evd.getV();
		// eigenvalues ^-0.5
		Matrix D_inv_sqrt = evd.getD().copy();
		for (int i = 0; i < D_inv_sqrt.getRowDimensionality(); i++) {
			D_inv_sqrt.set(i, i, 1.0 / Math.sqrt(D_inv_sqrt.get(i, i)));
		}
		// eigenvalue ^1/2
		Matrix D_sqrt = evd.getD().copy();
		for (int i = 0; i < D_sqrt.getRowDimensionality(); i++) {
			D_sqrt.set(i, i, Math.sqrt(D_sqrt.get(i, i)));
		}

		whiteningMatrix = D_inv_sqrt.times(E.transpose());
		dewhiteningMatrix = E.times(D_sqrt);

		// whiteningMatrix = E.times(D_inv_sqrt.times(E.transpose()));
		// dewhiteningMatrix = E.times(D_sqrt).times(E.transpose());

		whitenedData = whiteningMatrix.times(centeredData);

		if (debug) {
			StringBuffer msg = new StringBuffer();
			msg.append("\ninput data X: " + inputData.dimensionInfo());
			msg.append("\n" + inputData);
			msg.append("\ncentered data: " + centeredData.dimensionInfo());
			msg.append("\n" + centeredData);
			msg.append("\nWHITENING MATRIX: " + whiteningMatrix.dimensionInfo());
			msg.append("\n" + whiteningMatrix.toString(NF));
			msg.append("\nDEWHITENING MATRIX: " + dewhiteningMatrix.dimensionInfo());
			msg.append("\n" + dewhiteningMatrix.toString(NF));
			debugFine(msg.toString());
			output(inputData.transpose(), "x0");
			output(centeredData.transpose(), "x1");
			output(whitenedData.transpose(), "x2");
		}
	}

	/**
	 * Determines the input matrix from the specified database, the objects are
	 * columnvectors.
	 * 
	 * @param database
	 *            the database containing the objects
	 * @return a matrix consisting of the objects of the specified database as
	 *         column vectors
	 */
	private Matrix inputMatrix(Database<RealVector> database) {
		int dim = database.dimensionality();
		double[][] input = new double[database.size()][dim];

		int i = 0;
		for (Iterator<Integer> it = database.iterator(); it.hasNext(); i++) {
			RealVector o = database.get(it.next());
			for (int d = 1; d <= dim; d++) {
				input[i][d - 1] = o.getValue(d).doubleValue();
			}
		}

		return new Matrix(input).transpose();
	}

	/**
	 * Returns true, if the convergence criterion for weighting vector wp is
	 * reached.
	 * 
	 * @param wp_old
	 *            the old value of wp
	 * @param wp_new
	 *            the new value of wp
	 * @return true, if the scalar product between wp_old and wp_new is less
	 *         than or equal to 1-epsilon
	 */
	private boolean isVectorConverged(Vector wp_old, Vector wp_new) {
		double scalar = Math.abs(wp_old.scalarProduct(wp_new));
		return scalar >= (1 - epsilon) && scalar <= (1 + epsilon);
	}

	/**
	 * Returns true, if the convergence criterion for weighting matrix w is
	 * reached.
	 * 
	 * @param w_old
	 *            the old value of w
	 * @param w_new
	 *            the new value of w
	 * @return true, if the convergence criterion for each column vector is
	 *         reached.
	 */
	private boolean isMatrixConverged(Matrix w_old, Matrix w_new) {
		for (int p = 0; p < w_old.getColumnDimensionality(); p++) {
			Vector wp_old = w_old.getColumnVector(p);
			Vector wp_new = w_new.getColumnVector(p);
			if (!isVectorConverged(wp_old, wp_new))
				return false;
		}
		return true;
	}

	/**
	 * Output method for a matrix for debuging purposes.
	 * 
	 * @param m
	 *            the matrix to be written
	 * @param name
	 *            the file name
	 */
	private void output(Matrix m, String name) {
		try {
			PrintStream printStream = new PrintStream(new FileOutputStream(name));
			printStream.println("# " + m.dimensionInfo());
			for (int i = 0; i < m.getRowDimensionality(); i++) {
				for (int j = 0; j < m.getColumnDimensionality(); j++) {
					printStream.print(m.get(i, j));
					printStream.print(" ");
				}
				printStream.println("");
			}
			printStream.flush();
			printStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Generates feature vectors belonging to a specified hyperplane for
	 * debugging purposes and writes them to the specified file.
	 * 
	 * @param m
	 *            the basis of the hyperplane
	 * @param p
	 *            the model point of the hyperplane
	 * @param name
	 *            the file name
	 */
	private void generate(Matrix m, double[] p, String name) {
		double[] min = new double[p.length];
		double[] max = new double[p.length];

		Arrays.fill(min, -100);
		Arrays.fill(max, 100);

		File file = new File(name);
		if (file.exists())
			file.delete();

		for (int i = 0; i < m.getColumnDimensionality(); i++)
			ICADataGenerator.runGenerator(100, p, new double[][] { m.getColumnVector(i).getColumnPackedCopy() }, name + i, min, max, 0,
					name);
	}
}
