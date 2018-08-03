package edu.temple.cla.papolicy.transcriptdata;

import edu.temple.cla.papolicy.xmlutil.XMLUtil;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.query.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Data Access Object for the Transcript table.
 *
 * @author Paul Wolfgang
 */
public class TranscriptDAO {

    private static final Logger LOGGER = Logger.getLogger(TranscriptDAO.class);
    private final SessionFactory factory;
    private Session dbSession;

    /**
     * Constructor. 
     * @param factory The SessionFactory
     */
    public TranscriptDAO(SessionFactory factory) {
        this.factory = factory;
    }

    /**
     * Loads the transcript XML file into the DOM tree.
     *
     * @param fileName The name of the file containing the XML file.
     */
    public void loadDocument(String fileName) {
        try {
            LOGGER.info("Begin loading file " + fileName);
            loadDocument(new FileInputStream(fileName));
            LOGGER.info("Finished loading file " + fileName);
        } catch (FileNotFoundException ex) {
            LOGGER.error("File " + fileName + " not found", ex);
        }
    }

    /**
     * Loads the transcript XML file into the DOM tree.
     *
     * @param in The input Stream
     */
    public void loadDocument(InputStream in) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(in);
            loadTranscripts(doc.getDocumentElement());
        } catch (IOException | SAXException | ParserConfigurationException ex) {
            LOGGER.fatal("Error Parsing ", ex);
        }

    }

    /**
     * Method to traverse the DOM tree and insert any transcript elements
     * into the database
     * 
     * @param e Root element to be searched.
     */
    private void loadTranscripts(Element e) {
        dbSession = factory.openSession();
        if (e.getNodeName().equals("transcript")) {
            insertIntoDatabase(e);
        } else {
            XMLUtil.getChildElements(e).forEach((child) -> {
                loadTranscripts(child);
            });
        }
        dbSession.close();
    }
    /**
     * Method to insert an element (Transcript record) into the database
     *
     * @param e The DOM element representing a Transcript record
     */
    public void insertIntoDatabase(Element e) {
        Query<CommitteeAliases> houseCommitteeHqlQuery
                = dbSession.createQuery("from CommitteeAliases c where c.ctyCode"
                        + " like '1%' and c.alternateName like :name",
                        CommitteeAliases.class);
        Query<CommitteeAliases> senateCommitteeHqlQuery
                = dbSession.createQuery("from CommitteeAliases c where c.ctyCode"
                        + " like '2%' and c.alternateName like :name",
                        CommitteeAliases.class);
        Transaction tx = dbSession.beginTransaction();
        Transcript t = XMLUtil.readElement(Transcript.class, e);
        if (t.getHearingYear() != null && t.getHearingMonth() != null && t.getHearingDay() != null) {
            LocalDate hearingDate = LocalDate.of(t.getHearingYear(), t.getHearingMonth(), t.getHearingDay());
            t.setHearingDate(Date.from(hearingDate.atStartOfDay().toInstant(ZoneOffset.UTC)));
        } else {
            t.setHearingDate(null);
        }
        if (t.getReceivedYear() != null && t.getReceivedMonth() != null && t.getReceviedDay() != null) {
            LocalDate receivedDate = LocalDate.of(t.getReceivedYear(), t.getReceivedMonth(), t.getReceviedDay());
            t.setReceivedDate(Date.from(receivedDate.atStartOfDay().toInstant(ZoneOffset.UTC)));
        } else {
            t.setReceivedDate(null);
        }
        
        t.setId(e.getAttribute("id"));
        String transcriptID = t.getId();
        LOGGER.info("Inserting " + transcriptID);
        dbSession.save(t);
        Element bills = XMLUtil.getChildElement(e, "bills");
        if (bills != null) {
            t.setBills(new HashSet<>());
            XMLUtil.getChildElements(bills)
                    .stream()
                    .map((billIDElement) -> billIDElement.getAttribute("id"))
                    .map((billIDString) -> {
                        BillID billID = dbSession.get(BillID.class, billIDString);
                        if (billID == null) {
                            billID = new BillID(billIDString);
                            dbSession.save(billID);
                        }
                        return billID;
                    })
                    .map((billID) -> {
                        t.getBills().add(billID);
                        return billID;
                    })
                    .forEachOrdered((billID) -> {
                        billID.getTranscripts().add(t);
                    });
        }
        Element committees = XMLUtil.getChildElement(e, "committees");
        if (committees != null) {
            t.setCommittees(new HashSet<>());
            XMLUtil.getChildElements(committees)
                    .stream()
                    .map((committee) -> committee.getTextContent().trim())
                    .map((committeeAliasName) -> expandAmpersand(committeeAliasName))
                    .forEachOrdered((committeeAliasName) -> {
                        if (committeeAliasName.startsWith("Senate")) {
                            committeeAliasName = committeeAliasName.substring(7);
                            insertCommittee(senateCommitteeHqlQuery, 2, committeeAliasName, dbSession, t);
                        } else {
                            insertCommittee(houseCommitteeHqlQuery, 1, committeeAliasName, dbSession, t);
                        }
                    });
        }
        Element witnesses = XMLUtil.getChildElement(e, "witnesses");
        if (witnesses != null) {
            t.setWitnesses(new HashSet<>());
            XMLUtil.getChildElements(witnesses)
                    .stream()
                    .map((witness) -> XMLUtil.readElement(Witness.class, witness))
                    .map((elementWitness) -> {
                        dbSession.save(elementWitness);
                        return elementWitness;
                    })
                    .map((elementWitness) -> {
                        t.getWitnesses().add(elementWitness);
                        return elementWitness;
                    })
                    .forEachOrdered((elementWitness) -> {
                        elementWitness.setTranscript(t);
                    });
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
     *
     * @param committeeHqlQuery Query to search for the committee name
     * @param chamber Chamber 1 for House, 2 for Senate
     * @param committeeAliasName The committee name
     * @param dbSession Hibernate Database Session
     * @param t Transcript object
     * @throws HibernateException If an error occurs.
     */
    private void insertCommittee(Query<CommitteeAliases> committeeHqlQuery, int chamber,
            String committeeAliasName, Session dbSession, Transcript t)
            throws HibernateException {
        committeeHqlQuery.setParameter("name", committeeAliasName);
        List<CommitteeAliases> list = committeeHqlQuery.list();
        if (list.isEmpty()) {
            CommitteeAliases newCommitteeAlias = new CommitteeAliases();
            newCommitteeAlias.setCtyCode((short) (chamber * 100 + 99));
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
     *
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
