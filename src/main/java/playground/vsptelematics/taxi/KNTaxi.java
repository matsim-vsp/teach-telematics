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

package playground.vsptelematics.taxi;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.taxi.run.MultiModeTaxiConfigGroup;
import org.matsim.contrib.taxi.run.TaxiControlerCreator;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.vis.otfvis.OTFVisConfigGroup;

import java.net.URL;
import java.util.Iterator;
import java.util.Map;

class KNTaxi{
	/**
	 * @param configUrl               configuration (e.g. read from a param file)
	 * @param removeNonPassengers     if {@code true}, only taxi traffic is simulated
	 * @param endActivitiesAtTimeZero if {@code true}, everybody calls taxi at time 0
	 * @param otfvis                  if {@code true}, OTFVis is launched
	 */
	public static void run(URL configUrl, boolean removeNonPassengers, boolean endActivitiesAtTimeZero, boolean otfvis) {
		if (!removeNonPassengers && endActivitiesAtTimeZero) {
			throw new RuntimeException( "endActivitiesAtTimeZero makes sense only in combination with removeNonPassengers");
		}

		Config config = ConfigUtils.loadConfig(configUrl, new MultiModeTaxiConfigGroup(), new DvrpConfigGroup(), new OTFVisConfigGroup());

		OTFVisConfigGroup otfConfig = ConfigUtils.addOrGetModule(config, OTFVisConfigGroup.class);
		otfConfig.setAgentSize(otfConfig.getAgentSize() * 2);

//		var dvrpConfig = ConfigUtils.addOrGetModule( config, DvrpConfigGroup.class );
//
//		var multiModeTaxiConfigGroup = ConfigUtils.addOrGetModule( config, MultiModeTaxiConfigGroup.class );
//		for( TaxiConfigGroup taxiConfig : multiModeTaxiConfigGroup.getModalElements() ){
//
//		}

		Controler controler = TaxiControlerCreator.createControler(config, otfvis);

		if (removeNonPassengers) {
			removePersonsNotUsingMode(TransportMode.taxi, controler.getScenario());
		}

		if (endActivitiesAtTimeZero) {
			setEndTimeForFirstActivities(controler.getScenario(), 0);
		}

		controler.run();
	}

	private static void setEndTimeForFirstActivities(Scenario scenario, double time) {
		Map<Id<Person>, ? extends Person> persons = scenario.getPopulation().getPersons();
		for (Person p : persons.values()) {
			Activity activity = (Activity)p.getSelectedPlan().getPlanElements().get(0);
			activity.setEndTime(time);
		}
	}

	public static void main(String... args) {
		URL configUrl = IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("mielec"), "mielec_taxi_config.xml");
		run(configUrl, true, false, true);
	}

	// yyyy the following used to be in a central place in dvrp, but have been removed or moved somewhere else.  kai, apr'24

	private static void removePersonsNotUsingMode(String mode, Scenario scenario) {
		Map<Id<Person>, ? extends Person> persons = scenario.getPopulation().getPersons();
		Iterator<? extends Person> personIter = persons.values().iterator();

		while (personIter.hasNext()) {
			Plan selectedPlan = personIter.next().getSelectedPlan();

			if (!hasLegOfMode(selectedPlan, mode)) {
				personIter.remove();
			}
		}
	}

	private static boolean hasLegOfMode(Plan plan, String mode) {
		for ( PlanElement pe : plan.getPlanElements()) {
			if (pe instanceof Leg ) {
				if (((Leg)pe).getMode().equals(mode)) {
					return true;
				}
			}
		}

		return false;
	}


}
