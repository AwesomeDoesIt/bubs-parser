/*
 * Copyright 2010, 2011 Aaron Dunlop and Nathan Bodenstab
 * 
 * This file is part of the BUBS Parser.
 * 
 * The BUBS Parser is free software: you can redistribute it and/or 
 * modify  it under the terms of the GNU Affero General Public License 
 * as published by the Free Software Foundation, either version 3 of 
 * the License, or (at your option) any later version.
 * 
 * The BUBS Parser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with the BUBS Parser. If not, see <http://www.gnu.org/licenses/>
 */
package edu.ohsu.cslu.lela;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

import cltool4j.BaseCommandlineTool;
import cltool4j.BaseLogger;
import cltool4j.GlobalConfigProperties;
import cltool4j.args4j.Option;
import edu.ohsu.cslu.datastructs.narytree.BinaryTree;
import edu.ohsu.cslu.datastructs.narytree.NaryTree;
import edu.ohsu.cslu.datastructs.narytree.NaryTree.Binarization;
import edu.ohsu.cslu.grammar.GrammarFormatType;
import edu.ohsu.cslu.grammar.InsideOutsideCscSparseMatrixGrammar;
import edu.ohsu.cslu.grammar.SparseMatrixGrammar;
import edu.ohsu.cslu.grammar.SparseMatrixGrammar.PerfectIntPairHashPackingFunction;
import edu.ohsu.cslu.grammar.SymbolSet;
import edu.ohsu.cslu.grammar.Tokenizer;
import edu.ohsu.cslu.lela.ProductionListGrammar.NoiseGenerator;
import edu.ohsu.cslu.parser.ParseTask;
import edu.ohsu.cslu.parser.Parser;
import edu.ohsu.cslu.parser.ParserDriver;
import edu.ohsu.cslu.parser.ml.CartesianProductHashSpmlParser;
import edu.ohsu.cslu.util.Arrays;
import edu.ohsu.cslu.util.Evalb.BracketEvaluator;
import edu.ohsu.cslu.util.Evalb.EvalbResult;
import edu.ohsu.cslu.util.Strings;

/**
 * Learns a latent-variable grammar from a training corpus using the approach from Petrov et al., 2006
 * "Learning Accurate, Compact, and Interpretable Tree Annotation"
 * 
 * @author Aaron Dunlop
 */
public class TrainGrammar extends BaseCommandlineTool {

    @Option(name = "-c", aliases = { "--sm-cycles" }, metaVar = "cycles", usage = "Split-merge cycles")
    private int splitMergeCycles = 6;

    @Option(name = "-i", aliases = { "--em-iterations" }, metaVar = "count", usage = "EM iterations per split-merge cycle")
    private int emIterationsPerCycle = 50;

    @Option(name = "-ami", aliases = { "--after-merge" }, metaVar = "count", usage = "EM iterations after merge")
    private int emIterationsAfterMerge = 20;

    @Option(name = "-gd", aliases = { "--grammar-directory" }, required = true, metaVar = "directory", usage = "Output grammar directory. Each merged grammar will be output in .gz format")
    private File outputGrammarDirectory;

    @Option(name = "-gp", aliases = { "--grammar-prefix" }, metaVar = "prefix", usage = "Output grammar file prefix (e.g. 'eng.' produces 'eng.sm1.gr.gz', 'eng.sm2.gr.gz', etc.")
    private String outputGrammarPrefix = "";

    @Option(name = "-gf", aliases = { "--grammar-format" }, metaVar = "format", usage = "Grammar Format; required if binarization is specified")
    GrammarFormatType grammarFormatType = GrammarFormatType.Berkeley;

    @Option(name = "-unk", aliases = { "-rw" }, metaVar = "threshold", usage = "Smooth production probabilities with unknown-word probabilities for rare words")
    private int rareWordThreshold = 20;

    @Option(name = "-ot", aliases = { "--open-class-threshold" }, metaVar = "threshold", usage = "Learn unknown-word probabilities for tags producing at least n words")
    private int openClassPreterminalThreshold = 50;

    @Option(name = "-l", aliases = { "--language" }, metaVar = "language", usage = "Language. Output in grammar file headers.")
    private String language = "english";

    @Option(name = "-b", aliases = { "--binarization" }, metaVar = "type", usage = "Binarization direction.")
    Binarization binarization = Binarization.LEFT;

    @Option(name = "-mrp", metaVar = "probability", usage = "Minimum rule log probability (rules with lower probability are pruned from the grammar)")
    private float minimumRuleProbability = -70f;

    @Option(name = "-noise", metaVar = "noise (0-1)", usage = "Random noise to add to rule probabilities during each split")
    private float noise = 0.01f;

    @Option(name = "-rs", aliases = { "--random-seed" }, metaVar = "seed", usage = "Random seed (default = System.currentTimeMillis())")
    private long randomSeed;

    @Option(name = "-mf", aliases = { "--merge-fraction" }, metaVar = "fraction", usage = "Fraction of new splits to re-merge in each split-merge cycle")
    private float mergeFraction = 0.5f;

    @Option(name = "-ds", aliases = { "--dev-set" }, metaVar = "file", usage = "Dev-set trees. If specified, parse accuracy will be reported after each split-merge cycle")
    private File developmentSet;

    @Option(name = "-pf", aliases = { "--prune-fraction" }, metaVar = "fraction", usage = "Pruning fraction (of non-terminals) for dev-set parsing")
    private float pruneFraction = 0.4f;

    /** Maximum size of a training or development corpus in characters. Currently 20 MB */
    private final static int MAX_CORPUS_SIZE = 20 * 1024 * 1024;

    final ArrayList<NaryTree<String>> goldTrees = new ArrayList<NaryTree<String>>();
    final ArrayList<ConstrainingChart> constrainingCharts = new ArrayList<ConstrainingChart>();

    private NoiseGenerator noiseGenerator;

    @Override
    protected void setup() {

        if (randomSeed == 0) {
            randomSeed = System.currentTimeMillis();
        }

        noiseGenerator = new ProductionListGrammar.RandomNoiseGenerator(noise, randomSeed);

        if (outputGrammarDirectory != null && !outputGrammarDirectory.exists()) {
            outputGrammarDirectory.mkdir();
        }
    }

    @Override
    protected void run() throws IOException {

        train(new BufferedReader(new InputStreamReader(System.in)),
                developmentSet != null ? fileAsBufferedReader(developmentSet) : null);
    }

    @SuppressWarnings("null")
    void train(final BufferedReader trainingCorpusReader, final BufferedReader devCorpusReader) throws IOException {

        trainingCorpusReader.mark(MAX_CORPUS_SIZE);
        devCorpusReader.mark(MAX_CORPUS_SIZE);

        // Induce M0 grammar from training corpus
        BaseLogger.singleton().info("Inducing M0 grammar...");
        final StringCountGrammar scg = new StringCountGrammar(trainingCorpusReader, binarization, grammarFormatType);
        final ProductionListGrammar markov0Grammar = new ProductionListGrammar(scg);
        final Int2IntOpenHashMap corpusWordCounts = scg.wordCounts(markov0Grammar.lexicon);

        BaseLogger.singleton().config("Markov-0 grammar size: " + grammarSummaryString(markov0Grammar));
        ProductionListGrammar plGrammar = markov0Grammar;
        trainingCorpusReader.reset();

        loadGoldTreesAndConstrainingCharts(trainingCorpusReader, plGrammar);
        trainingCorpusReader.close();

        // Run split-merge training cycles
        for (int cycle = 1; cycle <= splitMergeCycles; cycle++) {

            //
            // Split
            //
            BaseLogger.singleton().info(String.format("=== Cycle %d ===", cycle));
            plGrammar = plGrammar.split(noiseGenerator);
            BaseLogger.singleton().config("Split grammar size: " + grammarSummaryString(plGrammar));

            //
            // Train the split grammar with EM
            //
            FractionalCountGrammar finalCountGrammar = null;
            for (int i = 1; i <= emIterationsPerCycle; i++) {
                final EmIterationResult result = emIteration(plGrammar, minimumRuleProbability);
                logEmIteration(result, i);
                plGrammar = result.plGrammar;
                finalCountGrammar = result.fcGrammar;
            }
            BaseLogger.singleton().config("Learned grammar size: " + grammarSummaryString(plGrammar));

            // At verbose log levels, write pre-merge grammar to file
            if (BaseLogger.singleton().isLoggable(Level.FINER)) {
                writeGrammarToFile(String.format("em%d.gr.gz", cycle), plGrammar);
            }

            //
            // Estimate likelihood loss of re-merging and merge least costly splits
            //
            final ConstrainedInsideOutsideGrammar finalSplitGrammar = cscGrammar(plGrammar);
            plGrammar = merge(plGrammar, finalCountGrammar, finalSplitGrammar);

            //
            // TODO Run some more EM iterations on merged grammar - we'll have to 'partially-merge' the grammar,
            // combining rule probabilities, but delay merging the vocabulary until after this EM cycle, so we can still
            // map to the existing ConstrainingChart properly.
            //
            // BaseLogger.singleton().info("Post-merge EM");
            // for (int i = 1; i <= emIterationsAfterMerge; i++) {
            // final EmIterationResult result = emIteration(plGrammar, minimumRuleProbability);
            // logEmIteration(result, i);
            // plGrammar = result.plGrammar;
            // finalCountGrammar = result.fcGrammar;
            // }

            // Add UNK productions
            final ProductionListGrammar grammarWithUnks = finalCountGrammar.toProductionListGrammar(
                    minimumRuleProbability, unkClassMap(finalCountGrammar.lexicon), openClassPreterminalThreshold,
                    corpusWordCounts, rareWordThreshold);

            //
            // TODO Smooth
            //

            // Write merged grammar
            writeGrammarToFile(String.format("sm%d.gr.gz", cycle), grammarWithUnks);

            // Populate constraining charts for the next SM cycle from a Viterbi 1-best parse with the current grammar
            reloadConstrainingCharts(finalSplitGrammar, cscGrammar(plGrammar));

            // Output dev-set parse accuracy
            if (developmentSet != null) {
                parseDevSet(devCorpusReader, cscGrammar(grammarWithUnks));
            }
        }
    }

    private ConstrainedInsideOutsideGrammar cscGrammar(final ProductionListGrammar plGrammar) {
        return new ConstrainedInsideOutsideGrammar(plGrammar, GrammarFormatType.Berkeley,
                PerfectIntPairHashPackingFunction.class);
    }

    private Int2IntOpenHashMap unkClassMap(final SymbolSet<String> lexicon) {
        final Int2IntOpenHashMap unkClassMap = new Int2IntOpenHashMap();
        for (int i = 0; i < lexicon.size(); i++) {
            unkClassMap.put(i, lexicon.addSymbol(Tokenizer.berkeleyGetSignature(lexicon.getSymbol(i), false, lexicon)));
        }
        return unkClassMap;
    }

    final void loadGoldTreesAndConstrainingCharts(final BufferedReader trainingCorpusReader,
            final ProductionListGrammar plGrammar) throws IOException {

        // Convert Markov-0 grammar to CSC format
        final SparseMatrixGrammar cscGrammar0 = new InsideOutsideCscSparseMatrixGrammar(plGrammar.binaryProductions,
                plGrammar.unaryProductions, plGrammar.lexicalProductions, plGrammar.vocabulary, plGrammar.lexicon,
                GrammarFormatType.Berkeley, SparseMatrixGrammar.PerfectIntPairHashPackingFunction.class, true);

        // Load in constraining charts from training corpus
        BaseLogger.singleton().info("Loading gold trees and constraining charts...");

        for (String line = trainingCorpusReader.readLine(); line != null; line = trainingCorpusReader.readLine()) {
            final NaryTree<String> goldTree = NaryTree.read(line, String.class);
            goldTrees.add(goldTree);

            final BinaryTree<String> factoredTree = goldTree.factor(grammarFormatType, binarization);
            constrainingCharts.add(new ConstrainingChart(factoredTree, cscGrammar0));
        }
    }

    /**
     * Parses with a split grammar and replaces the current {@link ConstrainingChart}s with 1-best parses, populated
     * with a merged version of the same grammar.
     * 
     * @param finalSplitGrammar
     * @param mergedGrammar
     */
    final void reloadConstrainingCharts(final ConstrainedInsideOutsideGrammar finalSplitGrammar,
            final ConstrainedInsideOutsideGrammar mergedGrammar) {

        final ParserDriver opts = new ParserDriver();
        opts.cellSelectorModel = ConstrainedCellSelector.MODEL;
        final ConstrainedViterbiParser parser = new ConstrainedViterbiParser(opts, finalSplitGrammar);

        BaseLogger.singleton().info("Reloading constraining charts...");

        // Iterate over the training corpus, parsing and replacing current ConstrainingCharts
        for (int i = 0; i < constrainingCharts.size(); i++) {
            final ConstrainedChart c = parser.parse(constrainingCharts.get(i));
            constrainingCharts.set(i, new ConstrainingChart(c, mergedGrammar, true));
        }
    }

    /**
     * Execute a single EM iteration
     * 
     * @param cscGrammar
     * @param minimumRuleLogProbability
     * @return EM result, including the newly trained {@link ProductionListGrammar}
     */
    EmIterationResult emIteration(final ProductionListGrammar plGrammar, final float minimumRuleLogProbability) {

        final long t0 = System.currentTimeMillis();
        final ConstrainedInsideOutsideGrammar cscGrammar = cscGrammar(plGrammar);

        final ParserDriver opts = new ParserDriver();
        opts.cellSelectorModel = ConstrainedCellSelector.MODEL;
        final ConstrainedInsideOutsideParser parser = new ConstrainedInsideOutsideParser(opts, cscGrammar);

        final FractionalCountGrammar countGrammar = new FractionalCountGrammar((SplitVocabulary) cscGrammar.nonTermSet,
                cscGrammar.lexSet, cscGrammar.packingFunction);

        final long t1 = System.currentTimeMillis();

        // Iterate over the training corpus, parsing and counting rule occurrences
        float corpusLikelihood = 0f;
        for (final ConstrainingChart constrainingChart : constrainingCharts) {
            parser.findBestParse(constrainingChart);
            corpusLikelihood += parser.chart.getInside(0, parser.chart.size(), 0);
            parser.countRuleOccurrences(countGrammar);
        }
        final long t2 = System.currentTimeMillis();
        final ProductionListGrammar finalPlGrammar = countGrammar.toProductionListGrammar(minimumRuleLogProbability);

        return new EmIterationResult(countGrammar, finalPlGrammar, corpusLikelihood, (int) (t2 - t1),
                (int) (System.currentTimeMillis() - t2 + t1 - t0));
    }

    private ProductionListGrammar merge(final ProductionListGrammar plGrammar,
            final FractionalCountGrammar countGrammar, final ConstrainedInsideOutsideGrammar cscGrammar) {

        // Special-case - just merge TOP_0
        if (mergeFraction == 0) {
            final ProductionListGrammar mergedGrammar = plGrammar.merge(new short[] { 1 });
            BaseLogger.singleton().fine("Merged 1 nonterminal. Grammar size:  " + grammarSummaryString(mergedGrammar));
            return mergedGrammar;
        }

        // Estimate the merge cost
        final float[] mergeCost = estimateMergeCost(cscGrammar, countGrammar);

        // Populate a temporary array of the nonterminal indices represented in the cost array (odd indices only)
        final float[] tmpCost = new float[mergeCost.length];
        System.arraycopy(mergeCost, 0, tmpCost, 0, mergeCost.length);
        final short[] tmpIndices = new short[mergeCost.length];
        for (int i = 0; i < tmpIndices.length; i++) {
            tmpIndices[i] = (short) (i * 2 + 1);
        }
        // Sort the parallel array by cost (from least costly to most)
        Arrays.sort(tmpCost, tmpIndices);
        Arrays.reverse(tmpCost);
        Arrays.reverse(tmpIndices);

        // Copy the least-costly indices into a new array
        final short[] mergeIndices = new short[Math.round(mergeCost.length * mergeFraction)];
        System.arraycopy(tmpIndices, 0, mergeIndices, 0, mergeIndices.length);
        java.util.Arrays.sort(mergeIndices);

        if (BaseLogger.singleton().isLoggable(Level.FINER)) {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tmpCost.length; i++) {
                sb.append(plGrammar.vocabulary.getSymbol(tmpIndices[i]) + " " + tmpCost[i] + '\n');
                if (i == mergeIndices.length) {
                    sb.append("--------\n");
                }
            }
            BaseLogger.singleton().finer("Merge Costs:");
            BaseLogger.singleton().finer(sb.toString());
        }
        // Perform the merge
        final ProductionListGrammar mergedGrammar = plGrammar.merge(mergeIndices);
        BaseLogger.singleton().config(
                "Merged " + mergeIndices.length + " nonterminals. Grammar size:  "
                        + grammarSummaryString(mergedGrammar));
        return mergedGrammar;
    }

    /**
     * Estimate the likelihood loss for each nonterminal if it is re-merged with its split sibling.
     * 
     * @param cscGrammar
     * @return Array of estimated likelihood losses for each nonterminal if merged into its sibling (only odd-numbered
     *         entries are populated)
     */
    private float[] estimateMergeCost(final ConstrainedInsideOutsideGrammar cscGrammar,
            final FractionalCountGrammar countGrammar) {

        final ParserDriver opts = new ParserDriver();
        opts.cellSelectorModel = ConstrainedCellSelector.MODEL;
        final ConstrainedInsideOutsideParser parser = new ConstrainedInsideOutsideParser(opts, cscGrammar);

        // Compute log(p_1), log(p_2) for each split pair based on relative frequency counts of each
        final float[] logSplitFraction = countGrammar.logSplitFraction();

        // Iterate over training corpus and compute merge cost
        final float[] mergeCost = new float[cscGrammar.nonTermSet.size() / 2];

        // Iterate over the training corpus, parsing and counting rule occurrences
        @SuppressWarnings("unused")
        int sentenceCount = 0;
        for (final ConstrainingChart constrainingChart : constrainingCharts) {
            parser.findBestParse(constrainingChart);
            parser.countMergeCost(mergeCost, logSplitFraction);
            sentenceCount++;
        }

        return mergeCost;
    }

    private void parseDevSet(final BufferedReader devCorpusReader, final ConstrainedInsideOutsideGrammar mergedGrammar)
            throws IOException {
        BaseLogger.singleton().info("Parsing development set...");
        devCorpusReader.reset();

        GlobalConfigProperties.singleton().setProperty(Parser.PROPERTY_MAX_BEAM_WIDTH,
                Integer.toString(Math.round(pruneFraction * mergedGrammar.nonTermSet.size())));

        final CartesianProductHashSpmlParser parser = new CartesianProductHashSpmlParser(new ParserDriver(),
                mergedGrammar);
        final BracketEvaluator evaluator = new BracketEvaluator();

        for (String line = devCorpusReader.readLine(); line != null; line = devCorpusReader.readLine()) {
            final NaryTree<String> goldTree = NaryTree.read(line, String.class);

            // Extract tokens from training tree, parse, and evaluate
            final String sentence = Strings.join(goldTree.leafLabels(), " ");
            final ParseTask context = parser.parseSentence(sentence);

            if (context.binaryParse != null) {
                evaluator.evaluate(goldTree, context.binaryParse.unfactor(GrammarFormatType.Berkeley));
            }
        }

        final EvalbResult evalbResult = evaluator.accumulatedResult();
        BaseLogger.singleton().info(String.format("Dev-set F-score: %.2f", evalbResult.f1() * 100));
    }

    /**
     * Reports the result of an EM iteration to the user via {@link BaseLogger}
     * 
     * @param result
     * @param i
     */
    private void logEmIteration(final EmIterationResult result, final int i) {
        if (BaseLogger.singleton().isLoggable(Level.FINE)) {
            BaseLogger.singleton().fine(
                    String.format("Iteration: %2d  Likelihood: %.2f  EM Time: %5dms  Grammar Time: %4dms  "
                            + grammarSummaryString(result.plGrammar), i, result.corpusLikelihood, result.emTime,
                            result.grammarConversionTime));
        } else {
            BaseLogger.singleton().config(
                    String.format("Iteration: %2d  Likelihood: %.2f  EM Time: %5dms  Grammar Time: %dms", i,
                            result.corpusLikelihood, result.emTime, result.grammarConversionTime));
        }
    }

    /**
     * Writes a grammar file to disk
     * 
     * @param filename
     * @param grammar
     * @throws IOException
     */
    private void writeGrammarToFile(String filename, final ProductionListGrammar grammar) throws IOException {

        filename = (outputGrammarPrefix != null ? outputGrammarPrefix : "") + filename;

        if (outputGrammarDirectory != null) {
            final Writer w = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(new File(
                    outputGrammarDirectory, filename))));
            w.write(grammar.toString(false, language, grammarFormatType, rareWordThreshold));
            w.close();
        }
    }

    /**
     * Returns a short summary of statistics about the supplied grammar
     * 
     * @param grammar
     * @return A short summary of statistics about the supplied grammar
     */
    private String grammarSummaryString(final ProductionListGrammar grammar) {
        return String.format("%4d nonterminals  %7d binary rules  %6d unary rules  %7d lexical rules",
                grammar.vocabulary.size(), grammar.binaryProductions.size(), grammar.unaryProductions.size(),
                grammar.lexicalProductions.size());
    }

    public static void main(final String[] args) {
        run(args);
    }

    public static class EmIterationResult {

        final FractionalCountGrammar fcGrammar;
        final ProductionListGrammar plGrammar;
        final float corpusLikelihood;
        final int emTime;
        final int grammarConversionTime;

        public EmIterationResult(final FractionalCountGrammar fcGrammar, final ProductionListGrammar plGrammar,
                final float corpusLikelihood, final int emTime, final int grammarConversionTime) {
            this.fcGrammar = fcGrammar;
            this.plGrammar = plGrammar;
            this.corpusLikelihood = corpusLikelihood;
            this.emTime = emTime;
            this.grammarConversionTime = grammarConversionTime;
        }

        @Override
        public String toString() {
            return String.format("%.2f", corpusLikelihood);
        }
    }
}
