package edu.ohsu.cslu.grammar;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import cltool.BaseCommandlineTool;
import edu.ohsu.cslu.parser.edgeselector.BoundaryInOut;
import edu.ohsu.cslu.parser.edgeselector.EdgeSelector;
import edu.ohsu.cslu.parser.edgeselector.EdgeSelector.EdgeSelectorType;

/**
 * Command-line tool to convert textual grammar formats to Java serialized object format.
 * 
 * @author Aaron Dunlop
 * @since Sep 9, 2010
 * 
 * @version $Revision$ $Date$ $Author$
 */
public class SerializeModel extends BaseCommandlineTool {

    @Argument(index = 0, metaVar = "grammar", required = true, usage = "Grammar file (text, gzipped text, or binary serialized")
    private String inputGrammarFile;

    @Option(name = "-gc", metaVar = "class", usage = "Grammar Class")
    public String grammarClass;

    @Option(name = "-fom", metaVar = "fom", usage = "Figure of Merit")
    public EdgeSelectorType fomType;

    @Option(name = "-fomModel", metaVar = "file", usage = "FOM model file")
    private String fomModelFileName;

    @Option(name = "-o", metaVar = "options", usage = "Default options to be serialized (key=value, comma-separated)")
    private String options;

    @Option(name = "-ser", required = true, metaVar = "filename", usage = "Serialized output file")
    private String serializedOutputFile;

    public static void main(final String[] args) {
        run(args);
    }

    @Override
    protected void run() throws Exception {
        Reader grammarReader;

        if (inputGrammarFile.endsWith(".gz")) {
            grammarReader = new InputStreamReader(new GZIPInputStream(new FileInputStream(inputGrammarFile)));
        } else {
            grammarReader = new FileReader(inputGrammarFile);
        }

        // Try to create the output stream before we read in the source files. At least it will fail a little sooner if
        // the path is invalid
        OutputStream os = new FileOutputStream(serializedOutputFile);
        if (serializedOutputFile.endsWith(".gz")) {
            os = new GZIPOutputStream(os);
        }
        final ObjectOutputStream oos = new ObjectOutputStream(os);
        final PrintWriter w = new PrintWriter(new OutputStreamWriter(os));

        logger.info("Reading grammar...");
        Grammar g = new Grammar(grammarReader);

        if (grammarClass != null && grammarClass != Grammar.class.getName()) {
            logger.info("Converting grammar...");
            g = (Grammar) Class.forName(grammarClass).getConstructor(Grammar.class).newInstance(g);
        }

        EdgeSelector fom = null;
        if (fomModelFileName != null) {
            logger.info("Reading FOM...");
            // Handle gzipped and non-gzipped model files
            final BufferedReader fomModelReader = fomModelFileName.endsWith(".gz") ? new BufferedReader(
                    new InputStreamReader(new GZIPInputStream(new FileInputStream(fomModelFileName))))
                    : new BufferedReader(new FileReader(fomModelFileName));
            switch (fomType) {
            case BoundaryInOut:
                fom = new BoundaryInOut(g, fomModelReader);
                break;
            default:
                throw new UnsupportedOperationException("FOM type not supported");
            }
        }

        logger.info("Writing serialized model file...");

        // Write a key/value header line
        final StringBuilder headerLine = new StringBuilder();
        headerLine.append("grammarClass=" + g.getClass().getName());
        if (fom != null) {
            headerLine.append(",fomClass=" + fom.getClass().getName());
        }

        headerLine.append(options != null ? options : "");
        oos.writeObject(headerLine.toString());

        // Write the grammar
        logger.info("Writing grammar...");
        oos.writeObject(g);

        // Write the FOM model
        if (fom != null) {
            logger.info("Writing FOM...");
            oos.writeObject(fom);
        }
        oos.close();
        logger.info("Serialized model file written.");
    }
}