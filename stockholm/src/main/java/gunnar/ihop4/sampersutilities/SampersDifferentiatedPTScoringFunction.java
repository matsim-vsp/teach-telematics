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
package gunnar.ihop4.sampersutilities;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Route;
import org.matsim.pt.PtConstants;
import org.matsim.utils.objectattributes.attributable.Attributes;

/**
 *
 * @author Gunnar Flötteröd
 *
 */
public class SampersDifferentiatedPTScoringFunction extends SampersScoringFunction {

	// -------------------- CONSTANTS (TODO) --------------------

	public static final Set<String> PT_SUBMODES;
	static {
		Set<String> ptSubmodes = new LinkedHashSet<>();
		ptSubmodes.add("busPassenger");
		ptSubmodes.add("tramPassenger");
		ptSubmodes.add("subwayPassenger");
		ptSubmodes.add("railPassenger");
		ptSubmodes.add("ferryPassenger");
		PT_SUBMODES = Collections.unmodifiableSet(ptSubmodes);
	}

	// -------------------- MEMBERS --------------------

	private final LinkedList<Leg> tmpLegs = new LinkedList<>();

	private final LinkedList<Activity> tmpActs = new LinkedList<>();

	// -------------------- CONSTRUCTION --------------------

	public SampersDifferentiatedPTScoringFunction(final Person person, final SampersTourUtilityFunction utlFct) {
		super(person, utlFct);
	}

	// -------------------- IMPLEMENTATION OF ScoringFunction --------------------

	@Override
	public void handleLeg(final Leg leg) {
		// The next activity defines how to handle this.
		this.tmpLegs.add(leg);
	}

	@Override
	public void handleActivity(final Activity act) {

		if (PtConstants.TRANSIT_ACTIVITY_TYPE.equals(act.getType())) {

			// We are in the middle of a transit leg. Just memorize, do nothing yet.
			this.tmpActs.add(act);

		} else {

			if (this.tmpLegs.size() == 1) {

				// The previous leg contains a single trip. Expected to be non-PT.
				final Leg leg = this.tmpLegs.getFirst();
				if (TransportMode.pt.equals(leg.getMode()) || PT_SUBMODES.contains(leg.getMode())) {
					throw new RuntimeException("Encountered single-trip leg with mode: " + leg.getMode());
				}
				super.handleLeg(leg);

			} else if (this.tmpLegs.size() > 1) {

				// More than one trip in the previous leg: Expected to be a PT trip chain.

				// "anslutningstid"
				if (!TransportMode.access_walk.equals(this.tmpLegs.getFirst().getMode())) {
					throw new RuntimeException("Expected " + TransportMode.access_walk + " but received "
							+ this.tmpLegs.getFirst().getMode() + " in the first leg.");
				}
				if (!TransportMode.egress_walk.equals(this.tmpLegs.getLast().getMode())) {
					throw new RuntimeException("Expected " + TransportMode.egress_walk + " but received "
							+ this.tmpLegs.getLast().getMode() + " in the last leg.");
				}
				final double accessEgressTime_s = this.tmpLegs.getFirst().getTravelTime()
						+ this.tmpLegs.getLast().getTravelTime();

				// "första väntetid"
				final double firstWaitingTime_s = this.tmpActs.getFirst().getEndTime()
						- this.tmpActs.getFirst().getStartTime();

				double transferTime_s = 0; // "bytestid"
				double inVehicleTime_s = 0; // "restid i fordonet"
				for (int i = 1; i < this.tmpLegs.size() - 1; i++) {
					final Leg leg = this.tmpLegs.get(i);
					if (TransportMode.transit_walk.equals(leg.getMode())) {
						transferTime_s += leg.getTravelTime();
					} else if (PT_SUBMODES.contains(leg.getMode())) {
						inVehicleTime_s += leg.getTravelTime();
					} else {
						throw new RuntimeException("Unknown PT trip-chain mode: " + leg.getMode());
					}
				}
				int numberOfChanges = 0; // "antal byten"
				for (Activity transfer : this.tmpActs) {
					numberOfChanges++;
					transferTime_s += transfer.getEndTime() - transfer.getStartTime();
				}

				final SampersPTSummaryLeg summaryLeg = new SampersPTSummaryLeg(accessEgressTime_s, firstWaitingTime_s,
						inVehicleTime_s, transferTime_s, numberOfChanges);
				super.handleLeg(summaryLeg);

			}

			super.handleActivity(act);
			this.tmpLegs.clear();
			this.tmpActs.clear();
		}
	}

	// -------------------- INNER CLASS --------------------

	class SampersPTSummaryLeg implements Leg {

		private final double accessTime_s;
		private final double firstWaitingTime_s;
		private final double inVehicleTime_s;
		private final double transferTime_s;
		private final int numberOfChanges;

		private SampersPTSummaryLeg(final double accessTime_s, final double firstWaitingTime_s,
				final double inVehicleTime_s, final double transferTime_s, final int numberOfChanges) {
			this.accessTime_s = accessTime_s;
			this.firstWaitingTime_s = firstWaitingTime_s;
			this.inVehicleTime_s = inVehicleTime_s;
			this.transferTime_s = transferTime_s;
			this.numberOfChanges = numberOfChanges;
		}

		@Override
		public String getMode() {
			return TransportMode.pt;
		}

		@Override
		public Route getRoute() {
			return new Route() {
				@Override
				public double getDistance() {
					return 0; // TODO
				}

				@Override
				public void setDistance(double distance) {
					throw new UnsupportedOperationException();
				}

				@Override
				public double getTravelTime() {
					throw new UnsupportedOperationException();
				}

				@Override
				public void setTravelTime(double travelTime) {
					throw new UnsupportedOperationException();
				}

				@Override
				public Id<Link> getStartLinkId() {
					throw new UnsupportedOperationException();
				}

				@Override
				public Id<Link> getEndLinkId() {
					throw new UnsupportedOperationException();
				}

				@Override
				public void setStartLinkId(Id<Link> linkId) {
					throw new UnsupportedOperationException();
				}

				@Override
				public void setEndLinkId(Id<Link> linkId) {
					throw new UnsupportedOperationException();
				}

				@Override
				public String getRouteDescription() {
					throw new UnsupportedOperationException();
				}

				@Override
				public void setRouteDescription(String routeDescription) {
					throw new UnsupportedOperationException();
				}

				@Override
				public String getRouteType() {
					throw new UnsupportedOperationException();
				}

				@Override
				public Route clone() {
					throw new UnsupportedOperationException();
				}
			};
		}

		@Override
		public double getTravelTime() {
			// TODO
			return (this.accessTime_s + this.firstWaitingTime_s + this.inVehicleTime_s + this.transferTime_s);
		}

		@Override
		public Attributes getAttributes() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setMode(String mode) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setRoute(Route route) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getDepartureTime() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setDepartureTime(double seconds) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setTravelTime(double seconds) {
			throw new UnsupportedOperationException();
		}
	}
}
