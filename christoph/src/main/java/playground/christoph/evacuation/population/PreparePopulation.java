/* *********************************************************************** *
 * project: org.matsim.*
 * PreparePopulation.java
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

package playground.christoph.evacuation.population;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.mobsim.qsim.multimodalsimengine.router.MultiModalLegRouter;
import org.matsim.core.mobsim.qsim.multimodalsimengine.router.util.PTTravelTimeFactory;
import org.matsim.core.mobsim.qsim.multimodalsimengine.router.util.RideTravelTimeFactory;
import org.matsim.core.mobsim.qsim.multimodalsimengine.tools.MultiModalNetworkCreator;
import org.matsim.core.population.MatsimPopulationReader;
import org.matsim.core.population.PopulationFactoryImpl;
import org.matsim.core.population.PopulationImpl;
import org.matsim.core.population.PopulationReader;
import org.matsim.core.population.PopulationWriter;
import org.matsim.core.population.routes.LinkNetworkRouteFactory;
import org.matsim.core.population.routes.ModeRouteFactory;
import org.matsim.core.router.IntermodalLeastCostPathCalculator;
import org.matsim.core.router.LegRouter;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.costcalculators.TravelCostCalculatorFactoryImpl;
import org.matsim.core.router.util.DijkstraFactory;
import org.matsim.core.router.util.FastAStarLandmarksFactory;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.trafficmonitoring.TravelTimeCalculatorFactoryImpl;
import org.matsim.core.utils.collections.CollectionUtils;

import playground.christoph.evacuation.trafficmonitoring.BikeTravelTimeFactory;
import playground.christoph.evacuation.trafficmonitoring.WalkTravelTimeFactory;
import playground.meisterk.kti.config.KtiConfigGroup;
import playground.meisterk.kti.router.KtiPtRouteFactory;
import playground.meisterk.kti.router.PlansCalcRouteKtiInfo;

public class PreparePopulation {

	final private static Logger log = Logger.getLogger(PreparePopulation.class);
	
	private final Scenario scenario;
	private final TravelTimeCalculator travelTime;
	
	/**
	 * Input arguments:
	 * <ul>
	 *	<li>path to config file</li>
	 *  <li>path to events file</li>
	 *  <li>path to population output file</li>
	 * </ul>
	 */
	public static void main(String[] args) {
		if (args.length != 3) return;
		
		Config config = ConfigUtils.createConfig();
		
		KtiConfigGroup ktiConfigGroup = new KtiConfigGroup();
		config.addModule(KtiConfigGroup.GROUP_NAME, ktiConfigGroup);
		
		ConfigUtils.loadConfig(config, args[0]);
			
		String populationInputFile = config.plans().getInputFile();
		String eventsFile = args[1];
		String populationOutputFile = args[2];
		
		// remove population input file from config
		config.plans().setInputFile(null);
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		new PreparePopulation(scenario, eventsFile, populationInputFile, populationOutputFile);
	}

	public PreparePopulation(Scenario scenario, String eventsFile, String populationInFile, String populationOutFile) {

		this.scenario = scenario;
		
		log.info("Reading events file to get data for travel time calculator...");
		EventsManager eventsManager = EventsUtils.createEventsManager();
		travelTime = new TravelTimeCalculatorFactoryImpl().createTravelTimeCalculator(scenario.getNetwork(), 
				scenario.getConfig().travelTimeCalculator());
		eventsManager.addHandler(travelTime);
		new MatsimEventsReader(eventsManager).readFile(eventsFile);
		log.info("done.");

		log.info("Setup multi-modal network...");
		new MultiModalNetworkCreator(scenario.getConfig().multiModal()).run(scenario.getNetwork());
		log.info("done.");
		
		log.info("Setup multi-modal router...");
		LegRouter legRouter = createLegRouter();
		log.info("done.");
		
		log.info("Reading, processing, writing plans...");
		PopulationImpl population = (PopulationImpl) scenario.getPopulation();

		// add support for KTI pt routes
		KtiConfigGroup ktiConfigGroup = (KtiConfigGroup) scenario.getConfig().getModule(KtiConfigGroup.GROUP_NAME);
		PlansCalcRouteKtiInfo plansCalcRouteKtiInfo = new PlansCalcRouteKtiInfo(ktiConfigGroup);
		plansCalcRouteKtiInfo.prepare(scenario.getNetwork());
		((PopulationFactoryImpl) population.getFactory()).setRouteFactory(TransportMode.pt, new KtiPtRouteFactory(plansCalcRouteKtiInfo));
//		((PopulationFactoryImpl) population.getFactory()).setRouteFactory(TransportMode.car, new KtiLinkNetworkRouteFactory(this.getNetwork(), super.getConfig().planomat()));
		
		population.setIsStreaming(true);
		
		PopulationWriter populationWriter = new PopulationWriter(population, scenario.getNetwork(), ((ScenarioImpl)scenario).getKnowledges());
		populationWriter.startStreaming(populationOutFile);

		population.addAlgorithm(new RemoveUnselectedPlans());
		Set<String> modesToReroute = new HashSet<String>();
		modesToReroute.add(TransportMode.ride);
		modesToReroute.add(TransportMode.bike);
		modesToReroute.add(TransportMode.walk);
		modesToReroute.add(TransportMode.pt);
		population.addAlgorithm(new CreateMultiModalRoutes(legRouter, modesToReroute));
		population.addAlgorithm(populationWriter);
		
		PopulationReader plansReader = new MatsimPopulationReader(scenario);
		plansReader.readFile(populationInFile);
		
		population.printPlansCount();
		populationWriter.closeStreaming();
		log.info("done.");
	}
	
	private LegRouter createLegRouter() {
			
		PlansCalcRouteConfigGroup configGroup = this.scenario.getConfig().plansCalcRoute();
		Map<String, TravelTime> travelTimes = new HashMap<String, TravelTime>();
		travelTimes.put(TransportMode.car, this.travelTime);
		travelTimes.put(TransportMode.walk, new WalkTravelTimeFactory(configGroup).createTravelTime());
		travelTimes.put(TransportMode.bike, new BikeTravelTimeFactory(configGroup).createTravelTime());
		travelTimes.put(TransportMode.ride, new RideTravelTimeFactory(this.travelTime,
				new WalkTravelTimeFactory(configGroup).createTravelTime()).createTravelTime());
		travelTimes.put(TransportMode.pt, new PTTravelTimeFactory(configGroup, 
				this.travelTime, new WalkTravelTimeFactory(configGroup).createTravelTime()).createTravelTime());
		
		
		// create travel costs object and use a multi-model travel time calculator
		TravelDisutility travelCost = new TravelCostCalculatorFactoryImpl().createTravelDisutility(travelTime, 
				this.scenario.getConfig().planCalcScore());

		ModeRouteFactory modeRouteFactory = ((PopulationFactoryImpl) (scenario.getPopulation().getFactory())).getModeRouteFactory();

		// set Route Factories
		LinkNetworkRouteFactory factory = new LinkNetworkRouteFactory();
		for (String mode : CollectionUtils.stringToArray(this.scenario.getConfig().multiModal().getSimulatedModes())) {
			modeRouteFactory.setRouteFactory(mode, factory);
		}
		
		LeastCostPathCalculatorFactory leastCostPathCalculatorFactory = new FastAStarLandmarksFactory(
				this.scenario.getNetwork(), new FreespeedTravelTimeAndDisutility(this.scenario.getConfig().planCalcScore()));
		
		IntermodalLeastCostPathCalculator routeAlgo = (IntermodalLeastCostPathCalculator) 
			leastCostPathCalculatorFactory.createPathCalculator(this.scenario.getNetwork(), travelCost, travelTime);
		MultiModalLegRouter multiModalLegRouter = new MultiModalLegRouter(this.scenario.getNetwork(), new DijkstraFactory(), travelTimes);

		return multiModalLegRouter;
	}
}
