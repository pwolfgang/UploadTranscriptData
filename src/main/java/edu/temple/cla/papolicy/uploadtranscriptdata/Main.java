package edu.temple.cla.papolicy.uploadtranscriptdata;

import edu.temple.cla.papolicy.transcriptdata.TranscriptDAO;
import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.Properties;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

/**
 * Program to load the Transcript XML files into the database.
 * @author Paul Wolfgang
 */
public class Main {

    private final static Logger LOGGER = Logger.getLogger(Main.class);

    /**
     * Main method
     * 
     * @param args the command line arguments
     * args[0] is the name of a file containing the datasource parameters.
     * args[1] is the name of the directory or file containing the XML file(s)
     */
    public static void main(String[] args) {
        SessionFactory sessionFactory = configureSessionFactory(args[0]);
        BasicConfigurator.configure();
        LOGGER.setLevel(Level.INFO);
        File directory = new File(args[1]);
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            for (File file : files) processFile(sessionFactory, file);
        } else {
            processFile(sessionFactory, directory);
        }
        LOGGER.info("Done processing");
        System.exit(0);
    }
    
    /**
     * Method to configure Hibernate and return the SessionFactory
     * @param fileName of the parameters file
     * @return a session factory
     */
    public static SessionFactory configureSessionFactory(String fileName) {
        try {
            File file = new File(fileName);
            Properties props = new Properties();
            FileInputStream in = new FileInputStream(file);
            props.load(in);
            return configureSessionFactory(props);
        } catch (Exception ex) {
            throw new RuntimeException("Error configuring SessionFactory", ex);
        }
    }

    public static SessionFactory configureSessionFactory(Properties props) throws HibernateException {
        return new Configuration()
                .setProperty("hibernate.connection.driver_class", props.getProperty("jdbc.driver"))
                .setProperty("hibernate.connection.url", props.getProperty("jdbc.url"))
                .setProperty("hibernate.connection.username", props.getProperty("jdbc.username"))
                .setProperty("hibernate.connection.password", props.getProperty("jdbc.password"))
                .addResource("Transcript.hbm.xml")
                .addResource("Witness.hbm.xml")
                .addResource("CommitteeAliases.hbm.xml")
                .addResource("BillID.hbm.xml")
                .configure()
                .buildSessionFactory();
    }

    /**
     * Method to process a single file containing a set of transcripts
     * 
     * @param factory The SessionFactory
     * @param file file containing the XML document
     */
    private static void processFile(SessionFactory factory, File file) {
        TranscriptDAO transcriptDAO = new TranscriptDAO(factory);
        transcriptDAO.loadDocument(file.getName());
    }


}
