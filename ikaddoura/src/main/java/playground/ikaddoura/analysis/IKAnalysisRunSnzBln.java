/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
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

package playground.ikaddoura.analysis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import playground.ikaddoura.analysis.modalSplitUserType.AgentAnalysisFilter;

public class IKAnalysisRunSnzBln {
	private static final Logger log = Logger.getLogger(IKAnalysisRunSnzBln.class);
			
	public static void main(String[] args) throws IOException {
			
		String runDirectory = null;
		String runId = null;
		String runDirectoryToCompareWith = null;
		String runIdToCompareWith = null;
		String visualizationScriptInputDirectory = null;
		String scenarioCRS = null;	
		String shapeFileZones = null;
		String zonesCRS = null;
		String zoneFile = null;
		String homeActivityPrefix = null;
		int scalingFactor;
		String modesString = null;
		String taxiMode = null;
		String carMode = null;
		double rewardSAVuserFormerCarUser = 0.;
		String analyzeSubpopulation = null;
		
		if (args.length > 0) {
			throw new RuntimeException();
			
		} else {
			
			runDirectory = "../../runs-svn/avoev/2019-05/output_2019-05-08_snz-bc-0/";
			runId = "snz-bc-0";		
			
			runDirectoryToCompareWith = null;
			runIdToCompareWith = null;
			
			visualizationScriptInputDirectory = "./visualization-scripts/";
			
			scenarioCRS = "EPSG:25832";
			
			shapeFileZones = "../../shared-svn/projects/avoev/data/berlkoenig-od-trips/Bezirksregionen_zone_UTM32N/Bezirksregionen_zone_UTM32N_fixed.SHP";
			zonesCRS = "EPSG:25832";
			
			zoneFile = "../../shared-svn/projects/avoev/data/berlin-area/berlin-area_EPSG25832.shp";

			homeActivityPrefix = "home";
			scalingFactor = 4;
			
			modesString = TransportMode.car + "," + TransportMode.pt + "," + TransportMode.bike + "," + TransportMode.walk + "," + TransportMode.ride;
			
			taxiMode = null;
			carMode = null;
			rewardSAVuserFormerCarUser = 0.0;
			
			analyzeSubpopulation = null;
		}
		
		Scenario scenario1 = loadScenario(runDirectory, runId);
		Scenario scenario0 = loadScenario(runDirectoryToCompareWith, runIdToCompareWith);
		
		List<AgentAnalysisFilter> filter1 = new ArrayList<>();
		
		AgentAnalysisFilter filter1a = new AgentAnalysisFilter(scenario1);
		filter1a.preProcess(scenario1);
		filter1.add(filter1a);
		
		AgentAnalysisFilter filter1b = new AgentAnalysisFilter(scenario1);
		filter1b.setZoneFile(zoneFile);
		filter1b.setRelevantActivityType(homeActivityPrefix);
		filter1b.preProcess(scenario1);
		filter1.add(filter1b);
		
		List<AgentAnalysisFilter> filter0 = new ArrayList<>();
				
		AgentAnalysisFilter filter0a = new AgentAnalysisFilter(scenario0);
		filter0a.preProcess(scenario0);
		filter0.add(filter0a);
		
		AgentAnalysisFilter filter0b = new AgentAnalysisFilter(scenario0);
		filter0b.setZoneFile(zoneFile);
		filter0b.setRelevantActivityType(homeActivityPrefix);
		filter0b.preProcess(scenario0);
		filter0.add(filter0b);
		
		List<String> modes = new ArrayList<>();
		for (String mode : modesString.split(",")) {
			modes.add(mode);
		}

		IKAnalysisRun analysis = new IKAnalysisRun(
				scenario1,
				scenario0,
				visualizationScriptInputDirectory,
				scenarioCRS,
				shapeFileZones,
				zonesCRS,
				homeActivityPrefix,
				scalingFactor,
				filter1,
				filter0,
				modes,
				taxiMode,
				carMode,
				rewardSAVuserFormerCarUser,
				analyzeSubpopulation);
		analysis.run();
	}
	
	private static Scenario loadScenario(String runDirectory, String runId) {
		log.info("Loading scenario...");
		
		if (runDirectory == null) {
			return null;	
		}
		
		if (runDirectory.equals("")) {
			return null;	
		}
		
		if (runDirectory.equals("null")) {
			return null;	
		}
		
		if (!runDirectory.endsWith("/")) runDirectory = runDirectory + "/";

		String networkFile;
		String populationFile;
		String configFile;
		String personAttributesFile;
		
		if (new File(runDirectory + runId + ".output_config.xml").exists()) {
			
			configFile = runDirectory + runId + ".output_config.xml";	
			networkFile = runId + ".output_network.xml.gz";
			populationFile = runId + ".output_plans.xml.gz";
			personAttributesFile = runId + ".output_personAttributes.xml.gz";
			
		} else {
			
			configFile = runDirectory + "output_config.xml";	
			networkFile = "output_network.xml.gz";
			populationFile = "output_plans.xml.gz";
			personAttributesFile = "output_personAttributes.xml.gz";

		}

		Config config = ConfigUtils.loadConfig(configFile);

		if (config.controler().getRunId() != null) {
			if (!runId.equals(config.controler().getRunId())) throw new RuntimeException("Given run ID " + runId + " doesn't match the run ID given in the config file. Aborting...");
		} else {
			config.controler().setRunId(runId);
		}

		config.controler().setOutputDirectory(runDirectory);
		config.plans().setInputFile(populationFile);
		config.network().setInputFile(networkFile);
		config.vehicles().setVehiclesFile(null);
		config.transit().setTransitScheduleFile(null);
		config.transit().setVehiclesFile(null);
		config.facilities().setInputFile(null);
		config.plans().setInputPersonAttributeFile(personAttributesFile);
		
		return ScenarioUtils.loadScenario(config);
	}

}
		
