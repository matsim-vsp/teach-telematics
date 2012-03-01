/* *********************************************************************** *
 * project: org.matsim.*
 * CenteredTimeProportionalPenaltyFactory.java
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
package playground.thibautd.parknride.scoring;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Plan;

/**
 * @author thibautd
 */
public class CenteredTimeProportionalPenaltyFactory implements ParkingPenaltyFactory {
	private final Coord zoneCenter;
	private final double zoneRadius;
	private final double maxCostPerSecond;

	public CenteredTimeProportionalPenaltyFactory(
			final Coord zoneCenter,
			final double zoneRadius,
			final double maxCostPerSecond) {
		this.zoneCenter = zoneCenter;
		this.zoneRadius = zoneRadius;
		this.maxCostPerSecond = maxCostPerSecond;
	}

	@Override
	public ParkingPenalty createPenalty(final Plan plan) {
		return new CenteredTimeProportionalPenalty( zoneCenter , zoneRadius , maxCostPerSecond );
	}
}

