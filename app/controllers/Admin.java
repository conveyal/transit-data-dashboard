package controllers;


import java.util.List;
import java.math.BigInteger;

import javax.persistence.Query;

import play.db.jpa.JPA;

import models.MetroArea;
import models.NtdAgency;

public class Admin extends Mapper {
    /**
     * The main admin screen.
     */
    public static void index () {
        // get counts for various types of problems
        // Feeds with no agency
        Query q = JPA.em().createNativeQuery("SELECT count(*) FROM GtfsFeed WHERE review = 'NO_AGENCY'");
        List<BigInteger> results = q.getResultList();
        int feedsNoAgency = results.get(0).intValue();
        
        q = JPA.em().createNativeQuery("SELECT count(*) FROM NtdAgency WHERE review = 'AGENCY_MULTIPLE_AREAS'");
        results = q.getResultList();
        int agenciesNoMetro = results.get(0).intValue();
        
        render(feedsNoAgency, agenciesNoMetro);
    }
    
    
    /**
     * Actually perform the split for a given metro
     */
    // 
    public static void saveSplitMetro () {
        // we get the number of splits then parse down the URL params; each metro is named
        // metron, where n is a number greater than or equal to 1 and less than or equal to splits.
        int splits = params.get("splits", Integer.class);
        MetroArea original = MetroArea.findById(params.get("original", Long.class));
        
        // This is an array of lists of long.
        NtdAgency[][] splitAgencies = new NtdAgency[splits][];
        String[] currentAgencies;
        
        for (int i = 1; i <= splits; i++) {
            // get all of the agencies with the metro of this index
            currentAgencies = params.getAll("metro" + i);
            splitAgencies[i - 1] = new NtdAgency[currentAgencies.length];
            
            // loop through each one, getting the agencies
            for (int j = 0; j < currentAgencies.length; j++) {
                splitAgencies[i - 1][j] = NtdAgency.findById(Long.parseLong(currentAgencies[j]));
            }
        }
        
        // now, create new metros for each
        MetroArea metro;
        for (NtdAgency[] agencies: splitAgencies) {
            metro = new MetroArea();
            for (NtdAgency agency : agencies) {
                metro.agencies.add(agency);
            }
            metro.autoname();
            metro.save();
        }
        
        original.disabled = true;
        original.note = "superseded by split metro.";
        original.save();
    }

    /**
     * Split a metro area into the given number of parts interactively.
     * @param metroId The metro area to split
     * @param splits The number of pieces to split it into 
     */
    public static void splitMetroInteractively (long metroId, int splits) {
        MetroArea original = MetroArea.findById(metroId);
        render(original, splits);
    }

}
