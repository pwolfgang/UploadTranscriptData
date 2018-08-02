/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.temple.cla.papolicy.transcriptdata;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Paul Wolfgang
 */
public class BillID {


    private String billID;
    private Set<Transcript> transcripts = new HashSet<>();

    public BillID() {}
    public BillID(String billID) {this.billID = billID;}

    public String getBillID() {return billID;}
    public void setBillID(String billID) {
        this.billID = billID;
    }

    public Set<Transcript> getTranscripts() {return transcripts;}
    public void setTranscripts(Set<Transcript> transcripts) {
        this.transcripts = transcripts;
    }

}
