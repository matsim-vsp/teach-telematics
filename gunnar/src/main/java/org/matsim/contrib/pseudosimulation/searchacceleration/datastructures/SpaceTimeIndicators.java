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
package org.matsim.contrib.pseudosimulation.searchacceleration.datastructures;

import java.util.ArrayList;
import java.util.List;

import floetteroed.utilities.EmptyIterable;

/**
 * Stores real-valued (weighted) indicator data ("visits") that are indexed by
 * (space, time) key tuples. The same key may be set multiple times, meaning
 * that one can store multiple indicators per key. This class is constructed to
 * minimize memory usage for very sparse data. "Space" is represented by the
 * generic class L (e.g. a network link).
 * 
 * 2018-12-17: The indicators may be real-valued in order to allow for the
 * counting of weighted events.
 * 
 * @author Gunnar Flötteröd
 * 
 * @param <L>
 *            the space coordinate type
 */
public class SpaceTimeIndicators<L> {

	// -------------------- INNER CLASS --------------------

	public class Visit {

		public final L spaceObject;

		public final double weight;

		private Visit(final L spaceObject, final double weight) {
			this.spaceObject = spaceObject;
			this.weight = weight;
		}
	}

	// -------------------- MEMBERS --------------------

	// outer list: time bins; inner list: space references
	// private final List<List<L>> data;
	private final List<List<Visit>> data;

	// -------------------- CONSTRUCTION --------------------

	public SpaceTimeIndicators(final int timeBinCnt) {
		// this.data = new ArrayList<List<L>>(timeBinCnt);
		this.data = new ArrayList<List<Visit>>(timeBinCnt);
		for (int bin = 0; bin < timeBinCnt; bin++) {
			this.data.add(null);
		}
	}

	// -------------------- IMPLEMENTATION --------------------

	public int getTimeBinCnt() {
		return this.data.size();
	}

	// public Iterable<L> getVisitedSpaceObjects(final int timeBin) {
	// if (this.data.get(timeBin) != null) {
	// return this.data.get(timeBin);
	// } else {
	// return new EmptyIterable<L>();
	// }
	// }
	public Iterable<Visit> getVisits(final int timeBin) {
		if (this.data.get(timeBin) != null) {
			return this.data.get(timeBin);
		} else {
			// return new EmptyIterable<L>();
			return new EmptyIterable<Visit>();
		}
	}

	// TODO One could put into this weight both the link- and the person weights!

	public void visit(final L spaceObj, final int timeBin, final double weight) {
		// List<L> spaceObjList = this.data.get(timeBin);
		List<Visit> spaceObjList = this.data.get(timeBin);
		if (spaceObjList == null) {
			// spaceObjList = new ArrayList<L>(1);
			spaceObjList = new ArrayList<Visit>(1);
			this.data.set(timeBin, spaceObjList);
		}
		// spaceObjList.add(spaceObj);
		spaceObjList.add(new Visit(spaceObj, weight));
	}
}
