/* *********************************************************************** *
 * project: org.matsim.*
 * MyWithinDayMobsimListener.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
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

package playground.vsptelematics.bangbang;

import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.MobsimDriverAgent;
import org.matsim.core.mobsim.framework.events.MobsimBeforeSimStepEvent;
import org.matsim.core.mobsim.framework.events.MobsimInitializedEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimBeforeSimStepListener;
import org.matsim.core.mobsim.framework.listeners.MobsimInitializedListener;
import org.matsim.core.mobsim.qsim.agents.WithinDayAgentUtils;
import org.matsim.core.mobsim.qsim.interfaces.MobsimVehicle;
import org.matsim.core.mobsim.qsim.interfaces.Netsim;
import org.matsim.core.mobsim.qsim.interfaces.NetsimLink;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.withinday.utils.EditRoutes;
import playground.vsptelematics.bangbang.KNAccidentScenario.MyIterationCounter;

import java.util.*;

/**
 * @author nagel
 *
 */
class IterativeWithinDayReRouteMobsimListener implements MobsimBeforeSimStepListener, MobsimInitializedListener {
	private static final Logger log = LogManager.getLogger( IterativeWithinDayReRouteMobsimListener.class );

	@Inject private Scenario scenario;
	@Inject private LeastCostPathCalculatorFactory pathAlgoFactory;
	@Inject private TravelTime travelTime;
	@Inject private Map<String, TravelDisutilityFactory> travelDisutilityFactories;
	@Inject private MyIterationCounter iterationCounter;
	
	private boolean init = true ;
	private EditRoutes editRoutes ;

	@Override public void notifyMobsimInitialized( MobsimInitializedEvent e ){
		log.warn("####### WE ARE AT ITERATION=" + this.iterationCounter.getIteration() + " #####");
	}

	@Override public void notifyMobsimBeforeSimStep(@SuppressWarnings("rawtypes") MobsimBeforeSimStepEvent event) {

		if ( init ){
			init= false ;
			TravelDisutility travelDisutility = travelDisutilityFactories.get(TransportMode.car).createTravelDisutility( travelTime ) ;
			LeastCostPathCalculator pathAlgo = pathAlgoFactory.createPathCalculator(scenario.getNetwork(), travelDisutility, travelTime) ;
			PopulationFactory pf = scenario.getPopulation().getFactory() ;
			this.editRoutes = new EditRoutes( scenario.getNetwork(), pathAlgo, pf ) ;
		}


		if ( event.getSimulationTime() == 8*3600+5*60 ){
			double replanningProba = 0.1;
			log.warn("going through " + replanningProba *100 + "% of all agents and give the route that would have been optimal in prev iteration" );
			for( MobsimAgent ma : getAgentsToReplan( (Netsim) event.getQueueSimulation(), replanningProba ) ){
				WithinDayReRouteMobsimListener.doReplanning( ma, (Netsim) event.getQueueSimulation(), this.editRoutes );
			}
		}
		// (this can't be in the "initialized" mobsim listener since we want to do it in the middle of the mobsim)
	}
	static List<MobsimAgent> getAgentsToReplan(Netsim mobsim, double replanningProba) {

		List<MobsimAgent> set = new ArrayList<>();

		// find all agents:
		for( NetsimLink link : mobsim.getNetsimNetwork().getNetsimLinks().values() ){
			for (MobsimVehicle vehicle : link.getAllNonParkedVehicles()) {
				MobsimDriverAgent agent=vehicle.getDriver();
				if ( MatsimRandom.getRandom().nextDouble() < replanningProba) {
					set.add(agent);
				}
			}
		}

		return set;
	}
}
