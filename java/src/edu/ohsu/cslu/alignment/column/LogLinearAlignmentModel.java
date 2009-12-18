package edu.ohsu.cslu.alignment.column;

import java.io.BufferedReader;
import java.io.IOException;

import edu.ohsu.cslu.alignment.CharVocabulary;
import edu.ohsu.cslu.alignment.SubstitutionAlignmentModel;
import edu.ohsu.cslu.common.LogLinearMappedSequence;
import edu.ohsu.cslu.common.Vocabulary;
import edu.ohsu.cslu.datastructs.vectors.BitVector;
import edu.ohsu.cslu.datastructs.vectors.FloatVector;
import edu.ohsu.cslu.datastructs.vectors.IntVector;
import edu.ohsu.cslu.datastructs.vectors.NumericVector;
import edu.ohsu.cslu.datastructs.vectors.SparseBitVector;
import edu.ohsu.cslu.datastructs.vectors.Vector;
import edu.ohsu.cslu.util.Strings;

/**
 * Implements {@link ColumnAlignmentModel} using a {@link BitVector} and the perceptron algorithm
 * 
 * @author Aaron Dunlop
 * @since Mar 24, 2009
 * 
 * @version $Revision$ $Date$ $Author$
 */
public class LogLinearAlignmentModel implements ColumnAlignmentModel
{
    protected final int MAX_TOSTRING_LENGTH = 256;

    private final FloatVector[] costVectors;
    private final Vocabulary vocabulary;
    private final NumericVector columnInsertionCostVector;

    public LogLinearAlignmentModel(FloatVector[] costVectors, Vocabulary vocabulary,
        NumericVector columnInsertionCostVector)
    {
        this.costVectors = costVectors;
        this.vocabulary = vocabulary;
        this.columnInsertionCostVector = columnInsertionCostVector;
    }

    public LogLinearAlignmentModel(final java.io.Reader trainingData, final CharVocabulary vocabulary,
        boolean ignoreLabelLines, NumericVector columnInsertionCostVector) throws IOException
    {
        this(trainingData, vocabulary, null, ignoreLabelLines, columnInsertionCostVector);
    }

    public LogLinearAlignmentModel(final java.io.Reader trainingData, final CharVocabulary vocabulary,
        final Vector laplacePseudoCounts, final boolean ignoreLabelLines, final NumericVector columnInsertionCostVector)
        throws IOException
    {
        this.vocabulary = vocabulary;
        this.columnInsertionCostVector = columnInsertionCostVector;

        BufferedReader br = new BufferedReader(trainingData);

        String line = br.readLine();
        if (ignoreLabelLines)
        {
            // Discard label line
            line = br.readLine();
        }

        final int features = vocabulary.size();
        final int columns = line.length();

        Vector[] counts = new Vector[columns];
        for (int j = 0; j < columns; j++)
        {
            if (laplacePseudoCounts != null)
            {
                counts[j] = new IntVector(vocabulary.size()).add(laplacePseudoCounts);
            }
            else
            {
                counts[j] = new IntVector(vocabulary.size());
            }
        }

        countLine(line, columns, counts);
        float totalCount = 1 + (laplacePseudoCounts != null ? laplacePseudoCounts.sum() : 0);

        for (line = br.readLine(); line != null; line = br.readLine())
        {
            if (ignoreLabelLines)
            {
                // Discard label line
                line = br.readLine();
            }
            countLine(line, columns, counts);
            totalCount++;
        }
        trainingData.close();

        costVectors = new FloatVector[columns];
        for (int j = 0; j < columns; j++)
        {
            costVectors[j] = new FloatVector(vocabulary.size());
            for (int i = 0; i < features; i++)
            {
                costVectors[j].set(i, (float) -Math.log(counts[j].getFloat(i) / totalCount));
            }
        }
    }

    private void countLine(String sequence, final int columns, Vector[] counts)
    {
        for (int j = 0; j < columns; j++)
        {
            final int feature = ((CharVocabulary) vocabulary).mapCharacter(sequence.charAt(j));
            counts[j].set(feature, counts[j].getFloat(feature) + 1);
        }
    }

    @Override
    public float columnInsertionCost(Vector featureVector)
    {
        return featureVector.dotProduct(columnInsertionCostVector);
    }

    @Override
    public int columnCount()
    {
        return costVectors.length;
    }

    @Override
    public float cost(Vector featureVector, int column)
    {
        return featureVector.dotProduct(costVectors[column]);
    }

    @Override
    public float cost(Vector featureVector, int column, int[] featureIndices)
    {
        // TODO: Do we need this method for a Perceptron aligner? And if so, what should its
        // semantics be?
        return cost(featureVector, column);
    }

    @Override
    public int featureCount()
    {
        return costVectors[0].length();
    }

    @Override
    public Vocabulary[] vocabularies()
    {
        return new Vocabulary[] {vocabulary};
    }

    @Override
    public Vector gapVector()
    {
        return new SparseBitVector(new int[] {SubstitutionAlignmentModel.GAP_INDEX}, false);
    }

    @Override
    public String toString()
    {
        final int columns = columnCount();
        if (columns > MAX_TOSTRING_LENGTH)
        {
            return "Maximum length exceeded";
        }

        int maxTokenLength = 0;
        for (int i = 0; i < vocabulary.size(); i++)
        {
            final int tokenLength = vocabulary.map(i).length();
            if (tokenLength > maxTokenLength)
            {
                maxTokenLength = tokenLength;
            }
        }

        StringBuffer sb = new StringBuffer(1024);

        sb.append(String.format("%" + maxTokenLength + "s ", ""));
        for (int i = 0; i < columns; i++)
        {
            sb.append(String.format("%6d ", i));
        }
        sb.append('\n');
        sb.append(Strings.fill('-', columns * 6 + 3));
        sb.append('\n');

        for (int i = 0; i < vocabulary.size(); i++)
        {
            sb.append(String.format("%" + maxTokenLength + "s | ", vocabulary.map(i)));
            for (int j = 0; j < columns; j++)
            {
                float p = costVectors[j].getFloat(i);
                sb.append(Float.isInfinite(p) ? "  Inf " : String.format("%6.2f ", p));
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    @Override
    public float costOfInsertingAGapIntoThisAlignmentModel(final Vector featureVector)
    {
        // Auto-generated method stub
        return 0;
    }

    /**
     * Update model weights using the perceptron algorithm.
     * 
     * @param trainingSequence The positive example to learn from (for negative examples, specify a
     *            negative training rate).
     * @param trainingRate Positive (for positive examples) or negative (for negative examples)
     *            amount by which to update model weights.
     */
    public void perceptronUpdate(final LogLinearMappedSequence trainingSequence, final float trainingRate)
    {
        for (int i = 0; i < trainingSequence.length(); i++)
        {
            costVectors[i].perceptronUpdate((SparseBitVector) trainingSequence.elementAt(i), trainingRate);
        }
    }

    public LogLinearAlignmentModel insertGaps(final int[] gapIndices, final float minWeight, final float maxWeight,
        final NumericVector newColumnInsertionCostVector)
    {
        if (gapIndices.length == 0)
        {
            return this;
        }

        final int gaps = gapIndices.length;
        final int newLength = columnCount() + gaps;

        final FloatVector[] newCostVectors = new FloatVector[newLength];
        final FloatVector[] gapCosts = new FloatVector[gaps];
        for (int i = 0; i < gapCosts.length; i++)
        {
            gapCosts[i] = new FloatVector(vocabulary.size(), minWeight, maxWeight);
        }
        edu.ohsu.cslu.util.Arrays.insertGaps(costVectors, gapIndices, newCostVectors, gapCosts);

        return new LogLinearAlignmentModel(newCostVectors, vocabulary, newColumnInsertionCostVector != null
            ? newColumnInsertionCostVector : columnInsertionCostVector);
    }
}
