/* *********************************************************************** *
 * project: org.matsim.*
 * AgentsInMunicipalityEventsHandler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.christoph.evacuation.analysis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.ActivityEndEvent;
import org.matsim.core.api.experimental.events.ActivityStartEvent;
import org.matsim.core.api.experimental.events.Event;
import org.matsim.core.api.experimental.events.LinkEnterEvent;
import org.matsim.core.api.experimental.events.LinkLeaveEvent;
import org.matsim.core.api.experimental.events.handler.ActivityEndEventHandler;
import org.matsim.core.api.experimental.events.handler.ActivityStartEventHandler;
import org.matsim.core.api.experimental.events.handler.LinkEnterEventHandler;
import org.matsim.core.api.experimental.events.handler.LinkLeaveEventHandler;
import org.matsim.core.api.experimental.facilities.ActivityFacility;
import org.matsim.core.api.experimental.facilities.Facility;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.households.Household;
import org.matsim.utils.objectattributes.ObjectAttributes;

import com.vividsolutions.jts.geom.Geometry;

public class AgentsInMunicipalityEventsHandler implements LinkEnterEventHandler, LinkLeaveEventHandler, 
	ActivityStartEventHandler, ActivityEndEventHandler {
	
	final private static Logger log = Logger.getLogger(AgentsInMunicipalityEventsHandler.class);
	
	private final static int maxTime = 36*3600;
	private final static String separator = "\t";
	private final static String newLine = "\n";
	private final static Charset charset = Charset.forName("UTF-8");
	
	private final Scenario scenario;
	private final ObjectAttributes householdObjectAttributes;
	private final String outputFile;
	
	private final Set<Id> insideAgents;
	private final Set<Id> residentAgents;
	private final Set<Id> residentHouseholds;
	private final Map<Integer, List<Id>> residentHouseholdHHTPs;
	private final CoordAnalyzer coordAnalyzer;
	private final List<PlotData> plotData;
	
	private FileOutputStream fos;
	private OutputStreamWriter osw;
	private BufferedWriter bw;
	
	private int currentSecond = 0;
		
	public AgentsInMunicipalityEventsHandler(Scenario scenario, ObjectAttributes householdObjectAttributes, String outputFile, Geometry area) {
		this.scenario = scenario;
		this.householdObjectAttributes = householdObjectAttributes;
		this.outputFile = outputFile;
		
		insideAgents = new HashSet<Id>();
		residentAgents = new HashSet<Id>();
		residentHouseholds = new HashSet<Id>();
		residentHouseholdHHTPs = new TreeMap<Integer, List<Id>>();
		plotData = new ArrayList<PlotData>();

		coordAnalyzer = new CoordAnalyzer(area);
		
		getResidents();
	}
	
	private void getResidents() {
		for (Household household : ((ScenarioImpl) scenario).getHouseholds().getHouseholds().values()) {
			if (household.getMemberIds().size() == 0) continue;
			
			Coord householdCoord = getHouseholdCoord(household);
			
			if (householdCoord == null) {
				throw new RuntimeException("No Coordinate found for household " + household.getId());
			}
			
			boolean isInside = coordAnalyzer.isCoordAffected(householdCoord);
			
			if (isInside) {
				// mark household as resident household and add it to the HHTP map
				residentHouseholds.add(household.getId());
				int HHTP = Integer.valueOf(String.valueOf(householdObjectAttributes.getAttribute(household.getId().toString(), "HHTP")));
				List<Id> list = residentHouseholdHHTPs.get(HHTP);
				if (list == null) {
					list = new ArrayList<Id>();
					residentHouseholdHHTPs.put(HHTP, list);
				}
				list.add(household.getId());
				
				// mark household members as residents and agents within the area
				for (Id id : household.getMemberIds()) {
					insideAgents.add(id);
					residentAgents.add(id);
				}
			}
		}
	}
	
	private Coord getHouseholdCoord(Household household) {
		for (Id id : household.getMemberIds()) {
			Person person = scenario.getPopulation().getPersons().get(id);

			if (person.getSelectedPlan().getPlanElements().size() == 0) continue;
			else {
				Activity activity = (Activity) person.getSelectedPlan().getPlanElements().get(0);

				if (activity.getFacilityId() != null) {
					ActivityFacility facility = ((ScenarioImpl) scenario).getActivityFacilities().getFacilities().get(activity.getFacilityId());
					return facility.getCoord();
				} else {
					Link link = scenario.getNetwork().getLinks().get(activity.getLinkId());
					log.warn("No facility defined in activity - taking coordinate from activity...");
					return link.getCoord();
				}
			}
		}
		return null;
	}
	
	
	public void beforeEventsReading() {
		 try {
		    	fos = new FileOutputStream(outputFile + ".txt");
		    	osw = new OutputStreamWriter(fos, charset);
		    	bw = new BufferedWriter(osw);
			
		    	// write header
		    	bw.write("time");
		    	bw.write(separator);
		    	bw.write("total inside area");
		    	bw.write(separator);
		    	bw.write("residents inside area");
		    	bw.write(separator);
		    	bw.write("commuters inside area");
		    	bw.write(newLine);
		    	
		    	// write statistics before first event
		    	printStatistics();
		    	
			    } catch (IOException e) {
				Gbl.errorMsg(e);
			}
	}
	
	public void afterEventsReading() {
		 try {
		    	// write statistics after last event
		    	printStatistics();
		    	
		    	bw.close();
		    	osw.close();
		    	fos.close();
		    	
		    	writeGraphic(outputFile + ".png", getGraphic());
		    	
			    } catch (IOException e) {
				Gbl.errorMsg(e);
			}
	}
	
	@Override
	public void reset(int iteration) {
	}

	@Override
	public void handleEvent(LinkLeaveEvent event) {
		checkTime(event);
		
		Id linkId = event.getLinkId();
		Link link = scenario.getNetwork().getLinks().get(linkId);
		
		boolean isInside = coordAnalyzer.isCoordAffected(link.getCoord());
		
		if (isInside) insideAgents.remove(event.getPersonId());
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		checkTime(event);
		
		Id linkId = event.getLinkId();
		Link link = scenario.getNetwork().getLinks().get(linkId);
		
		boolean isInside = coordAnalyzer.isCoordAffected(link.getCoord());
		
		if (isInside) insideAgents.add(event.getPersonId());
	}
	
	@Override
	public void handleEvent(ActivityStartEvent event) {
		checkTime(event);
		
		Id facilityId = event.getFacilityId();
		Facility facility = ((ScenarioImpl) scenario).getActivityFacilities().getFacilities().get(facilityId);
		
		boolean isInside = coordAnalyzer.isCoordAffected(facility.getCoord());
		
		if (isInside) insideAgents.add(event.getPersonId());
		else insideAgents.remove(event.getPersonId());
	}
	
	@Override
	public void handleEvent(ActivityEndEvent event) {
		checkTime(event);
		
		Id linkId = event.getLinkId();
		Link link = scenario.getNetwork().getLinks().get(linkId);
		
		boolean isInside = coordAnalyzer.isCoordAffected(link.getCoord());
		
		if (isInside) insideAgents.add(event.getPersonId());
	}
	
	private void checkTime(Event event) {
		double eventTime = event.getTime();
		if (eventTime > currentSecond) {
			printStatistics();
			currentSecond = (int) eventTime;
		}
	}
	
	public void printInitialStatistics() {
		try {
	    	fos = new FileOutputStream(outputFile + "_statistics.txt");
	    	osw = new OutputStreamWriter(fos, charset);
	    	bw = new BufferedWriter(osw);
		
	    	bw.write("residental people");
	    	bw.write(separator);
	    	bw.write(String.valueOf(this.residentAgents.size()));
	    	bw.write(newLine);
	    	
	    	bw.write("residental households");
	    	bw.write(separator);
	    	bw.write(String.valueOf(this.residentHouseholds.size()));
	    	bw.write(newLine);

	    	// HHTP statistics
	    	bw.write(newLine);
	    	bw.write("household HHTP");
	    	bw.write(separator);
	    	bw.write("number of households");
	    	bw.write(newLine);
	    	for (Entry<Integer, List<Id>> entry : this.residentHouseholdHHTPs.entrySet()) {
	    		bw.write(String.valueOf(entry.getKey()));
		    	bw.write(separator);
		    	bw.write(String.valueOf(entry.getValue().size()));
		    	bw.write(newLine);
	    	}
	    	
	    	bw.close();
	    	osw.close();
	    	fos.close();
		} catch (IOException e) {
			Gbl.errorMsg(e);
		}
	}
	
	private void printStatistics() {
		try {
			int residents = 0;
			for (Id insideAgent : insideAgents) {
				if (residentAgents.contains(insideAgent)) residents++;
			}
			
			bw.write(String.valueOf(currentSecond));
			bw.write(separator);
			bw.write(String.valueOf(insideAgents.size()));
			bw.write(separator);
			bw.write(String.valueOf(residents));
			bw.write(separator);
			bw.write(String.valueOf(insideAgents.size() - residents));
			bw.write(newLine);
			
			if (currentSecond <= maxTime) {
				PlotData pd = new PlotData();
				pd.time = currentSecond;
				pd.residentAgentCount = residents;
				pd.commuterAgentCount = insideAgents.size() - residents;
				plotData.add(pd);
			}
		} catch (IOException e) {
			Gbl.errorMsg(e);
		}
	}
	
	private void writeGraphic(final String filename, JFreeChart chart) {
		try {
			ChartUtilities.saveChartAsPNG(new File(filename), chart, 1024, 768);
		} catch (IOException e) {
			Gbl.errorMsg(e);
		}
	}
	
	private JFreeChart getGraphic() {
		final XYSeriesCollection xyData = new XYSeriesCollection();
		final XYSeries insideSerie = new XYSeries("total inside area", false, true);
		final XYSeries residentsSerie = new XYSeries("residents inside area", false, true);
		final XYSeries commutersSerie = new XYSeries("commuters inside area", false, true);

		for (int i = 0; i < plotData.size(); i++) {
			PlotData pd = plotData.get(i);
			double hour = pd.time / 3600.0;
			insideSerie.add(hour, pd.commuterAgentCount + pd.residentAgentCount);
			residentsSerie.add(hour, pd.residentAgentCount);
			commutersSerie.add(hour, pd.commuterAgentCount);
		}
		xyData.addSeries(insideSerie);
		xyData.addSeries(residentsSerie);
		xyData.addSeries(commutersSerie);

		final JFreeChart chart = ChartFactory.createXYStepChart(
        "agents inside area", "time [hour]", "# agents",
        xyData,
        PlotOrientation.VERTICAL,
        true,   // legend
        false,   // tooltips
        false   // urls
    );

		XYPlot plot = chart.getXYPlot();

		NumberAxis na = new NumberAxis("time [hour]");
		na.setRange(0, maxTime / 3600.0);
		na.setLabelFont(plot.getRangeAxis().getLabelFont());
		na.setTickLabelFont(plot.getRangeAxis().getTickLabelFont());
		plot.setDomainAxis(na);
		return chart;
	}
	
	private static class PlotData {
		int time;
		int commuterAgentCount;
		int residentAgentCount;
	}
}
