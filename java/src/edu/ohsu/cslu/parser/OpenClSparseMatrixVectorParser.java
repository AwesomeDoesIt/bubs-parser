package edu.ohsu.cslu.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import com.nativelibs4java.opencl.CLContext;
import com.nativelibs4java.opencl.CLFloatBuffer;
import com.nativelibs4java.opencl.CLIntBuffer;
import com.nativelibs4java.opencl.CLKernel;
import com.nativelibs4java.opencl.CLMem;
import com.nativelibs4java.opencl.CLProgram;
import com.nativelibs4java.opencl.CLQueue;
import com.nativelibs4java.opencl.CLShortBuffer;

import edu.ohsu.cslu.grammar.CsrSparseMatrixGrammar;
import edu.ohsu.cslu.parser.cellselector.CellSelector;
import static com.nativelibs4java.opencl.JavaCL.createBestContext;
import static com.nativelibs4java.util.NIOUtils.directFloats;
import static com.nativelibs4java.util.NIOUtils.directInts;
import static com.nativelibs4java.util.NIOUtils.directShorts;

/**
 * {@link SparseMatrixVectorParser} which uses a sparse grammar stored in CSR format ({@link CsrSparseMatrixGrammar}) and implements cross-product and SpMV multiplication using
 * OpenCL kernels.
 * 
 * @author Aaron Dunlop
 * @since Feb 11, 2010
 * 
 * @version $Revision$ $Date$ $Author$
 */
public class OpenClSparseMatrixVectorParser extends SparseMatrixVectorParser {

    private final static int LOCAL_WORK_SIZE = 64;

    private final CsrSparseMatrixGrammar csrSparseMatrixGrammar;
    private CLContext context;
    private CLKernel fillFloatKernel;
    private CLKernel binarySpmvKernel;
    private CLKernel unarySpmvKernel;
    private CLKernel crossProductKernel;
    private CLKernel crossProductUnionKernel;
    private CLQueue clQueue;

    // TODO Make these final once we move kernel compilation into the constructor
    private CLIntBuffer clBinaryRuleMatrixRowIndices;
    private CLIntBuffer clBinaryRuleMatrixColumnIndices;
    private CLFloatBuffer clBinaryRuleMatrixProbabilities;

    private CLIntBuffer clUnaryRuleMatrixRowIndices;
    private CLIntBuffer clUnaryRuleMatrixColumnIndices;
    private CLFloatBuffer clUnaryRuleMatrixProbabilities;

    // Temporary chart cell storage
    private CLIntBuffer clChartCellChildren;
    private CLFloatBuffer clChartCellProbabilities;
    private CLShortBuffer clChartCellMidpoints;

    // Cross-product vector
    private CLFloatBuffer clCrossProductProbabilities0;
    private CLShortBuffer clCrossProductMidpoints0;
    private CLFloatBuffer clCrossProductProbabilities1;
    private CLShortBuffer clCrossProductMidpoints1;

    public OpenClSparseMatrixVectorParser(final CsrSparseMatrixGrammar grammar, final CellSelector cellSelector) {
        super(grammar, cellSelector);
        this.csrSparseMatrixGrammar = grammar;

        context = createBestContext();
        clQueue = context.createDefaultQueue();
    }

    @Override
    protected void initParser(final int sentLength) {
        chart = new Chart<OpenClChartCell>(sentLength, OpenClChartCell.class, csrSparseMatrixGrammar);

        totalSpMVTime = 0;
        totalCrossProductTime = 0;

        // TODO Move this to constructor after debugging

        try {
            // Compile OpenCL kernels
            final StringWriter sw = new StringWriter();
            sw.write("#define LEFT_CHILD_SHIFT " + csrSparseMatrixGrammar.leftChildShift + '\n');
            sw.write("#define MASK " + csrSparseMatrixGrammar.mask + '\n');
            final String filename = getClass().getCanonicalName().replace('.', File.separatorChar) + ".cl";
            final BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(filename)));
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                sw.write(line);
                sw.write('\n');
            }
            final CLProgram program = context.createProgram(sw.toString()).build();
            fillFloatKernel = program.createKernel("fillFloat");
            binarySpmvKernel = program.createKernel("binarySpmvMultiply");
            unarySpmvKernel = program.createKernel("unarySpmvMultiply");
            crossProductKernel = program.createKernel("crossProduct");
            crossProductUnionKernel = program.createKernel("crossProductUnion");
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        // Allocate OpenCL-hosted memory for binary rules and copy to the device
        clBinaryRuleMatrixRowIndices = copyToDevice(csrSparseMatrixGrammar.binaryRuleMatrixRowIndices(), CLMem.Usage.Input);
        clBinaryRuleMatrixColumnIndices = copyToDevice(csrSparseMatrixGrammar.binaryRuleMatrixColumnIndices(), CLMem.Usage.Input);
        clBinaryRuleMatrixProbabilities = copyToDevice(csrSparseMatrixGrammar.binaryRuleMatrixProbabilities(), CLMem.Usage.Input);

        // Repeat for unary rules
        clUnaryRuleMatrixRowIndices = copyToDevice(csrSparseMatrixGrammar.unaryRuleMatrixRowIndices(), CLMem.Usage.Input);
        clUnaryRuleMatrixColumnIndices = copyToDevice(csrSparseMatrixGrammar.unaryRuleMatrixColumnIndices(), CLMem.Usage.Input);
        clUnaryRuleMatrixProbabilities = copyToDevice(csrSparseMatrixGrammar.unaryRuleMatrixProbabilities(), CLMem.Usage.Input);

        // Allocate OpenCL-hosted memory for temporary chart cell storage
        clChartCellChildren = context.createIntBuffer(CLMem.Usage.InputOutput, csrSparseMatrixGrammar.numNonTerms());
        clChartCellProbabilities = context.createFloatBuffer(CLMem.Usage.InputOutput, csrSparseMatrixGrammar.numNonTerms());
        clChartCellMidpoints = context.createShortBuffer(CLMem.Usage.InputOutput, csrSparseMatrixGrammar.numNonTerms());

        // And for cross-product storage
        clCrossProductProbabilities0 = context.createFloatBuffer(CLMem.Usage.InputOutput, csrSparseMatrixGrammar.packedArraySize());
        clCrossProductMidpoints0 = context.createShortBuffer(CLMem.Usage.InputOutput, csrSparseMatrixGrammar.packedArraySize());
        clCrossProductProbabilities1 = context.createFloatBuffer(CLMem.Usage.InputOutput, csrSparseMatrixGrammar.packedArraySize());
        clCrossProductMidpoints1 = context.createShortBuffer(CLMem.Usage.InputOutput, csrSparseMatrixGrammar.packedArraySize());
    }

    @Override
    protected void visitCell(final ChartCell cell) {

        final DenseVectorChartCell spvChartCell = (DenseVectorChartCell) cell;
        final short start = (short) cell.start();
        final short end = (short) cell.end();

        final long t0 = System.currentTimeMillis();
        long t1 = t0;
        long crossProductTime = 0;

        long t2;
        long binarySpmvTime = 0;
        // Skip binary grammar intersection for span-1 cells
        if (end - start > 1) {
            // final CrossProductVector crossProductVector = crossProductUnion(start, end);
            internalCrossProductUnion(start, end);

            t1 = System.currentTimeMillis();
            crossProductTime = t1 - t0;

            // Multiply the unioned vector with the grammar matrix and populate the current cell with the
            // vector resulting from the matrix-vector multiplication
            // binarySpmvMultiply(crossProductVector, spvChartCell);

            // Copy cross-product to OpenCL memory
            // copyToDevice(clCrossProductProbabilities0, crossProductVector.probabilities);
            // copyToDevice(clCrossProductMidpoints0, crossProductVector.midpoints);

            internalBinarySpmvMultiply();

            t2 = System.currentTimeMillis();
            binarySpmvTime = t2 - t1;

        } else {
            t2 = System.currentTimeMillis();
            copyToDevice(clChartCellChildren, spvChartCell.children);
            copyToDevice(clChartCellProbabilities, spvChartCell.probabilities);
            copyToDevice(clChartCellMidpoints, spvChartCell.midpoints);
        }

        // Handle unary productions
        // TODO: This only goes through unary rules one time, so it can't create unary chains unless such chains are encoded in the grammar. Iterating a few times would probably
        // work, although it's a big-time hack.
        internalUnarySpmvMultiply(end);
        copyFromDevice(clChartCellChildren, spvChartCell.children);
        copyFromDevice(clChartCellProbabilities, spvChartCell.probabilities);
        copyFromDevice(clChartCellMidpoints, spvChartCell.midpoints);

        final long t3 = System.currentTimeMillis();
        final long unarySpmvTime = t3 - t2;

        spvChartCell.finalizeCell();

        // System.out.format("Visited cell: %2d,%2d (%5d ms). Cross-product: %6d/%6d combinations (%5.0f ms, %4.2f/ms), Multiply: %5d edges (%5.0f ms, %4.2f /ms)\n", start, end, t3
        // - t0, crossProductSize, totalProducts, crossProductTime, crossProductSize / crossProductTime, edges, spmvTime, edges / spmvTime);
        totalCrossProductTime += crossProductTime;
        totalSpMVTime += binarySpmvTime + unarySpmvTime;
    }

    /**
     * Takes the cross-product of all potential child-cell combinations. Unions those cross-products together, saving the maximum probability child combinations. This version
     * copies the cross-product back into main memory.
     * 
     * @param start
     * @param end
     * @return Unioned cross-product
     */
    @Override
    protected CrossProductVector crossProductUnion(final int start, final int end) {

        internalCrossProductUnion(start, end);

        final float[] crossProductProbabilities = new float[csrSparseMatrixGrammar.packedArraySize()];
        final FloatBuffer mappedClCrossProductProbabilities = directFloats(crossProductProbabilities.length, context.getByteOrder());
        clCrossProductProbabilities0.read(clQueue, mappedClCrossProductProbabilities, true);
        mappedClCrossProductProbabilities.get(crossProductProbabilities);

        final short[] crossProductMidpoints = new short[csrSparseMatrixGrammar.packedArraySize()];
        final ShortBuffer mappedClCrossProductMidpoints = directShorts(crossProductMidpoints.length, context.getByteOrder());
        clCrossProductMidpoints0.read(clQueue, mappedClCrossProductMidpoints, true);
        mappedClCrossProductMidpoints.get(crossProductMidpoints);

        int size = 0;
        for (int i = 0; i < crossProductProbabilities.length; i++) {
            if (crossProductProbabilities[i] != Float.NEGATIVE_INFINITY) {
                size++;
            }
        }
        return new CrossProductVector(csrSparseMatrixGrammar, crossProductProbabilities, crossProductMidpoints, size);
    }

    private void internalCrossProductUnion(final int start, final int end) {

        // Fill the buffer with negative infinity
        fillFloatKernel.setArgs(clCrossProductProbabilities0, csrSparseMatrixGrammar.packedArraySize(), Float.NEGATIVE_INFINITY);
        final int globalWorkSize = edu.ohsu.cslu.util.Math.roundUp(csrSparseMatrixGrammar.packedArraySize(), LOCAL_WORK_SIZE);
        fillFloatKernel.enqueueNDRange(clQueue, new int[] { globalWorkSize }, new int[] { LOCAL_WORK_SIZE });
        clQueue.finish();

        short midpoint = (short) (start + 1);

        while (((DenseVectorChartCell) chart.getCell(start, midpoint)).validLeftChildren.length == 0
                || ((DenseVectorChartCell) chart.getCell(midpoint, end)).validRightChildren.length == 0) {
            midpoint++;
        }

        // Compute the cross-product of the first midpoint separately
        internalCrossProduct((DenseVectorChartCell) chart.getCell(start, midpoint), (DenseVectorChartCell) chart.getCell(midpoint, end), clCrossProductProbabilities0,
                clCrossProductMidpoints0);
        // printCrossProduct(copyFromDevice(clCrossProductProbabilities0, csrSparseMatrixGrammar.packedArraySize()));

        // Iterate over all other midpoints, unioning together the cross-product of discovered
        // non-terminals in each left/right child pair
        for (; midpoint <= end - 1; midpoint++) {

            final DenseVectorChartCell leftCell = (DenseVectorChartCell) chart.getCell(start, midpoint);
            final DenseVectorChartCell rightCell = (DenseVectorChartCell) chart.getCell(midpoint, end);

            fillFloatKernel.setArgs(clCrossProductProbabilities1, csrSparseMatrixGrammar.packedArraySize(), Float.NEGATIVE_INFINITY);
            fillFloatKernel.enqueueNDRange(clQueue, new int[] { globalWorkSize }, new int[] { LOCAL_WORK_SIZE });
            clQueue.finish();

            if (leftCell.validLeftChildren.length > 0 && rightCell.validRightChildren.length > 0) {
                internalCrossProduct(leftCell, rightCell, clCrossProductProbabilities1, clCrossProductMidpoints1);

                // printCrossProduct(copyFromDevice(clCrossProductProbabilities1, csrSparseMatrixGrammar.packedArraySize()));

                // Union the new cross-product with the existing cross-product
                crossProductUnionKernel.setArgs(clCrossProductProbabilities0, clCrossProductMidpoints0, clCrossProductProbabilities1, clCrossProductMidpoints1,
                        csrSparseMatrixGrammar.packedArraySize());
                crossProductUnionKernel.enqueueNDRange(clQueue, new int[] { globalWorkSize }, new int[] { LOCAL_WORK_SIZE });
                clQueue.finish();
                // printCrossProduct(copyFromDevice(clCrossProductProbabilities0, csrSparseMatrixGrammar.packedArraySize()));
            }
        }
    }

    private void internalCrossProduct(final DenseVectorChartCell leftCell, final DenseVectorChartCell rightCell, final CLFloatBuffer tmpClCrossProductProbabilities,
            final CLShortBuffer tmpClCrossProductMidpoints) {

        final CLIntBuffer clValidLeftChildren = copyToDevice(leftCell.validLeftChildren, CLMem.Usage.Input);
        final CLFloatBuffer clValidLeftChildrenProbabilities = copyToDevice(leftCell.validLeftChildrenProbabilities, CLMem.Usage.Input);
        final CLShortBuffer clValidRightChildren = copyToDevice(rightCell.validRightChildren, CLMem.Usage.Input);
        final CLFloatBuffer clValidRightChildrenProbabilities = copyToDevice(rightCell.validRightChildrenProbabilities, CLMem.Usage.Input);

        // Bind the arguments of the OpenCL kernel
        crossProductKernel.setArgs(clValidLeftChildren, clValidLeftChildrenProbabilities, leftCell.validLeftChildren.length, clValidRightChildren,
                clValidRightChildrenProbabilities, rightCell.validRightChildren.length, tmpClCrossProductProbabilities, tmpClCrossProductMidpoints, (short) rightCell.start());

        // Call the kernel and wait for results
        final int globalWorkSize = edu.ohsu.cslu.util.Math.roundUp(leftCell.validLeftChildren.length * rightCell.validRightChildren.length, LOCAL_WORK_SIZE);
        crossProductKernel.enqueueNDRange(clQueue, new int[] { globalWorkSize }, new int[] { LOCAL_WORK_SIZE });

        clQueue.finish();
        clValidLeftChildren.release();
        clValidLeftChildrenProbabilities.release();
        clValidRightChildren.release();
        clValidRightChildrenProbabilities.release();
    }

    private void printCrossProduct(final float[] cp) {
        for (int i = 0; i < cp.length; i++) {
            if (cp[i] != Float.NEGATIVE_INFINITY) {
                System.out.format("%d : %.2f\n", i, cp[i]);
            }
        }
        System.out.println();
    }

    /**
     * This version copies the cross-product to device memory and the resulting chart cell back into main memory. Useful for testing, but we can do better if we avoid the repeated
     * copying.
     */
    @Override
    public void binarySpmvMultiply(final CrossProductVector crossProductVector, final DenseVectorChartCell chartCell) {

        // Copy cross-product to OpenCL memory
        copyToDevice(clCrossProductProbabilities0, crossProductVector.probabilities);
        copyToDevice(clCrossProductMidpoints0, crossProductVector.midpoints);

        internalBinarySpmvMultiply();

        copyFromDevice(clChartCellChildren, chartCell.children);
        copyFromDevice(clChartCellProbabilities, chartCell.probabilities);
        copyFromDevice(clChartCellMidpoints, chartCell.midpoints);
    }

    private void internalBinarySpmvMultiply() {

        // Bind the arguments of the OpenCL kernel
        binarySpmvKernel.setArgs(clBinaryRuleMatrixRowIndices, clBinaryRuleMatrixColumnIndices, clBinaryRuleMatrixProbabilities, grammar.numNonTerms(),
                clCrossProductProbabilities0, clCrossProductMidpoints0, clChartCellChildren, clChartCellProbabilities, clChartCellMidpoints);

        // Call the kernel and wait for results
        final int globalWorkSize = edu.ohsu.cslu.util.Math.roundUp(grammar.numNonTerms(), LOCAL_WORK_SIZE);
        binarySpmvKernel.enqueueNDRange(clQueue, new int[] { globalWorkSize }, new int[] { LOCAL_WORK_SIZE });
        clQueue.finish();
    }

    /**
     * This version copies the current cell population to device memory and the results back into main memory. Useful for testing, but we can do better if we avoid the repeated
     * copying.
     */
    @Override
    public void unarySpmvMultiply(final DenseVectorChartCell chartCell) {

        // Copy current chart cell entries to OpenCL memory
        copyToDevice(clChartCellChildren, chartCell.children);
        copyToDevice(clChartCellProbabilities, chartCell.probabilities);
        copyToDevice(clChartCellMidpoints, chartCell.midpoints);

        internalUnarySpmvMultiply((short) chartCell.end());

        copyFromDevice(clChartCellChildren, chartCell.children);
        copyFromDevice(clChartCellProbabilities, chartCell.probabilities);
        copyFromDevice(clChartCellMidpoints, chartCell.midpoints);
    }

    private void internalUnarySpmvMultiply(final short chartCellEnd) {

        // Bind the arguments of the OpenCL kernel
        unarySpmvKernel.setArgs(clUnaryRuleMatrixRowIndices, clUnaryRuleMatrixColumnIndices, clUnaryRuleMatrixProbabilities, grammar.numNonTerms(), clChartCellChildren,
                clChartCellProbabilities, clChartCellMidpoints, chartCellEnd);

        // Call the kernel and wait for results
        final int globalWorkSize = edu.ohsu.cslu.util.Math.roundUp(grammar.numNonTerms(), LOCAL_WORK_SIZE);
        unarySpmvKernel.enqueueNDRange(clQueue, new int[] { globalWorkSize }, new int[] { LOCAL_WORK_SIZE });
        clQueue.finish();
    }

    private CLFloatBuffer copyToDevice(final float[] array, final CLMem.Usage usage) {
        final CLFloatBuffer clFloatBuffer = context.createFloatBuffer(usage, array.length);
        copyToDevice(clFloatBuffer, array);
        return clFloatBuffer;
    }

    private void copyToDevice(final CLFloatBuffer clFloatBuffer, final float[] array) {
        final FloatBuffer mappedInitialArray = clFloatBuffer.map(clQueue, CLMem.MapFlags.Write);
        mappedInitialArray.put(array);
        clFloatBuffer.unmap(clQueue, mappedInitialArray);
    }

    private CLIntBuffer copyToDevice(final int[] array, final CLMem.Usage usage) {
        final CLIntBuffer clIntBuffer = context.createIntBuffer(usage, array.length);
        copyToDevice(clIntBuffer, array);
        return clIntBuffer;
    }

    private void copyToDevice(final CLIntBuffer clIntBuffer, final int[] array) {
        final IntBuffer mappedInitialArray = clIntBuffer.map(clQueue, CLMem.MapFlags.Write);
        mappedInitialArray.put(array);
        clIntBuffer.unmap(clQueue, mappedInitialArray);
    }

    private CLShortBuffer copyToDevice(final short[] array, final CLMem.Usage usage) {
        final CLShortBuffer clShortBuffer = context.createShortBuffer(usage, array.length);
        copyToDevice(clShortBuffer, array);
        return clShortBuffer;
    }

    private void copyToDevice(final CLShortBuffer clShortBuffer, final short[] array) {
        final ShortBuffer mappedInitialArray = clShortBuffer.map(clQueue, CLMem.MapFlags.Write);
        mappedInitialArray.put(array);
        clShortBuffer.unmap(clQueue, mappedInitialArray);
    }

    private float[] copyFromDevice(final CLFloatBuffer clFloatBuffer, final int size) {
        final float[] array = new float[size];
        copyFromDevice(clFloatBuffer, array);
        return array;
    }

    private void copyFromDevice(final CLFloatBuffer clFloatBuffer, final float[] array) {
        final FloatBuffer mappedClBuffer = directFloats(array.length, context.getByteOrder());
        clFloatBuffer.read(clQueue, mappedClBuffer, true);
        mappedClBuffer.get(array);
    }

    private int[] copyFromDevice(final CLIntBuffer clIntBuffer, final int size) {
        final int[] array = new int[size];
        copyFromDevice(clIntBuffer, array);
        return array;
    }

    private void copyFromDevice(final CLIntBuffer clIntBuffer, final int[] array) {
        final IntBuffer mappedClBuffer = directInts(array.length, context.getByteOrder());
        clIntBuffer.read(clQueue, mappedClBuffer, true);
        mappedClBuffer.get(array);
    }

    private short[] copyFromDevice(final CLShortBuffer clShortBuffer, final int size) {
        final short[] array = new short[size];
        copyFromDevice(clShortBuffer, array);
        return array;
    }

    private void copyFromDevice(final CLShortBuffer clShortBuffer, final short[] array) {
        final ShortBuffer mappedClBuffer = directShorts(array.length, context.getByteOrder());
        clShortBuffer.read(clQueue, mappedClBuffer, true);
        mappedClBuffer.get(array);
    }

    public class OpenClChartCell extends DenseVectorChartCell {

        public OpenClChartCell(final int start, final int end, final Chart<OpenClChartCell> chart) {
            super(start, end, chart);
        }

        @Override
        public void finalizeCell() {
            // Count valid left and right children
            numValidLeftChildren = numValidRightChildren = 0;
            for (int nonterminal = 0; nonterminal < sparseMatrixGrammar.numNonTerms(); nonterminal++) {

                if (probabilities[nonterminal] != Float.NEGATIVE_INFINITY) {
                    if (sparseMatrixGrammar.isValidLeftChild(nonterminal)) {
                        numValidLeftChildren++;
                    }
                    if (sparseMatrixGrammar.isValidRightChild(nonterminal)) {
                        numValidRightChildren++;
                    }
                }
            }

            super.finalizeCell();
        }
    }
}