package edu.ohsu.cslu.datastructs.matrices;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringWriter;

import org.junit.Test;

/**
 * Unit tests shared by all matrix test classes
 * 
 * @author Aaron Dunlop
 * @since Nov 3, 2008
 * 
 * @version $Revision$ $Date$ $Author$
 */
public abstract class MatrixTestCase {

    protected String stringSampleMatrix;
    protected Matrix sampleMatrix;

    protected String stringSymmetricMatrix;
    protected Matrix symmetricMatrix;

    protected String stringSampleMatrix2;
    protected Matrix sampleMatrix2;

    protected String stringSymmetricMatrix2;
    protected Matrix symmetricMatrix2;

    protected Class<? extends Matrix> matrixClass;

    protected abstract Matrix create(float[][] array, boolean symmetric);

    protected abstract String matrixType();

    /**
     * Tests deserializing a matrix using a Reader
     * 
     * @throws Exception
     *             if something bad happens
     */
    @Test
    public void testReadfromReader() throws Exception {
        final Matrix m1 = Matrix.Factory.read(stringSampleMatrix);
        assertEquals(matrixClass, m1.getClass());
        assertEquals(3, m1.rows());
        assertEquals(4, m1.columns());
        assertEquals(11.11f, m1.getFloat(0, 0), .0001f);
        assertEquals(22.22f, m1.getFloat(0, 1), .0001f);
        assertEquals(66.66f, m1.getFloat(1, 1), .0001f);
        assertEquals(77.77f, m1.getFloat(1, 2), .0001f);
        assertEquals(11.11f, m1.getFloat(2, 2), .0001f);
        assertEquals(12.11f, m1.getFloat(2, 3), .0001f);

        final Matrix m2 = Matrix.Factory.read(stringSymmetricMatrix);
        assertEquals(matrixClass, m2.getClass());
        assertEquals("Wrong number of rows", 5, m2.rows());
        assertEquals("Wrong number of columns", 5, m2.columns());
        assertEquals("Wrong value", 0, m2.getFloat(0, 0), .0001f);
        assertEquals("Wrong value", 11.11f, m2.getFloat(1, 0), .0001f);
        assertEquals("Wrong value", 66.66f, m2.getFloat(3, 0), .0001f);
        assertEquals("Wrong value", 77.77f, m2.getFloat(3, 1), .0001f);
        assertEquals("Wrong value", 99.99f, m2.getFloat(3, 3), .0001f);
        assertEquals("Wrong value", 14.44f, m2.getFloat(4, 4), .0001f);
    }

    /**
     * Tests serializing a matrix to a Writer
     * 
     * @throws Exception
     *             if something bad happens
     */
    @Test
    public void testWriteToWriter() throws Exception {
        StringWriter writer = new StringWriter();
        sampleMatrix.write(writer);
        assertEquals(stringSampleMatrix, writer.toString());

        writer = new StringWriter();
        symmetricMatrix.write(writer);
        assertEquals(stringSymmetricMatrix, writer.toString());

        // Some matrix classes don't really need additional tests with sampleMatrix2 or
        // symmetricMatrix2
        if (sampleMatrix2 != null) {
            writer = new StringWriter();
            sampleMatrix2.write(writer);
            assertEquals(stringSampleMatrix2, writer.toString());
        }

        if (symmetricMatrix2 != null) {
            writer = new StringWriter();
            symmetricMatrix2.write(writer);
            assertEquals(stringSymmetricMatrix2, writer.toString());
        }
    }

    @Test
    public void testDimensions() throws Exception {
        assertEquals("Wrong number of rows", 3, sampleMatrix.rows());
        assertEquals("Wrong number of columns", 4, sampleMatrix.columns());

        assertEquals("Wrong number of rows", 5, symmetricMatrix.rows());
        assertEquals("Wrong number of columns", 5, symmetricMatrix.columns());
    }

    /**
     * Tests 'getInt', including reflection across the diagonal in symmetric matrices.
     * 
     * @throws Exception
     *             if something bad happens
     */
    @Test
    public void testGetInt() throws Exception {
        assertEquals("Wrong value", 11, sampleMatrix.getInt(0, 0));
        assertEquals("Wrong value", 22, sampleMatrix.getInt(0, 1));
        assertEquals("Wrong value", 56, sampleMatrix.getInt(1, 0));
        assertEquals("Wrong value", 89, sampleMatrix.getInt(1, 3));
        assertEquals("Wrong value", 100, sampleMatrix.getInt(2, 0));
        assertEquals("Wrong value", 12, sampleMatrix.getInt(2, 3));

        assertEquals("Wrong value", 0, symmetricMatrix.getInt(0, 0));
        assertEquals("Wrong value", 11, symmetricMatrix.getInt(1, 0));
        assertEquals("Wrong value", 67, symmetricMatrix.getInt(3, 0));
        assertEquals("Wrong value", 78, symmetricMatrix.getInt(3, 1));
        assertEquals("Wrong value", 100, symmetricMatrix.getInt(3, 3));
        assertEquals("Wrong value", 14, symmetricMatrix.getInt(4, 4));

        // And a couple values that are out of the storage area, but should be reflected about the
        // diagonal
        assertEquals("Wrong value", 67, symmetricMatrix.getInt(0, 3));
        assertEquals("Wrong value", 78, symmetricMatrix.getInt(1, 3));
        assertEquals("Wrong value", 89, symmetricMatrix.getInt(2, 3));
    }

    /**
     * Tests setting matrix elements
     * 
     * @throws Exception
     *             if something bad happens
     */
    @Test
    public abstract void testSet() throws Exception;

    /**
     * Tests min(), intMin(), argMin(), and rowArgMin() methods
     * 
     * @throws Exception
     *             if something bad happens
     */
    @Test
    public void testMin() throws Exception {
        assertEquals(10f, sampleMatrix.min(), .01f);
        assertEquals(10, sampleMatrix.intMin());
        assertArrayEquals(new int[] { 2, 1 }, sampleMatrix.argMin());

        sampleMatrix.set(1, 1, -3.0f);
        sampleMatrix.set(2, 3, -4.0f);
        assertEquals(-4f, sampleMatrix.min(), .01f);
        assertEquals(-4, sampleMatrix.intMin());
        assertArrayEquals(new int[] { 2, 3 }, sampleMatrix.argMin());

        assertEquals(1, sampleMatrix.rowArgMin(1));
        assertEquals(3, sampleMatrix.rowArgMin(2));

        assertEquals(0.0f, symmetricMatrix.min(), .01f);
        assertEquals(0, symmetricMatrix.intMin());
        assertArrayEquals(new int[] { 0, 0 }, symmetricMatrix.argMin());

        symmetricMatrix.set(2, 3, -5);
        assertEquals(-5.0f, symmetricMatrix.min(), .01f);
        assertEquals(-5, symmetricMatrix.intMin());
        assertArrayEquals(new int[] { 3, 2 }, symmetricMatrix.argMin());

        assertEquals(3, symmetricMatrix.rowArgMin(2));
        assertEquals(0, symmetricMatrix.rowArgMin(4));
    }

    /**
     * Tests max(), intMax(), argMax(), and rowArgMax() methods
     * 
     * @throws Exception
     *             if something bad happens
     */
    @Test
    public void testMax() throws Exception {
        assertEquals(100, sampleMatrix.intMax());
        assertArrayEquals(new int[] { 2, 0 }, sampleMatrix.argMax());

        sampleMatrix.set(1, 1, 125f);
        sampleMatrix.set(2, 1, 126f);
        assertEquals(126f, sampleMatrix.max(), .01f);
        assertEquals(126, sampleMatrix.intMax());
        assertArrayEquals(new int[] { 2, 1 }, sampleMatrix.argMax());

        assertEquals(1, sampleMatrix.rowArgMax(1));
        assertEquals(1, sampleMatrix.rowArgMax(2));

        assertEquals(100, symmetricMatrix.intMax());
        assertArrayEquals(new int[] { 3, 3 }, symmetricMatrix.argMax());

        symmetricMatrix.set(2, 3, 125f);
        assertEquals(125f, symmetricMatrix.max(), .01f);
        assertEquals(125, symmetricMatrix.intMax());
        assertArrayEquals(new int[] { 3, 2 }, symmetricMatrix.argMax());

        assertEquals(3, symmetricMatrix.rowArgMax(2));
        assertEquals(4, symmetricMatrix.rowArgMax(4));
    }

    /**
     * Tests scalar addition
     * 
     * @throws Exception
     *             if something bad happens
     */
    public abstract void testScalarAdd() throws Exception;

    /**
     * Tests scalar multiplication
     * 
     * @throws Exception
     *             if something bad happens
     */
    @Test
    public abstract void testScalarMultiply() throws Exception;

    /**
     * Tests matrix/vector multiplication
     * 
     * @throws Exception
     *             if something bad happens
     */
    public abstract void testVectorMultiply() throws Exception;

    /**
     * Tests {@link Matrix#infinity()} method
     * 
     * @throws Exception
     *             if something bad happens
     */
    @Test
    public abstract void testInfinity() throws Exception;

    /**
     * Tests {@link Matrix#negativeInfinity()} method
     * 
     * @throws Exception
     *             if something bad happens
     */
    @Test
    public abstract void testNegativeInfinity() throws Exception;

    /**
     * Tests transposition of a matrix.
     * 
     * @throws Exception
     *             if something bad happens
     */
    @Test
    public void testTranspose() throws Exception {
        Matrix transposed = sampleMatrix.transpose();
        assertEquals(matrixClass, transposed.getClass());
        assertEquals("Wrong number of rows", 4, transposed.rows());
        assertEquals("Wrong number of columns", 3, transposed.columns());
        assertEquals("Wrong value", 11, transposed.getInt(0, 0));
        assertEquals("Wrong value", 44, transposed.getInt(3, 0));
        assertEquals("Wrong value", 100, transposed.getInt(0, 2));
        assertEquals("Wrong value", 78, transposed.getInt(2, 1));
        assertEquals("Wrong value", 12, transposed.getInt(3, 2));

        transposed = symmetricMatrix.transpose();
        assertEquals(matrixClass, transposed.getClass());
        assertEquals(5, transposed.rows());
        assertEquals(5, transposed.columns());
        assertEquals(0, transposed.getInt(0, 0));
        assertEquals(11, transposed.getInt(1, 0));
        assertEquals(67, transposed.getInt(3, 0));
        assertEquals(78, transposed.getInt(3, 1));
        assertEquals(100, transposed.getInt(3, 3));
        assertEquals(14, transposed.getInt(4, 4));
    }

    @Test
    public void testIsSquare() throws Exception {
        assertFalse(sampleMatrix.isSquare());
        assertTrue(symmetricMatrix.isSquare());

        final Matrix squareMatrix = create(new float[][] { { 1, 2 }, { 3, 4 } }, false);
        assertTrue(squareMatrix.isSquare());
    }

    /**
     * Tests Java serialization and deserialization of matrices
     * 
     * @throws Exception
     *             if something bad happens
     */
    @Test
    public void testSerialize() throws Exception {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final ObjectOutputStream oos = new ObjectOutputStream(bos);

        oos.writeObject(sampleMatrix);

        final ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        final Matrix m = (Matrix) ois.readObject();
        assertEquals(stringSampleMatrix, m.toString());
    }
}
