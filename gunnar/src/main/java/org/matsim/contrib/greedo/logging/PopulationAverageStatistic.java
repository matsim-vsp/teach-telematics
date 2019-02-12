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
package org.matsim.contrib.greedo.logging;

import floetteroed.utilities.statisticslogging.Statistic;

/**
 *
 * @author Gunnar Flötteröd
 *
 */
public abstract class PopulationAverageStatistic implements Statistic<LogDataWrapper> {

	@Override
	public String label() {
		return this.getClass().getSimpleName();
	}

	protected String averageOrEmpty(final Double sum, final Integer count) {
		if ((sum != null) && (count != null)) {
			return Double.toString(sum / count);
		} else {
			return "";
		}
	}
	
	@Override
	public abstract String value(LogDataWrapper arg0);

}
