package edu.ohsu.cslu.datastructs.narytree;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import edu.ohsu.cslu.tests.FilteredRunner;

@RunWith(FilteredRunner.class)
public class TestStringBinaryTree {

    private StringBinaryTree sampleTree;
    private String stringSampleTree;

    private final static String[] SAMPLE_IN_ORDER_ARRAY = new String[] { "a", "b", "c", "d", "e", "f", "g",
            "h", "i", "j", "k" };
    private final static String[] SAMPLE_PRE_ORDER_ARRAY = new String[] { "f", "d", "b", "a", "c", "e", "i",
            "h", "g", "k", "j" };
    private final static String[] SAMPLE_POST_ORDER_ARRAY = new String[] { "a", "c", "b", "e", "d", "g", "h",
            "j", "k", "i", "f" };

    @Before
    public void setUp() {
        sampleTree = new StringBinaryTree("f");

        final StringBinaryTree d = sampleTree.addChild("d");
        final StringBinaryTree b = d.addChild("b");
        b.addChild("a");
        b.addChild("c");

        d.addChild("e");

        final StringBinaryTree i = sampleTree.addChild("i");
        final StringBinaryTree h = i.addChild("h");
        h.addChild("g");

        final StringBinaryTree k = i.addChild("k");
        k.addChild("j");

        /**
         * <pre>
         *             f 
         *             |
         *       --------------
         *       |           |
         *       d           i
         *       |           |
         *    -------     --------
         *    |     |     |      |
         *    b     e     h      k
         *    |           |      |
         *  -----         g      j
         *  |   |
         *  a   c
         * </pre>
         */
        stringSampleTree = "(f (d (b a c) e) (i (h g) (k j)))";
    }

    @Test
    public void testAddChild() throws Exception {
        final StringBinaryTree tree = new StringBinaryTree("a");
        assertEquals(1, tree.size());
        tree.addChild("b");
        assertEquals(2, tree.size());
        assertNull(tree.subtree("a"));
        assertNotNull(tree.subtree("b"));
    }

    @Test
    public void testAddChildren() throws Exception {
        final StringBinaryTree tree = new StringBinaryTree("a");
        assertEquals(1, tree.size());
        tree.addChildren(new String[] { "b", "c" });
        assertEquals(3, tree.size());
        assertNull(tree.subtree("a"));
        assertNotNull(tree.subtree("b"));
        assertNotNull(tree.subtree("c"));
    }

    @Test
    public void testAddSubtree() throws Exception {
        final StringBinaryTree tree = new StringBinaryTree("a");
        final StringBinaryTree tmp = new StringBinaryTree("b");
        tmp.addChildren(new String[] { "c", "d" });
        tree.addSubtree(tmp);
        assertEquals(4, tree.size());
        assertNotNull(tree.subtree("b"));
        assertNotNull(tree.subtree("b").subtree("c"));
        assertNotNull(tree.subtree("b").subtree("d"));
    }

    @Test
    public void testRemoveChild() throws Exception {
        assertEquals(11, sampleTree.size());

        sampleTree.removeChild("i");
        assertEquals(6, sampleTree.size());

        // Removing the "i" node should remove its children as well
        assertNull(sampleTree.subtree("i"));
        assertNull(sampleTree.subtree("h"));

        sampleTree.removeChild("d");
        assertEquals(1, sampleTree.size());
        assertNull(sampleTree.subtree("d"));
    }

    @Test
    public void testRemoveChildrenByStringArray() throws Exception {
        assertEquals(11, sampleTree.size());

        assertNull(sampleTree.subtree("h"));
        assertNull(sampleTree.subtree("j"));

        // Removing the "d" node should move the right child ("i") to the left child (there is no "g" subtree)
        sampleTree.removeChildren(new String[] { "d", "g" });
        assertEquals(6, sampleTree.size());

        assertNotNull(sampleTree.subtree("i"));
    }

    @Test
    public void testRemoveSubtree() throws Exception {
        assertEquals(11, sampleTree.size());

        sampleTree.removeSubtree("g");
        assertEquals(11, sampleTree.size());

        // Removing the "d" node should remove all its children as well
        sampleTree.removeSubtree("d");
        assertEquals(6, sampleTree.size());
        assertNull(sampleTree.subtree("d"));
        assertNull(sampleTree.subtree("e"));
    }

    @Test
    public void testSubtree() throws Exception {
        final StringBinaryTree subtree = sampleTree.subtree("d");
        assertEquals(5, subtree.size());
        assertNotNull(subtree.subtree("b"));
        assertNotNull(subtree.subtree("e"));
        assertEquals(3, subtree.subtree("b").size());

        assertEquals(5, sampleTree.subtree("i").size());
    }

    @Test
    public void testSize() throws Exception {
        assertEquals(11, sampleTree.size());
        sampleTree.subtree("i").removeSubtree("k");
        assertEquals(9, sampleTree.size());
        sampleTree.subtree("d").subtree("e").addChild("z");
        assertEquals(10, sampleTree.size());
    }

    @Test
    public void testDepthFromRoot() throws Exception {
        assertEquals(0, sampleTree.depthFromRoot());
        assertEquals(1, sampleTree.subtree("d").depthFromRoot());
        assertEquals(2, sampleTree.subtree("i").subtree("k").depthFromRoot());
        assertEquals(3, sampleTree.subtree("i").subtree("k").subtree("j").depthFromRoot());
    }

    @Test
    public void testLeaves() throws Exception {
        assertEquals(5, sampleTree.leaves());
        assertEquals(3, sampleTree.subtree("d").leaves());

        sampleTree.subtree("i").subtree("h").removeChild("g");
        assertEquals(5, sampleTree.leaves());

        sampleTree.subtree("i").removeSubtree("h");
        assertEquals(4, sampleTree.leaves());

        sampleTree.removeSubtree("d");
        assertEquals(1, sampleTree.leaves());
    }

    @Test
    public void testInOrderIterator() throws Exception {
        final Iterator<StringBinaryTree> iter = sampleTree.inOrderIterator();
        for (int i = 0; i < sampleTree.size(); i++) {
            assertEquals(SAMPLE_IN_ORDER_ARRAY[i], iter.next().stringLabel());
        }
    }

    @Test
    public void testInOrderLabelIterator() throws Exception {
        final Iterator<String> iter = sampleTree.inOrderLabelIterator();
        for (int i = 0; i < sampleTree.size(); i++) {
            assertEquals(SAMPLE_IN_ORDER_ARRAY[i], iter.next());
        }
    }

    @Test
    public void testPreOrderIterator() throws Exception {
        final Iterator<StringBinaryTree> iter = sampleTree.preOrderIterator();
        for (int i = 0; i < sampleTree.size(); i++) {
            assertEquals(SAMPLE_PRE_ORDER_ARRAY[i], iter.next().stringLabel());
        }
    }

    @Test
    public void testPreOrderLabelIterator() throws Exception {
        final Iterator<String> iter = sampleTree.preOrderLabelIterator();
        for (int i = 0; i < sampleTree.size(); i++) {
            assertEquals(SAMPLE_PRE_ORDER_ARRAY[i], iter.next());
        }
    }

    @Test
    public void testPostOrderIterator() throws Exception {
        final Iterator<StringBinaryTree> iter = sampleTree.postOrderIterator();
        for (int i = 0; i < sampleTree.size(); i++) {
            assertEquals(SAMPLE_POST_ORDER_ARRAY[i], iter.next().stringLabel());
        }
    }

    @Test
    public void testPostOrderLabelIterator() throws Exception {
        final Iterator<String> iter = sampleTree.postOrderLabelIterator();
        for (int i = 0; i < sampleTree.size(); i++) {
            assertEquals(SAMPLE_POST_ORDER_ARRAY[i], iter.next());
        }
    }

    @Test
    public void testSetStringLabel() throws Exception {
        final StringBinaryTree simpleTree = StringBinaryTree.read(new StringReader("(a (b c) d)"));
        simpleTree.children().get(0).setStringLabel("e");
        assertEquals("(a (e c) d)", simpleTree.toString());
    }

    @Test
    public void testReadFromReader() throws Exception {

        final String stringSimpleTree = "(a (b c) d)";
        final StringBinaryTree simpleTree = StringBinaryTree.read(new StringReader(stringSimpleTree));
        assertEquals(4, simpleTree.size());
        assertEquals(2, simpleTree.subtree("b").size());

        assertEquals("a", simpleTree.label());
        assertEquals("b", simpleTree.subtree("b").label());
        assertEquals("c", simpleTree.subtree("b").subtree("c").label());
        assertEquals("d", simpleTree.subtree("d").label());

        final String stringTestTree = "(a (b (c (d (e f) (g h)) (i j)) (k l)))";
        final StringBinaryTree testTree = StringBinaryTree.read(new StringReader(stringTestTree));
        assertEquals(12, testTree.size());
        assertEquals(8, testTree.subtree("b").subtree("c").size());

        assertEquals("a", testTree.label());
        assertEquals("b", testTree.subtree("b").label());
        assertEquals("c", testTree.subtree("b").subtree("c").label());
        assertEquals("d", testTree.subtree("b").subtree("c").subtree("d").label());
        assertEquals("e", testTree.subtree("b").subtree("c").subtree("d").subtree("e").label());
        assertEquals("f", testTree.subtree("b").subtree("c").subtree("d").subtree("e").subtree("f").label());
        assertEquals("g", testTree.subtree("b").subtree("c").subtree("d").subtree("g").label());
        assertEquals("h", testTree.subtree("b").subtree("c").subtree("d").subtree("g").subtree("h").label());
        assertEquals("i", testTree.subtree("b").subtree("c").subtree("i").label());
        assertEquals("j", testTree.subtree("b").subtree("c").subtree("i").subtree("j").label());
        assertEquals("k", testTree.subtree("b").subtree("k").label());
        assertEquals("l", testTree.subtree("b").subtree("k").subtree("l").label());

        final StringBinaryTree tree = StringBinaryTree.read(new StringReader(stringSampleTree));
        assertEquals(sampleTree, tree);
    }

    @Test
    public void testWriteToWriter() throws Exception {
        StringWriter writer = new StringWriter();
        sampleTree.write(writer);
        assertEquals(stringSampleTree, writer.toString());

        final String stringSimpleTree = "(a (b (c (d (e f) (g h)) (i j)) (k l)))";
        final StringBinaryTree tree = new StringBinaryTree("a");
        tree.addChild("b").addChild("c").addChild("d").addChild("e").addChild("f");
        tree.subtree("b").subtree("c").subtree("d").addChild("g").addChild("h");
        tree.subtree("b").subtree("c").addChild("i").addChild("j");
        tree.subtree("b").addChild("k").addChild("l");

        writer = new StringWriter();
        tree.write(writer);
        assertEquals(stringSimpleTree, writer.toString());
    }

    @Test
    public void testIsLeaf() throws Exception {
        assertFalse(sampleTree.isLeaf());
        assertFalse(sampleTree.subtree("d").isLeaf());
        assertTrue(sampleTree.subtree("i").subtree("h").subtree("g").isLeaf());
    }

    @Test
    public void testEquals() throws Exception {
        final StringBinaryTree tree1 = new StringBinaryTree("a");
        tree1.addChildren(new String[] { "b", "c" });

        final StringBinaryTree tree2 = new StringBinaryTree("a");
        tree2.addChildren(new String[] { "b", "c" });

        final StringBinaryTree tree3 = new StringBinaryTree("a");
        tree3.addChildren(new String[] { "b", "d" });

        assertTrue(tree1.equals(tree2));
        assertFalse(tree1.equals(tree3));

        tree2.subtree("c").addChild("d");
        assertFalse(tree1.equals(tree2));
    }

    /**
     * Tests Java serialization and deserialization of trees
     * 
     * @throws Exception if something bad happens
     */
    @Test
    public void testSerialize() throws Exception {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final ObjectOutputStream oos = new ObjectOutputStream(bos);

        oos.writeObject(sampleTree);

        final ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        final StringBinaryTree t = (StringBinaryTree) ois.readObject();
        assertTrue(sampleTree.equals(t));
    }
}
