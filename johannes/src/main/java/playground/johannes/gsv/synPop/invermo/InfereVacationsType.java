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

package playground.johannes.gsv.synPop.invermo;

import org.joda.time.DateTime;
import playground.johannes.synpop.source.mid2008.processing.EpisodeTask;
import playground.johannes.synpop.data.CommonKeys;
import playground.johannes.synpop.data.Attributable;
import playground.johannes.synpop.data.Episode;

/**
 * @author johannes
 * 
 */
public class InfereVacationsType implements EpisodeTask {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * playground.johannes.synpop.source.mid2008.processing.EpisodeTask#apply(playground.johannes
	 * .gsv.synPop.PlainEpisode)
	 */
	@Override
	public void apply(Episode plan) {
		boolean hasVacations = false;
		for (Attributable act : plan.getActivities()) {
			if ("vacations".equalsIgnoreCase(act.getAttribute(CommonKeys.ACTIVITY_TYPE))) {
				hasVacations = true;
				break;
			}
		}

		if (hasVacations) {
			boolean isLong = false;

			Attributable first = plan.getLegs().get(0);
			Attributable last = plan.getLegs().get(plan.getLegs().size() - 1);

			String startStr = first.getAttribute(CommonKeys.LEG_START_TIME);
			String endStr = last.getAttribute(CommonKeys.LEG_END_TIME);

			if (startStr != null && endStr != null) {
				DateTime start = SplitPlanTask.formatter.parseDateTime(startStr);
				DateTime end = SplitPlanTask.formatter.parseDateTime(endStr);

				if (end.getDayOfYear() - start.getDayOfYear() > 3) {
					isLong = true;
				}
			}
			
			for (Attributable act : plan.getActivities()) {
				if ("vacations".equalsIgnoreCase(act.getAttribute(CommonKeys.ACTIVITY_TYPE))) {
					if (isLong) {
						act.setAttribute(CommonKeys.ACTIVITY_TYPE, "vacations_long");
					} else {
						act.setAttribute(CommonKeys.ACTIVITY_TYPE, "vacations_short");
					}
				}
			}

		}
	}

}
