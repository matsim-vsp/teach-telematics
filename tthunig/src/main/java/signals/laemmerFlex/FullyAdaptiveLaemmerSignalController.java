/* *********************************************************************** *
 * project: org.matsim.*
 * DgTaController
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
package signals.laemmerFlex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.signals.controller.AbstractSignalController;
import org.matsim.contrib.signals.controller.SignalController;
import org.matsim.contrib.signals.controller.SignalControllerFactory;
import org.matsim.contrib.signals.controller.laemmerFix.LaemmerConfigGroup;
import org.matsim.contrib.signals.controller.laemmerFix.LaemmerConfigGroup.Regime;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalGroup;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.contrib.signals.sensor.DownstreamSensor;
import org.matsim.contrib.signals.sensor.LinkSensorManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.mobsim.qsim.interfaces.SignalGroupState;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;

import com.google.inject.Inject;


/**
 * @author tthunig, pschade based on LaemmerSignalController by nkuehnel
 */
public final class FullyAdaptiveLaemmerSignalController extends AbstractSignalController implements SignalController {

	public static final String IDENTIFIER = "FullyAdaptiveLaemmerSignalController";

	final LaemmerConfigGroup laemmerConfig;
	private final List<LaemmerPhase> laemmerPhases = new ArrayList<>();

	Request activeRequest = null;
	LinkSensorManager sensorManager;

	DownstreamSensor downstreamSensor;

	final Network network;
	final Lanes lanes;
	final Config config;
	final SignalsData signalsData;

	double tIdle;

	double maximumSystemsOutflowSum;

	private ArrayList<SignalPhase> signalPhases;
	private List<LaemmerApproach> laemmerApproaches = new LinkedList<>();
	private Queue<LaemmerApproach> approachesForStabilization = new LinkedList<>();

	private LaemmerPhase regulationPhase;

	private boolean debug = false;

	private AbstractStabilizationStrategy stabilisator;

	private int estNumOfPhases;

	private boolean switchSignal; //schedules an update of activeRequest if stabilization selects same phase that was already selected 
	
	private SignalCombinationBasedOnConflicts signalCombinationConflicts;

	public final static class LaemmerFlexFactory implements SignalControllerFactory {
		@Inject private LinkSensorManager sensorManager;
		@Inject private DownstreamSensor downstreamSensor;
		@Inject private Scenario scenario;

		@Override
		public SignalController createSignalSystemController(SignalSystem signalSystem) {
			SignalController controller = new FullyAdaptiveLaemmerSignalController(scenario, sensorManager, downstreamSensor);
			controller.setSignalSystem(signalSystem);
			return controller;
		}
	}

	private FullyAdaptiveLaemmerSignalController(Scenario scenario, LinkSensorManager sensorManager, DownstreamSensor downstreamSensor) {
		this.sensorManager = sensorManager;
		this.network = scenario.getNetwork();
		this.lanes = scenario.getLanes();
		this.config = scenario.getConfig();
        this.laemmerConfig = ConfigUtils.addOrGetModule(config, LaemmerConfigGroup.class);
		this.signalsData = (SignalsData) scenario.getScenarioElement(SignalsData.ELEMENT_NAME);
		this.downstreamSensor = downstreamSensor;
		try {
			switch (laemmerConfig.getStabilizationStrategy()) {
			case HEURISTIC:
				this.stabilisator = new StabStratHeuristic(this, network, lanes);
				((StabStratHeuristic) stabilisator).setSignalCombinationTool(this.signalCombinationConflicts);
				break;
			case USE_MAX_LANECOUNT:
				this.stabilisator = new StabStratMaxLaneCount(this, network, lanes);
				break;
			case COMBINE_SIMILAR_REGULATIONTIME:
				this.stabilisator = new StabStratCombineSimilarRegulationTime(this, network, lanes);
				break;
			case PRIORIZE_HIGHER_POSITIONS:
				this.stabilisator = new StabStratPriorizeHigherPositions(this, network, lanes);
				break;
			}
		} catch (Exception e) {	e.printStackTrace(); }
	}

	@Override
	public void simulationInitialized(double simStartTimeSeconds) {
		List<Double> maximumLaneOutflows = new LinkedList<>();
		laemmerApproaches = new LinkedList<>();
		this.estNumOfPhases = LaemmerUtils.estimateNumberOfPhases(system, network, signalsData);
		this.initializeSensoring();

		for (SignalGroup sg : this.system.getSignalGroups().values()) {
			this.system.scheduleDropping(simStartTimeSeconds, sg.getId());
			//schedule dropping will only schedule a dropping but not process it intermediately 
			sg.setState(SignalGroupState.RED);
			for (Signal signal : sg.getSignals().values()) {
				if (signal.getLaneIds() == null || signal.getLaneIds().isEmpty()) {
					LaemmerApproach laemmerLane = new LaemmerApproach(network.getLinks().get(signal.getLinkId()), sg, signal, this);
					laemmerApproaches.add(laemmerLane);
					maximumLaneOutflows.add(laemmerLane.getMaxOutflow());
				} else {
					Link link = network.getLinks().get(signal.getLinkId());
					for (Id<Lane> laneId : signal.getLaneIds()) {
						LaemmerApproach laemmerLane = new LaemmerApproach(link, lanes.getLanesToLinkAssignments().get(link.getId()).getLanes().get(laneId),
								sg, signal, this);
						laemmerApproaches.add(laemmerLane);
						maximumLaneOutflows.add(laemmerLane.getMaxOutflow());
					}
				}
			}
		}
		//sum the maximum n lanes for systems outflow maximum
		maximumSystemsOutflowSum = maximumLaneOutflows.stream().sorted(Comparator.comparingDouble(Double::doubleValue).reversed()).limit(this.estNumOfPhases).collect(Collectors.summingDouble(Double::doubleValue));
		// create all possible signal combinations based on conflict data
		this.signalCombinationConflicts = new SignalCombinationBasedOnConflicts(signalsData, system, network, lanes);
		this.signalPhases = signalCombinationConflicts.createSignalCombinations();

		if (laemmerConfig.isRemoveSubPhases()) {
			this.signalPhases = LaemmerUtils.removeRedundantSubPhases(this.signalPhases);
			if(debug)
				System.out.println("after remove subphases: " + this.signalPhases.size());
		}

		for (SignalPhase signalPhase : signalPhases) {
			LaemmerPhase laemmerPhase = new LaemmerPhase(this, signalPhase);
			laemmerPhases.add(laemmerPhase);
		}
	}

	private void initializeSensoring() {
		for (SignalGroup group : this.system.getSignalGroups().values()) {
			for (Signal signal : group.getSignals().values()) {
				if (signal.getLaneIds() != null && !(signal.getLaneIds().isEmpty())) {
					for (Id<Lane> laneId : signal.getLaneIds()) {
						this.sensorManager.registerNumberOfCarsOnLaneInDistanceMonitoring(signal.getLinkId(), laneId, 0.);
						this.sensorManager.registerAverageNumberOfCarsPerSecondMonitoringOnLane(signal.getLinkId(), laneId, this.laemmerConfig.getLookBackTime(), this.laemmerConfig.getTimeBucketSize());
					}
				}
				//always register link in case only one lane is specified (-> no LaneEnter/Leave-Events?), xy
				//moved this to next for-loop, unsure, if this is still needed, pschade Nov'17 
				this.sensorManager.registerNumberOfCarsInDistanceMonitoring(signal.getLinkId(), 0.);
				this.sensorManager.registerAverageNumberOfCarsPerSecondMonitoring(signal.getLinkId(), this.laemmerConfig.getLookBackTime(), this.laemmerConfig.getTimeBucketSize());
			}
		}
		//moved here from above, pschade Nov'17
		for (Link link : this.network.getLinks().values()) {
			this.sensorManager.registerNumberOfCarsInDistanceMonitoring(link.getId(), 0.);
			this.sensorManager.registerAverageNumberOfCarsPerSecondMonitoring(link.getId(), this.laemmerConfig.getLookBackTime(), this.laemmerConfig.getTimeBucketSize());
		}
		if (laemmerConfig.isCheckDownstream()){
			downstreamSensor.registerDownstreamSensors(system);
		}
	}

	@Override
	public void updateState(double now) {
		if (laemmerConfig.getActiveRegime().equals(LaemmerConfigGroup.Regime.COMBINED) ||
				laemmerConfig.getActiveRegime().equals(LaemmerConfigGroup.Regime.STABILIZING)) {
			updateActiveRegulation(now);
		}
		updatePhasesAndLanes(now);
		// TODO test what happens, when I move this up to the first line of this method. should save runtime. tt, dez'17
		// note: stabilization has still to be done to increment 'a'... tt, dez'17
		// another note: if we move this up, new lanes which need to be stabilized will only be
		// added to stabilization queue after processing a new request and won't be in
		// the same order as they were added during the process. But the influence of it
		// shouldn't be that big???, pschade, Dec'17
		if(activeRequest != null && activeRequest.laemmerPhase.phase.getState().equals(SignalGroupState.GREEN)) {
			double remainingMinG = activeRequest.onsetTime + laemmerConfig.getMinGreenTime() - now;
			if (remainingMinG > 0) {
				if (debug) {
					System.out.println("Not selecting new signal, remainingMinG="+remainingMinG);
				}
				return;
			}
		}
		if ((laemmerConfig.getActiveRegime().equals(LaemmerConfigGroup.Regime.COMBINED) ||
				laemmerConfig.getActiveRegime().equals(LaemmerConfigGroup.Regime.STABILIZING))
				&& regulationPhase == null && approachesForStabilization.size() > 0) {
			switchSignal = true;
			regulationPhase = findBestPhaseForStabilization();
			calculateTidle(now);
			double extraTime = (approachesForStabilization.peek().getMaxOutflow() / this.maximumSystemsOutflowSum) * this.tIdle;
			approachesForStabilization.peek().extendRegulationTime(extraTime);
		}
		LaemmerPhase selection = selectSignal();
		processSelection(now, selection);
	}

	/**
	 * Calculate tIdle from
	 * 	(a) lane with highest load in regulationPhase and
	 *  (b) lanes with the highest load.
	 * Number of (b) according to the estimated number of phases for this signalSystem
	 * @param now
	 */
	private void calculateTidle(double now) {
		tIdle = laemmerConfig.getDesiredCycleTime();
		//representive Lane for current selection
		LaemmerApproach representiveLane = laemmerApproaches.parallelStream()
				.filter(ll -> regulationPhase.getPhase().getGreenSignalGroups().contains(ll.getSignalGroup().getId()))
				.max(Comparator.comparingDouble(LaemmerApproach::getDeterminingLoad).reversed())
				.get();
		tIdle -= Math.max(representiveLane.getDeterminingLoad() * laemmerConfig.getDesiredCycleTime() + laemmerConfig.getIntergreenTime(), laemmerConfig.getMinGreenTime());
		//get all laemmerLanes and keep only the lanes which are not in the current phase
		if (laemmerConfig.isDetermineMaxLoadForTIdleGroupedBySignals()) {
			//implementation of grouping by values adapted from https://marcin-chwedczuk.github.io/grouping-using-java-8-streams
			Collection<Double> maxLoadFromLaneForEachSignal = laemmerApproaches.parallelStream()
					//filter by phases whos signalgroups are in current regulation phase
					.filter(ll->!regulationPhase.getPhase().getGreenSignalGroups().contains(ll.getSignalGroup().getId()))
					//grouping by signal and keeps the maximum determined load for each signal
					.collect(Collectors.groupingBy(LaemmerApproach::getSignal,
							Collectors.mapping(LaemmerApproach::getDeterminingLoad,
									//collect the max values and unbox Doubles from Optional
									Collectors.collectingAndThen(Collectors.maxBy(Double::compare), Optional::get))))
					.values();
			tIdle -= maxLoadFromLaneForEachSignal.parallelStream()
					//sort the loads desc and keep only numOfPhases-1 values
					.sorted(Comparator.reverseOrder())
					.limit(this.estNumOfPhases-1)
					//sum the parts of tIdle
					.collect(Collectors.summingDouble(determinigLoad->
					Math.max(determinigLoad * laemmerConfig.getDesiredCycleTime()+laemmerConfig.getIntergreenTime(),laemmerConfig.getMinGreenTime())
							))
					.doubleValue();
		}
		else {
			tIdle -= laemmerApproaches.parallelStream()
					//filter by phases whos signalgroups are in current regulation phase
					.filter(ll->!regulationPhase.getPhase().getGreenSignalGroups().contains(ll.getSignalGroup().getId()))
					//sort by determining load and keep only the upper half of the list of lammerlanes
					.sorted(Comparator.comparingDouble(LaemmerApproach::getDeterminingLoad).reversed())
					.limit(this.estNumOfPhases-1)
					//sum the parts of tIdle
					.collect(Collectors.summingDouble(ll->
					Math.max(ll.getDeterminingLoad() * laemmerConfig.getDesiredCycleTime()+laemmerConfig.getIntergreenTime(),laemmerConfig.getMinGreenTime())
							))
					.doubleValue();
		}
		tIdle = Math.max(0, tIdle);
	}

	/**
	 * Method will check if (a) currently a request is processed, (b) there is
	 * currently a need for stabilization and (c) the current request equals the
	 * peek of the regulation queue. <br>
	 * If these prerequisites are fulfilled, the current phase will be removed from
	 * the regulation queue, if the regulationTime is already passed or there aren't
	 * any more cars waiting to be processed.
	 * 
	 * @param now
	 */
	private void updateActiveRegulation(double now) {
		switchSignal = false;
		if (this.debug && approachesForStabilization.size() > 0) {
			System.out.println("regTime: "+approachesForStabilization.peek().getRegulationTime() + ", passed: "+ (now -activeRequest.onsetTime));
		}
		if (activeRequest != null && regulationPhase != null && regulationPhase.equals(activeRequest.laemmerPhase)) {
			int n = 1;
			//only end stabilization if at least minG has passed
			if (now - activeRequest.onsetTime >= laemmerConfig.getMinGreenTime()) {
				if (approachesForStabilization.peek().getLane() != null) {
					n = getNumberOfExpectedVehiclesOnLane(now, approachesForStabilization.peek().getLink().getId(), approachesForStabilization.peek().getLane().getId());
				} else {
					n = getNumberOfExpectedVehiclesOnLink(now, approachesForStabilization.peek().getLink().getId());
				} 
			}
			if (approachesForStabilization.peek().getRegulationTime() + activeRequest.onsetTime - now <= 0 || n == 0) {
				if(debug) {
					System.out.println("regulation time over or link/lane empty(n="+n+"), ending stabilization.");
				}
				endStabilization(now);
			}
		}
	}

	/**
	 * removes all successfully stabilized lanes from stabilization queue and
	 * shorten the regulation time of lanes, which are stabilized alongside the peek
	 * of the stabilization queue but still have pending stabilization time left.
	 * Also clears the selected/generated regulation phase.
	 * 
	 * @param now
	 */
	private void endStabilization(double now) {
		double passedRegulationTime = Math.max(now - activeRequest.onsetTime, 0.0);
		List<LaemmerApproach> markedForRemove = new ArrayList<>(approachesForStabilization.size());
		for (LaemmerApproach ll : approachesForStabilization) {
			if (activeRequest.laemmerPhase.phase.containsGreenLane(ll.getLink().getId(), (ll.getLane() == null ? null : ll.getLane().getId()))) {
				if(ll.getRegulationTime() <= passedRegulationTime
						|| (ll.getLane() != null && getNumberOfExpectedVehiclesOnLane(now, ll.getLink().getId(), ll.getLane().getId()) == 0)
						|| (ll.getLane() == null && getNumberOfExpectedVehiclesOnLink(now, ll.getLink().getId()) == 0)) {
					//remove all Lanes from regulationQueue when their regulation time > regulationTime of regulationPhase
					markedForRemove.add(ll);
					if(debug) {
						System.out.println("removing "+ll.getLink().getId().toString()+"-"+(ll.getLane() != null ? ll.getLane().getId().toString() : "null"));
					}
				} else {
					//Subtract regulationTime of all lanes if regulation time not > regulation time of current phase
					ll.shortenRegulationTime(passedRegulationTime);
					if(debug) {
						System.out.println("shorten time for "+ll.getLink()+"-"+ll.getLane()+" by "+passedRegulationTime);
					}
				}
			}
		}
		approachesForStabilization.removeAll(markedForRemove);
		//set regulationPhase null
		regulationPhase = null;
		switchSignal = true;
	}

	private void updatePhasesAndLanes(double now) {
		if(regulationPhase == null && (laemmerConfig.getActiveRegime().equals(Regime.COMBINED) || laemmerConfig.getActiveRegime().equals(Regime.OPTIMIZING))) {
			for (LaemmerPhase phase : laemmerPhases) {
				phase.updateAbortationPenaltyAndPriorityIndex(now);
			}
		}

		if(laemmerConfig.getActiveRegime().equals(Regime.COMBINED) || laemmerConfig.getActiveRegime().equals(Regime.STABILIZING)) {
			for (LaemmerApproach l : laemmerApproaches) {
				l.calcLoadAndArrivalrate(now);
				l.updateStabilization(now);
			}
		}
	}


	private LaemmerPhase findBestPhaseForStabilization() {
		if (this.debug ) {
			System.out.println("tIdle: " + tIdle);
			System.out.print("stabilizationQueue: ");
			for (LaemmerApproach ll : approachesForStabilization) {
				System.out.print(ll.getLink().getId()+"-"+(ll.getLane() != null ? ll.getLane().getId(): "null") + "(" + ll.getRegulationTime() + "), ");
			}
			System.out.print("\n");
		}
		LaemmerPhase max = stabilisator.determinePhase(approachesForStabilization, laemmerPhases, debug);
		return max;
	}

	private LaemmerPhase selectSignal() {
		LaemmerPhase max = null;

		//selection if stabilization is needed
		if (laemmerConfig.getActiveRegime().equals(LaemmerConfigGroup.Regime.COMBINED)
				|| laemmerConfig.getActiveRegime().equals(LaemmerConfigGroup.Regime.STABILIZING)) {
			max = regulationPhase;
		}

		//selection for optimizing
		if ((laemmerConfig.getActiveRegime().equals(LaemmerConfigGroup.Regime.COMBINED)
				|| laemmerConfig.getActiveRegime().equals(LaemmerConfigGroup.Regime.OPTIMIZING)) && max == null) {
			double index = 0;
			for (LaemmerPhase phase : laemmerPhases) {
				if (phase.index > index) {
					// if downstream check enabled, only select signals that do not lead to occupied
					// links
					boolean isAllDownstramLinksEmpty = true;
					if (laemmerConfig.isCheckDownstream()) {
						for (Id<SignalGroup> sg : phase.phase.getGreenSignalGroups()) {
							isAllDownstramLinksEmpty &= downstreamSensor.allDownstreamLinksEmpty(system.getId(), sg);
						}
					}
					if (isAllDownstramLinksEmpty) {
						max = phase;
						index = phase.index;
					}
				}
			}
		}
		return max;
	}

	private void processSelection(double now, LaemmerPhase selection) {
		/* quit the active request, when the next selection is different from the current (activeRequest)
		 * or, when the next selection is null
		 */
		if (activeRequest != null && ( selection == null || (!selection.equals(activeRequest.laemmerPhase) || switchSignal))) {
			// only schedule a dropping when the signals are already green, tthunig(?)
			// Since signals can show green during the intergreen-time when they are in two stabilization phases, they need to be dropped if their queue
			// is emptied before the intergreen time passed. So its necessary to schedule a drop always. pschade, Feb'18
			//        	if(activeRequest.onsetTime < now) {
			for (Id<SignalGroup> sg : activeRequest.laemmerPhase.phase.getGreenSignalGroups()) {
				// if there is an selection drop only the signals which not included in current (new) selection
				if (selection == null || !(selection.equals(regulationPhase) && selection.phase.getGreenSignalGroups().contains(sg))) {
					this.system.scheduleDropping(now, sg);
				} else if (debug) {
					System.err.println("not dropping "+sg.toString()+", "+(selection==null?"selection=null, ":"")+((selection == regulationPhase && selection.phase.getGreenSignalGroups().contains(sg))?"is in next regulation phase":""));
				}
			}
			//        	}
			activeRequest = null;
		}

		if (activeRequest == null && selection != null) {
			activeRequest = new Request(now + laemmerConfig.getIntergreenTime(), selection);
		}

		if (activeRequest != null && activeRequest.isDue(now)) {
			//shorten regulationTime by intergreenTime if signal was green during intergreenTime
			if (laemmerConfig.getShortenStabilizationAfterIntergreenTime() && activeRequest.laemmerPhase.equals(regulationPhase)) {
				List<LaemmerApproach> markForRemove = new LinkedList<>();
				for (LaemmerApproach l : approachesForStabilization) {
					if (l.getSignalGroup().getState().equals(SignalGroupState.GREEN)) {
						// regulation time here will be shorten only, approaches will not removed. since
						// they included in the next phase, they will be removed after minG
						l.shortenRegulationTime(laemmerConfig.getIntergreenTime());

					}
				}
				approachesForStabilization.removeAll(markForRemove);
			}

			for (Id<SignalGroup> sg : activeRequest.laemmerPhase.phase.getGreenSignalGroups()) {
				this.system.scheduleOnset(now, sg);
			}
		}
	}    

	int getNumberOfExpectedVehiclesOnLink(double now, Id<Link> linkId) {
		return this.sensorManager.getNumberOfCarsInDistance(linkId, 0., now);
	}

	int getNumberOfExpectedVehiclesOnLane(double now, Id<Link> linkId, Id<Lane> laneId) {
		if (lanes.getLanesToLinkAssignments().get(linkId).getLanes().size() == 1) {
			return getNumberOfExpectedVehiclesOnLink(now, linkId);
		} else {
			return this.sensorManager.getNumberOfCarsInDistanceOnLane(linkId, laneId, 0., now);
		}
	}

	double getAverageArrivalRate(double now, Id<Link> linkId) {
		if (this.laemmerConfig.getLinkArrivalRate(linkId) != null) {
			return this.laemmerConfig.getLinkArrivalRate(linkId);
		} else {
			return this.sensorManager.getAverageArrivalRateOnLink(linkId, now);
		}
	}

	double getAverageLaneArrivalRate(double now, Id<Link> linkId, Id<Lane> laneId) {
		if (lanes.getLanesToLinkAssignments().get(linkId).getLanes().size() > 1) {
			if (this.laemmerConfig.getLaneArrivalRate(linkId, laneId) != null) {
				return this.laemmerConfig.getLaneArrivalRate(linkId, laneId);
			} else {
				return this.sensorManager.getAverageArrivalRateOnLane(linkId, laneId, now);
			}
		} else {
			return getAverageArrivalRate(now, linkId);
		}
	}

	public SignalSystem getSystem() {
		return this.system;
	}

	public void addLaneForStabilization(LaemmerApproach laemmerLane) {
		if (!needStabilization(laemmerLane)) {
			approachesForStabilization.add(laemmerLane);
		}
	}

	public boolean needStabilization(LaemmerApproach laemmerLane) {
		return approachesForStabilization.contains(laemmerLane);
	}

	public LaemmerConfigGroup getLaemmerConfig() {
		return this.laemmerConfig;
	}

	public boolean getDebug() {
		return debug;
	}

	/** helper class for scheduling a new request 
	 * @author nkuehnel **/
	class Request {
		/** time at which the laemmer signal is planned to show green */
		final double onsetTime;
		final LaemmerPhase laemmerPhase;

		Request(double onsetTime, LaemmerPhase laemmerPhase) {
			this.laemmerPhase = laemmerPhase;
			this.onsetTime = onsetTime;
		}

		private boolean isDue(double now) {
			return now == this.onsetTime;
		}
	}

}
