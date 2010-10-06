package edu.ohsu.cslu.parser.chart;

import java.util.LinkedList;
import java.util.List;

import edu.ohsu.cslu.grammar.Grammar;
import edu.ohsu.cslu.grammar.Grammar.Production;
import edu.ohsu.cslu.parser.ParseTree;
import edu.ohsu.cslu.parser.ParserDriver;

public class GoldChart extends Chart {

    LinkedList<ChartEdge> goldEdges[][];

    @SuppressWarnings("unchecked")
    public GoldChart(final ParseTree tree, final Grammar grammar) {
        this.size = tree.getLeafNodes().size() + 1;

        goldEdges = new LinkedList[size][size + 1];
        for (int start = 0; start < size; start++) {
            for (int end = start + 1; end <= size; end++) {
                goldEdges[start][end] = new LinkedList<ChartEdge>();
            }
        }
        addParseTreeToChart(tree, grammar);
    }

    private void addParseTreeToChart(final ParseTree tree, final Grammar grammar) {

        // NOTE: the purpose of this function is that I need to be able
        // to reference the constituents of a gold tree by reference to
        // a <start,end> position. I was hoping to reuse the chart class
        // since this is exactly what it does, but am running into problems
        // of just instantiating a "basic" version (edgeSelector, inside prob,
        // etc). Maybe it would be easier just to create an 2-dim array of
        // lists of ChartEdges (list because there can be gold unary AND binary
        // edges in each cell)

        final List<ParseTree> leafNodes = tree.getLeafNodes();
        int start, end, midpt, numChildren;
        Production prod = null;
        ChartEdge edge;
        String A, B, C;

        assert tree.isBinaryTree() == true;
        assert tree.parent == null; // must be root so start/end indicies make sense
        assert leafNodes.size() == this.size; // tree width/span must be same as chart

        for (final ParseTree node : tree.preOrderTraversal()) {
            // TODO: could make this O(1) instead of O(n) ...
            start = leafNodes.indexOf(node.leftMostLeaf());
            end = leafNodes.indexOf(node.rightMostLeaf()) + 1;
            numChildren = node.children.size();

            if (numChildren > 0) {
                A = node.contents;
                if (numChildren == 2) {
                    B = node.children.get(0).contents;
                    C = node.children.get(1).contents;
                    prod = grammar.getBinaryProduction(A, B, C);
                    midpt = leafNodes.indexOf(node.children.get(0).rightMostLeaf()) + 1;
                    edge = new ChartEdge(prod, getCell(start, midpt), getCell(midpt, end));
                } else if (numChildren == 1) {
                    B = node.children.get(0).contents;
                    if (node.isPOS()) {
                        prod = grammar.getLexicalProduction(A, B);
                    } else {
                        prod = grammar.getUnaryProduction(A, B);
                    }
                    edge = new ChartEdge(prod, getCell(start, end));
                } else {
                    throw new RuntimeException("ERROR: Number of node children is " + node.children.size()
                            + ".  Expecting <= 2.");
                }

                if (prod == null) {
                    ParserDriver.getLogger().info(
                            "WARNING: production does not exist in grammar for node: " + A + " -> "
                                    + node.childrenToString());
                    prod = Grammar.nullProduction;
                }
                goldEdges[start][end].add(edge);
            }
        }

        // parser.edgeSelector = saveEdgeSelector;
    }

    // private int[] extractTokensFromParseTree(final ParseTree tree, final Grammar grammar) {
    // String sentence = "";
    // for (final ParseTree node : tree.getLeafNodes()) {
    // sentence += node.contents + " ";
    // }
    // return grammar.tokenizer.tokenizeToIndex(sentence.trim());
    // }

    @Override
    public ChartCell getCell(final int start, final int end) {
        return new GoldCell(start, end);
        // return new CellChart.HashSetChartCell(start, end);
    }

    @Override
    public float getInside(final int start, final int end, final int nonTerminal) {
        throw new UnsupportedOperationException("getInside() not implemented for GoldChart");
    }

    @Override
    public void updateInside(final int start, final int end, final int nonTerminal, final float insideProbability) {
        throw new UnsupportedOperationException("updateInside() not implemented for GoldChart");
    }

    public LinkedList<ChartEdge> getEdgeList(final int start, final int end) {
        return goldEdges[start][end];
    }

    public class GoldCell extends Chart.ChartCell {

        public GoldCell(final int start, final int end) {
            super(start, end);
        }

        @Override
        public ChartEdge getBestEdge(final int nonTerminal) {
            throw new UnsupportedOperationException("getBestEdge() not implemented for GoldChart");
        }

        @Override
        public float getInside(final int nonTerminal) {
            throw new UnsupportedOperationException("getInside() not implemented for GoldChart");
        }

        @Override
        public int getNumNTs() {
            throw new UnsupportedOperationException("getNumNTs() not implemented for GoldChart");
        }

        @Override
        public void updateInside(final ChartEdge edge) {
            throw new UnsupportedOperationException("updateInside() not implemented for GoldChart");

        }

        @Override
        public void updateInside(final Production p, final ChartCell leftCell, final ChartCell rightCell,
                final float insideProb) {
            throw new UnsupportedOperationException("updateInside() not implemented for GoldChart");

        }

    }
}