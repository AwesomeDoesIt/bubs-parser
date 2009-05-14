package edu.ohsu.cslu.datastructs.vectors;

import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.io.IOException;
import java.io.Writer;

/**
 * Implementation of the {@link BitVector} interface which stores the indices of populated bits in
 * an {@link IntSet}.
 * 
 * This class is generally useful to store binary feature vectors in which a few of the bits will be
 * populated - if a large number of bits are likely to be populated, {@link PackedBitVector} will
 * likely be more efficient.
 * 
 * @author Aaron Dunlop
 * @since Sep 11, 2008
 * 
 *        $Id$
 */
public class SparseBitVector extends BaseVector implements BitVector
{
    private final IntSet intSet;

    public SparseBitVector()
    {
        super(0);
        intSet = new IntOpenHashSet();
    }

    /**
     * Constructs a {@link SparseBitVector} from an integer array. Note that the semantics of this
     * constructor are different from those of most other {@link Vector} constructors with the same
     * signature - the int values contained in the parameter are themselves populated, whereas most
     * other constructors populate the _indices_ of the array which contain non-zero values.
     * 
     * @param array The vector indices to populate
     */
    public SparseBitVector(final int[] array)
    {
        super(0);
        intSet = new IntOpenHashSet(array.length);

        for (int i = 0; i < array.length; i++)
        {
            add(array[i]);
        }
    }

    @Override
    public Vector elementwiseMultiply(Vector v)
    {
        if (!(v instanceof BitVector))
        {
            return super.elementwiseMultiply(v);
        }

        // If we're multiplying two SparseBitVector instances, iterate through the smaller one.
        if (v instanceof SparseBitVector && ((SparseBitVector) v).intSet.size() < intSet.size())
        {
            return ((SparseBitVector) v).elementwiseMultiply(this);
        }

        SparseBitVector newVector = new SparseBitVector();
        for (int i : intSet)
        {
            if (v.getBoolean(i))
            {
                newVector.add(i);
            }
        }

        return newVector;
    }

    @Override
    public void add(int toAdd)
    {
        intSet.add(toAdd);
        if ((toAdd + 1) > length)
        {
            length = toAdd + 1;
        }
    }

    @Override
    public void addAll(int[] toAdd)
    {
        for (int i : toAdd)
        {
            intSet.add(i);
        }
    }

    @Override
    public void addAll(IntSet toAdd)
    {
        intSet.addAll(toAdd);
        length = length();
    }

    @Override
    public boolean contains(int i)
    {
        return intSet.contains(i);
    }

    @Override
    public boolean remove(int toRemove)
    {
        final boolean result = intSet.remove(toRemove);
        length = length();
        return result;
    }

    @Override
    public void removeAll(int[] toRemove)
    {
        for (int i : toRemove)
        {
            intSet.remove(i);
        }
        length = length();
    }

    @Override
    public void removeAll(IntSet toRemove)
    {
        intSet.removeAll(toRemove);
        length = length();
    }

    @Override
    public int argMax()
    {
        // If no index is populated, return 0
        if (intSet.size() == 0)
        {
            return 0;
        }

        // Return the lowest populated index
        int minSetIndex = Integer.MAX_VALUE;
        for (int i : intSet)
        {
            if (i < minSetIndex)
            {
                minSetIndex = i;
            }
        }
        return minSetIndex;
    }

    @Override
    public int argMin()
    {
        // If no index is populated, return 0
        if (intSet.size() == 0)
        {
            return 0;
        }

        for (int i = 0; i < length; i++)
        {
            if (!contains(i))
            {
                return i;
            }
        }
        return 0;
    }

    @Override
    public float dotProduct(Vector v)
    {
        try
        {
            float dotProduct = 0f;
            for (int i : intSet)
            {
                dotProduct += v.getFloat(i);
            }
            return dotProduct;
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            throw new IllegalArgumentException("Vector length mismatch");
        }
    }

    @Override
    public boolean getBoolean(int i)
    {
        return intSet.contains(i);
    }

    @Override
    public float getFloat(int i)
    {
        return intSet.contains(i) ? 1f : 0f;
    }

    @Override
    public int getInt(int i)
    {
        return intSet.contains(i) ? 1 : 0;
    }

    @Override
    public float infinity()
    {
        return 1;
    }

    @Override
    public int intMax()
    {
        // If any indices are populated, the maximum value is 1; otherwise, 0
        return intSet.size() > 0 ? 1 : 0;
    }

    @Override
    public int intMin()
    {
        // If all indices are populated, the minimum value is 1; otherwise, 0.
        return length == intSet.size() ? 1 : 0;
    }

    @Override
    public int length()
    {
        // If no index is populated, return 0
        if (intSet.size() == 0)
        {
            return 0;
        }

        // Return the highest populated index + 1
        int maxSetIndex = 0;
        for (int i : intSet)
        {
            if (i > maxSetIndex)
            {
                maxSetIndex = i;
            }
        }
        return maxSetIndex + 1;
    }

    @Override
    public float max()
    {
        return intMax();
    }

    @Override
    public float min()
    {
        return intMin();
    }

    @Override
    public float negativeInfinity()
    {
        return 0;
    }

    @Override
    public NumericVector scalarMultiply(float multiplier)
    {
        NumericVector v = new FloatVector(length);
        for (int i : intSet)
        {
            v.set(i, multiplier);
        }
        return v;
    }

    @Override
    public NumericVector scalarMultiply(int multiplier)
    {
        NumericVector v = createIntVector();
        for (int i : intSet)
        {
            v.set(i, multiplier);
        }
        return v;
    }

    @Override
    public void set(int i, int value)
    {
        set(i, value != 0);
    }

    @Override
    public void set(int i, float value)
    {
        set(i, value != 0);
    }

    @Override
    public void set(int i, boolean value)
    {
        if (value)
        {
            add(i);
        }
        else
        {
            remove(i);
        }
    }

    @Override
    public void set(int i, String newValue)
    {
        try
        {
            set(i, Integer.parseInt(newValue));
        }
        catch (NumberFormatException e)
        {
            set(i, Boolean.parseBoolean(newValue));
        }
    }

    @Override
    public Vector subVector(int i0, int i1)
    {
        SparseBitVector newVector = new SparseBitVector();
        for (int i = i0; i <= i1; i++)
        {
            if (contains(i))
            {
                newVector.add(i - i0);
            }
        }
        return newVector;
    }

    @Override
    public BitVector intersection(BitVector v)
    {
        return (BitVector) elementwiseMultiply(v);
    }

    @Override
    public float sum()
    {
        return intSet.size();
    }

    @Override
    public void write(Writer writer) throws IOException
    {
        // TODO: This outputs "0 0 1 0 1...". Should we instead output the populated indices (e.g.
        // "2 4...") ?
        write(writer, String.format("vector type=sparse-bit length=%d\n", length()));
    }

    @Override
    public int[] values()
    {
        // Return the values in-order
        return new IntAVLTreeSet(intSet).toIntArray();
    }

    @Override
    public SparseBitVector clone()
    {
        // TODO Sizing the initial IntSet in the copy would make this more efficient
        SparseBitVector newVector = new SparseBitVector();
        for (int i : intSet)
        {
            newVector.add(i);
        }
        return newVector;
    }

    /**
     * Exposes the internal {@link IntSet} for use by other {@link Vector} implementations within
     * the vector package.
     * 
     * @return IntSet
     */
    IntSet intSet()
    {
        return intSet;
    }
}
