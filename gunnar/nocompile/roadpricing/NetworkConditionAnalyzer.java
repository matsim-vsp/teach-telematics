/*
 * Copyright 2018 Gunnar Flötteröd
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * contact: gunnar.flotterod@gmail.com
 *
 */ 
package opdytsintegration.example.roadpricing;

import java.util.LinkedHashSet;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.opdyts.macrostate.DifferentiatedLinkOccupancyAnalyzer;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.roadpricing.RoadPricingConfigGroup;

import floetteroed.utilities.TimeDiscretization;

/**
 * 
 * @author Gunnar Flötteröd
 *
 */
public class NetworkConditionAnalyzer {

	public NetworkConditionAnalyzer() {
	}

	public static void main(String[] args) {
//
//		final int analyzedIteration = 100;
//		final Config config = ConfigUtils.loadConfig("./input/roadpricing/config.xml", new RoadPricingConfigGroup());
//		final Scenario scenario = ScenarioUtils.loadScenario(config);
//
//		// final LinkOccupancyAnalyzer occupAnalyzer = new
//		// LinkOccupancyAnalyzer(
//		// new TimeDiscretization(0, 1800, 48), scenario.getNetwork()
//		// .getLinks().keySet());
//		final Set<String> relevantModes = new LinkedHashSet<>();
//		relevantModes.add("car");
//		final DifferentiatedLinkOccupancyAnalyzer occupAnalyzer = new DifferentiatedLinkOccupancyAnalyzer(
//				new TimeDiscretization(0, 1800, 48), relevantModes, scenario.getNetwork().getLinks().keySet());
//
//		final EventsManager events = EventsUtils.createEventsManager(config);
//		events.addHandler(occupAnalyzer);
//		final MatsimEventsReader reader = new MatsimEventsReader(events);
//		reader.readFile(config.getModule("controler").getValue("outputDirectory") + "_0/ITERS/it." + analyzedIteration
//				+ "/" + analyzedIteration + ".events.xml.gz");
//		System.out.println();
//		for (Id<Link> linkId : scenario.getNetwork().getLinks().keySet()) {
//			System.out.print(linkId);
//			for (int bin = 0; bin < 48; bin++) {
//				System.out.print("\t");
//				final Link link = scenario.getNetwork().getLinks().get(linkId);
//				
//				// final double relOccup = occupAnalyzer.getCount(linkId, bin)
//				// / (link.getLength() * link.getNumberOfLanes() / 7.5);
//				final double relOccup = occupAnalyzer.getNetworkModeAnalyzer("car").getCount(linkId, bin)
//						/ (link.getLength() * link.getNumberOfLanes() / 7.5);
//
//				// TODO no getter
//				System.out.print(relOccup);
//			}
//			System.out.println();
//		}
	}
}
