/* *********************************************************************** *
 * project: org.matsim.*
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

package playground.andreas.P2.stats.abtractPAnalysisModules;

import java.util.HashMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.events.PersonEntersVehicleEvent;
import org.matsim.core.events.PersonLeavesVehicleEvent;
import org.matsim.core.events.TransitDriverStartsEvent;
import org.matsim.core.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.core.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.core.events.handler.TransitDriverStartsEventHandler;


/**
 * Calculates the average in vehicle trip travel time per ptModes specified. A trip starts by entering a vehicle and end by leaving one.
 * 
 * @author aneumann
 *
 */
public class AverageInVehicleTripTravelTimeSecondsPerMode extends AbstractPAnalyisModule implements TransitDriverStartsEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler{
	
	private final static Logger log = Logger.getLogger(AverageInVehicleTripTravelTimeSecondsPerMode.class);
	
	private HashMap<Id, String> vehId2ptModeMap;
	private HashMap<String, Double> ptMode2SecondsTravelledMap;
	private HashMap<String, Integer> ptMode2TripCountMap;
	private HashMap<Id, Double> agentId2PersonEntersVehicleEventTime = new HashMap<Id, Double>();
	
	public AverageInVehicleTripTravelTimeSecondsPerMode(String ptDriverPrefix){
		super(AverageInVehicleTripTravelTimeSecondsPerMode.class.getSimpleName(),ptDriverPrefix);
		log.info("enabled");
	}

	@Override
	public String getResult() {
		StringBuffer strB = new StringBuffer();
		for (String ptMode : this.ptModes) {
			strB.append(", " + (this.ptMode2SecondsTravelledMap.get(ptMode) / this.ptMode2TripCountMap.get(ptMode)));
		}
		return strB.toString();
	}
	
	@Override
	public void reset(int iteration) {
		this.vehId2ptModeMap = new HashMap<Id, String>();
		this.ptMode2SecondsTravelledMap = new HashMap<String, Double>();
		this.ptMode2TripCountMap = new HashMap<String, Integer>();
		this.agentId2PersonEntersVehicleEventTime = new HashMap<Id, Double>();
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		String ptMode = this.lineIds2ptModeMap.get(event.getTransitLineId());
		if (ptMode == null) {
			log.warn("Should not happen");
			ptMode = "no valid pt mode found";
		}
		
		this.vehId2ptModeMap.put(event.getVehicleId(), ptMode);
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if(!event.getPersonId().toString().startsWith(ptDriverPrefix)){
			this.agentId2PersonEntersVehicleEventTime.put(event.getPersonId(), event.getTime());
		}
	}
	
	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		if(!event.getPersonId().toString().startsWith(ptDriverPrefix)){
			String ptMode = this.vehId2ptModeMap.get(event.getVehicleId());
			if (ptMode == null) {
				ptMode = "nonPtMode";
			}
			
			if (ptMode2SecondsTravelledMap.get(ptMode) == null) {
				ptMode2SecondsTravelledMap.put(ptMode, new Double(0.0));
			}
			if (ptMode2TripCountMap.get(ptMode) == null) {
				ptMode2TripCountMap.put(ptMode, new Integer(0));
			}
			
			this.ptMode2SecondsTravelledMap.put(ptMode, new Double(this.ptMode2SecondsTravelledMap.get(ptMode) + (event.getTime() - this.agentId2PersonEntersVehicleEventTime.get(event.getPersonId()).doubleValue())));
			this.ptMode2TripCountMap.put(ptMode, new Integer(this.ptMode2TripCountMap.get(ptMode) + 1));
		}
	}
}
