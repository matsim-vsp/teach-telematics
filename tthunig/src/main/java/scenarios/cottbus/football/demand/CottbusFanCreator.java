/* *********************************************************************** *
 * project: org.matsim.*
 * CottbusFanCreatorI
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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
package scenarios.cottbus.football.demand;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Population;

public interface CottbusFanCreator {

	public Population createAndAddFans(Scenario sc, int numberOfFans);

	public void setEarliestArrivalTimeAtStadium(double earliestArrivalTime);

}