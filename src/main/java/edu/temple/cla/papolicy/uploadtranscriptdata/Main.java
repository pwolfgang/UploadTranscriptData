package edu.temple.cla.papolicy.uploadtranscriptdata;

import edu.temple.cla.papolicy.transcriptdata.TranscriptDAO;
import edu.temple.cla.papolicy.xmlutil.XMLUtil;
import java.io.File;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Program to load the Transcript XML files into the database.
 * @author Paul Wolfgang
 */
public class Main {

    private final static Logger logger = Logger.getLogger(Main.class);

    /**
     * Main method
     * 
     * @param args the command line arguments
     * args[0] is the name of the directory or file containing the XML file(s)
     */
    public static void main(String[] args) {
        BasicConfigurator.configure();
        logger.setLevel(Level.INFO);
        File directory = new File(args[0]);
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            for (File file : files) processFile(file);
        } else {
            processFile(directory);
        }
        logger.info("Done processing");
        System.exit(0);
    }

    /**
     * Method to process a single file containing a set of transcripts
     * 
     * @param file file containing the XML document
     */
    private static void processFile(File file) {
        System.out.println("Processing " + file);
        Element e;
        TranscriptDAO transcriptDAO = new TranscriptDAO();
        transcriptDAO.loadDocument(file);
        Document doc = transcriptDAO.getDoc();
        e = doc.getDocumentElement();
        loadTranscripts(transcriptDAO, e);
        logger.info("Done processing " + file.getName());
        transcriptDAO.closeSession();
    }

    /**
     * Method to traverse the DOM tree and insert any transcript elements
     * into the database
     * 
     * @param transcriptDAO Hibernate database access object
     * @param e Root element to be searched.
     */
    private static void loadTranscripts(TranscriptDAO transcriptDAO, Element e) {
        if (e.getNodeName().equals("transcript")) {
            transcriptDAO.insertIntoDatabase(e);
        } else {
            for (Element child : XMLUtil.getChildElements(e)) {
                loadTranscripts(transcriptDAO, child);
            }
        }
    }

}
