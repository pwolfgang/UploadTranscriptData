package edu.temple.cla.papolicy.transcriptdata;

import edu.temple.cla.papolicy.xmlutil.XMLUtil;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Data Access Object for the Transcript table.
 * @author Paul Wolfgang
 */
public class TranscriptDAO {

    private static final Logger logger = Logger.getLogger(TranscriptDAO.class);
    private Document doc;
    private Session dbSession;

    /**
     * Constructor.
     * Initializes the SessionFactory singleton.
     */
    public TranscriptDAO() {
        SessionFactory factory = NewHibernateUtil.getSessionFactory();
        dbSession = factory.openSession();
    }
    
    /**
     * Close the database session
     */
    public void closeSession() {
        dbSession.close();
        dbSession = null;
    }

    /**
     * Loads the transcript XML file into the DOM tree.
     * @param fileName The name of the file containing the XML file.
     */
    public void loadDocument(String fileName) {
        loadDocument(new File(fileName));
    }

    /**
     * Loads the transcript XML file into the DOM tree.
     * @param file The file containing the XML file.
     */
    public void loadDocument(File file) {
        logger.info("Loading " + file.getName());
        try {          
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        doc = builder.parse(file);
        } catch (IOException | SAXException | ParserConfigurationException ex) {
            logger.fatal("Error Parsing " + file.getAbsolutePath(), ex);
        } finally {
            logger.info("End loading " + file.getName());
        }
        
    }

    /**
     * Accessor for the document DOM tree
     * @return The document DOM
     */
    public Document getDoc() {
        return doc;
    }

    /**
     * Method to insert an element (Transcript record) into the database
     * @param e The DOM element representing a Transcript record
     */
    public void insertIntoDatabase(Element e) {
        Query houseCommitteeHqlQuery
                = dbSession.createQuery("from CommitteeAliases c where c.ctyCode like '1%' and c.alternateName like :name");
        Query senateCommitteeHqlQuery
                = dbSession.createQuery("from CommitteeAliases c where c.ctyCode like '2%' and c.alternateName like :name");
        Transaction tx = dbSession.beginTransaction();
        Transcript t = XMLUtil.readElement(Transcript.class, e);
        t.setId(e.getAttribute("id"));
        String transcriptID = t.getId();
        logger.info("Inserting " + transcriptID);
        dbSession.save(t);
        if (t != null) {
            Element bills = XMLUtil.getChildElement(e, "bills");
            if (bills != null) {
                t.setBills(new HashSet<BillID>());
                for (Element billIDElement : XMLUtil.getChildElements(bills)) {
                    String billIDString = billIDElement.getAttribute("id");
                    BillID billID = (BillID) dbSession.get(BillID.class, billIDString);
                    if (billID == null) {
                        billID = new BillID(billIDString);
                        dbSession.save(billID);
                    }
                    t.getBills().add(billID);
                    billID.getTranscripts().add(t);
                }
            }
            Element committees = XMLUtil.getChildElement(e, "committees");
            if (committees != null) {
                t.setCommittees(new HashSet<CommitteeAliases>());
                for (Element committee : XMLUtil.getChildElements(committees)) {
                    String committeeAliasName = committee.getTextContent().trim();
                    committeeAliasName = expandAmpersand(committeeAliasName);
                    if (committeeAliasName.startsWith("Senate")) {
                        committeeAliasName = committeeAliasName.substring(7);
                        insertCommittee(senateCommitteeHqlQuery, 2, committeeAliasName, dbSession, t);
                    } else {
                        insertCommittee(houseCommitteeHqlQuery, 1, committeeAliasName, dbSession, t);
                    }
                }
            }
            Element witnesses = XMLUtil.getChildElement(e, "witnesses");
            if (witnesses != null) {
                t.setWitnesses(new HashSet<Witness>());
                for (Element witness : XMLUtil.getChildElements(witnesses)) {
                    Witness elementWitness = XMLUtil.readElement(Witness.class, witness);
                    dbSession.save(elementWitness);
                    t.getWitnesses().add(elementWitness);
                    elementWitness.setTranscript(t);
                }
            }
        } else {
            logger.error(transcriptID + "not saved to database");
        }
        try {
            tx.commit();
        } catch (Exception ex) {
            System.err.println("Exception thrown " + ex);
            System.err.println(t);
            System.exit(1);
        }
    }

    /**
     * Method to insert a Committee into the Transcript object. If this
     * committee name is not currently in the database it is added to the
     * database
     * @param committeeHqlQuery Query to search for the committee name
     * @param chamber Chamber 1 for House, 2 for Senate
     * @param committeeAliasName The committee name
     * @param dbSession Hibernate Database Session
     * @param t Transcript object
     * @throws HibernateException 
     */
    private void insertCommittee(Query committeeHqlQuery, int chamber, 
            String committeeAliasName, Session dbSession, Transcript t) 
            throws HibernateException {
        committeeHqlQuery.setString("name", committeeAliasName);
        List<CommitteeAliases> list = committeeHqlQuery.list();
        if (list.isEmpty()) {
            CommitteeAliases newCommitteeAlias = new CommitteeAliases();
            newCommitteeAlias.setCtyCode((short) (chamber*100 + 99));
            newCommitteeAlias.setChamber((short) chamber);
            newCommitteeAlias.setAlternateName(committeeAliasName);
            if (chamber == 1) {
                newCommitteeAlias.setName("Other House Committee");
            } else {
                newCommitteeAlias.setName("Other Senate Committee");
            }
            newCommitteeAlias.setStartYear((short) 0);
            newCommitteeAlias.setEndYear((short) 9999);
            Serializable committeeID = dbSession.save(newCommitteeAlias);
            t.getCommittees().add(newCommitteeAlias);
            newCommitteeAlias.getTranscripts().add(t);
        } else {
            try {
                t.getCommittees().add(list.get(0));
                list.get(0).getTranscripts().add(t);
            } catch (Throwable tr) {
                System.err.println(tr);
            }
        }
    }

    /**
     * Method to replace ampersand characters with the word and.
     * @param s String to be processed
     * @return String with any occurrence of &amp; replaced with &quot;and&quot;
     */
    private static String expandAmpersand(String s) {
        StringBuilder stb = new StringBuilder(s);
        int index;
        while ((index = stb.indexOf("&")) != -1) {
            stb.replace(index, index + 1, "and");
        }
        return stb.toString();
    }
}
