/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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
import com.google.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.otfvis.OTFVis;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControllerConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup.TrafficDynamics;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup.VspDefaultsCheckingLevel;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.ControlerListenerManager;
import org.matsim.core.controler.IterationCounter;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.mobsim.framework.events.MobsimInitializedEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimInitializedListener;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.network.NetworkChangeEvent;
import org.matsim.core.network.NetworkChangeEvent.ChangeType;
import org.matsim.core.network.NetworkChangeEvent.ChangeValue;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.replanning.strategies.KeepLastExecutedAsPlanStrategy;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.vis.otfvis.OTFClientLive;
import org.matsim.vis.otfvis.OTFVisConfigGroup;
import org.matsim.vis.otfvis.OnTheFlyServer;
import org.matsim.withinday.controller.ExecutedPlansServiceImpl;
import org.matsim.withinday.mobsim.MobsimDataProvider;
import org.matsim.withinday.trafficmonitoring.WithinDayTravelTime;

import java.util.*;

import static org.matsim.core.config.groups.QSimConfigGroup.SnapshotStyle;

/**
 * @author nagel
 *
 */
public class KNAccidentScenario {
	private static final Logger log = LogManager.getLogger(KNAccidentScenario.class ) ;
	enum RunType {base, manualDetour, bangbang, withinDayRerouting, @Deprecated day2day }
	private static final RunType runType = RunType.manualDetour;
	// (yyyyyy I think that "day2day" effectively "looks into the future". kai, jun'22)
	// (But that is what it should do, shouldn't it?  Otherwise, it would rather be something like "iterateControlAgainstSpontaneousBehavior".  kai, apr'23)
	// Which is what it should be.  kai, apr'24
	// I think I fixed that.  --??
	private static final double fractionForDetour = 1.0;

	@SuppressWarnings("unused")
	private static final String KEEP_LAST_EXECUTED = "keepLastExecuted" ;
	static final class MyIterationCounter implements IterationStartsListener {
		private int iteration;

		@Inject MyIterationCounter( ControlerListenerManager cm ) {
			cm.addControlerListener(this);
		}

		@Override public void notifyIterationStarts(IterationStartsEvent event) {
			this.iteration = event.getIteration() ;
			log.warn("got notified; iteration=" + this.iteration ) ;
		}

		final int getIteration() {
			return this.iteration;
		}
	}
	static final Id<Link> accidentLinkId = Id.createLinkId( "4706699_484108_484109-4706699_484109_26662372");
	static List<Id<Link>> replanningLinkIds = new ArrayList<>() ;

	public static void main(String[] args) {
		replanningLinkIds.add( Id.createLinkId("4068014_26836040_26836036-4068014_26836036_251045850-4068014_251045850_251045852") ) ;

		// ===

		final Config config = ConfigUtils.loadConfig("../../shared-svn/studies/countries/de/berlin/telematics/funkturm-example/baseconfig.xml") ;

		config.network().setInputFile("../../counts/iv_counts/network.xml.gz"); // note that this path is relative to path of config!
		config.network().setTimeVariantNetwork(true);

		config.plans().setInputFile("reduced-plans.xml.gz"); // relative to path of config
		config.plans().setRemovingUnneccessaryPlanAttributes(true);

		config.controller().setFirstIteration(9);
		switch ( runType ) {
			case base:
			case manualDetour:
			case bangbang:
			case withinDayRerouting:
				config.controller().setLastIteration(9);
				break;
			case day2day:
				config.controller().setLastIteration(100);
				break;
			default:
				throw new IllegalStateException( "Unexpected value: " + runType );
		}
		config.controller().setOutputDirectory("./output/telematics/funkturm-example");
		config.controller().setWriteEventsInterval(100);
		config.controller().setWritePlansInterval(100);
		config.controller().setWritePlansUntilIteration( 0 );

		config.controller().setRoutingAlgorithmType( ControllerConfigGroup.RoutingAlgorithmType.Dijkstra );

		config.qsim().setFlowCapFactor(0.04);
		config.qsim().setStorageCapFactor(0.06);
		config.qsim().setStuckTime(100.);
		config.qsim().setStartTime(6.*3600.);
//		config.qsim().setTrafficDynamics(TrafficDynamics.queue);
//		config.qsim().setSnapshotStyle( SnapshotStyle.queue );

		config.qsim().setTrafficDynamics(TrafficDynamics.kinematicWaves);
		config.qsim().setSnapshotStyle( SnapshotStyle.kinematicWaves );
		// yy these generate lots of warnings re space consumption of holes that would need to be debugged.  kai, apr'24

		{
			config.scoring().addModeParams( new ScoringConfigGroup.ModeParams( "undefined" ) );
		}
		{
			ReplanningConfigGroup.StrategySettings stratSets = new ReplanningConfigGroup.StrategySettings();
			//		stratSets.setStrategyName(DefaultSelector.KeepLastSelected.name());
			stratSets.setStrategyName( KEEP_LAST_EXECUTED );
			stratSets.setWeight( 1. );
			config.replanning().addStrategySettings( stratSets );
		}
		config.scoring().setWriteExperiencedPlans( false );

		config.vspExperimental().setVspDefaultsCheckingLevel( VspDefaultsCheckingLevel.warn );
		config.vspExperimental().setWritingOutputEvents(true);

		OTFVisConfigGroup otfConfig = ConfigUtils.addOrGetModule(config, OTFVisConfigGroup.class ) ;
		otfConfig.setAgentSize(200);
		otfConfig.setLinkWidth(10);
		otfConfig.setDrawTime(true);

		// ===

		final Scenario scenario = ScenarioUtils.loadScenario( config ) ;

//		for ( Link link : scenario.getNetwork().getLinks().values() ) {
//			if ( link.getAllowedModes().contains( TransportMode.walk ) ) {
//				Set<String> modes = new HashSet<>( link.getAllowedModes() ) ;
//				modes.add( "segway");
//				link.setAllowedModes(modes);
//			}
//		}
		// yy don't know why this is here.  Maybe leftover from some other teaching?

		preparePopulation(scenario);
		scheduleAccident(scenario);

		Link link = scenario.getNetwork().getLinks().get( Id.createLinkId( "-418375_-248_247919764" ) ) ;
		link.setCapacity(2000.); // "repair" a capacity. (This is Koenigin-Elisabeth-Str., parallel to A100 near Funkturm, having visibly less capacity than links upstream and downstream.)

		Link link2 = scenario.getNetwork().getLinks().get( Id.createLinkId("40371262_533639234_487689293-40371262_487689293_487689300-40371262_487689300_487689306-40371262_487689306_487689312-40371262_487689312_487689316-40371262_487689316_487689336-40371262_487689336_487689344-40371262_487689344_487689349-40371262_487689349_487689356-40371262_487689356_533639223-4396104_533639223_487673629-4396104_487673629_487673633-4396104_487673633_487673636-4396104_487673636_487673640-4396104_487673640_26868611-4396104_26868611_484073-4396104_484073_26868612-4396104_26868612_26662459") ) ;
		link2.setCapacity( 300. ) ;
		// reduce cap on alt route. (This is the freeway _entry_ link just downstream of the accident; reducing its capacity
		// means that the router finds a wider variety of alternative routes.)  Original capacity is 1500.

		// ===

		final Controler controler = new Controler( scenario ) ;
		controler.getConfig().controller().setOverwriteFileSetting( OverwriteFileSetting.overwriteExistingFiles ) ;

		controler.addOverridingModule( new AbstractModule(){
			@Override public void install() {

				bind( MobsimDataProvider.class ).in( Singleton.class ) ;
				addMobsimListenerBinding().to( MobsimDataProvider.class) ;

				bind( ExecutedPlansServiceImpl.class ).in(Singleton.class) ;
				addControlerListenerBinding().to( ExecutedPlansServiceImpl.class ) ;
				// (note that this is different from EXPERIENCEDPlansService! kai, apr'18)

				addPlanStrategyBinding(KEEP_LAST_EXECUTED).toProvider(KeepLastExecutedAsPlanStrategy.class) ;

				this.bind( MyIterationCounter.class ).in(Singleton.class) ;

				// These are the possible strategies.  Only some of the above bindings are needed for each of them.
				switch( runType ) {
					case base:
						this.addMobsimListenerBinding().toInstance( new MyOTFVisMobsimListener(1) );
						break;
					case manualDetour:
						this.addMobsimListenerBinding().toInstance( new ManualDetour( fractionForDetour ) );
						this.addMobsimListenerBinding().toInstance( new MyOTFVisMobsimListener(1) );
						break;
					case bangbang: {
						final WithinDayTravelTime travelTime = new WithinDayTravelTime(scenario, Collections.singleton( TransportMode.car ));

						this.addEventHandlerBinding().toInstance( travelTime ) ;
						this.addMobsimListenerBinding().toInstance( travelTime );
						this.bind( TravelTime.class ).toInstance( travelTime );

						this.addMobsimListenerBinding().to( WithinDayBangBangMobsimListener.class );

						this.addMobsimListenerBinding().toInstance( new MyOTFVisMobsimListener(1) );
						break; }
					case withinDayRerouting: {
						final WithinDayTravelTime travelTime = new WithinDayTravelTime(scenario, Collections.singleton( TransportMode.car ));

						this.addEventHandlerBinding().toInstance( travelTime ) ;
						this.addMobsimListenerBinding().toInstance( travelTime );
						this.bind( TravelTime.class ).toInstance( travelTime );

						WithinDayReRouteMobsimListener abc = new WithinDayReRouteMobsimListener();;
						//				abc.setLastReplanningIteration(9);
						abc.setReplanningProba(1.0);
						this.addMobsimListenerBinding().toInstance( abc ) ;

						this.addMobsimListenerBinding().toInstance( new MyOTFVisMobsimListener(1) );
						break; }
					case day2day: {
						// this should do within-day replanning, but based on what was good solution in iteration before.  not sure if it works.
						// kai, jun'19
						this.bind( TravelTime.class ).to( PreviousIterationCarTraveltime.class ).in( Singleton.class ) ;

						IterativeWithinDayReRouteMobsimListener abc = new IterativeWithinDayReRouteMobsimListener();;
						this.addMobsimListenerBinding().toInstance( abc ) ;

						this.addMobsimListenerBinding().toInstance( new MyOTFVisMobsimListener(10) );
						break; }
					default:
						throw new IllegalStateException( "Unexpected value: " + runType );
				}


			}

		}) ;

		// ===

		controler.run() ;

	}

	private static void scheduleAccident(final Scenario scenario) {
		List<NetworkChangeEvent> events = new ArrayList<>() ;
		{
			NetworkChangeEvent event = new NetworkChangeEvent(8*3600.) ;
			event.addLink( scenario.getNetwork().getLinks().get( accidentLinkId ) ) ;
			ChangeValue change = new ChangeValue( ChangeType.FACTOR, 0.1 ) ;
			event.setFlowCapacityChange(change);
			ChangeValue lanesChange = new ChangeValue( ChangeType.FACTOR, 0.1 ) ;
			event.setLanesChange(lanesChange);
			events.add(event) ;
		}
		{
			NetworkChangeEvent event = new NetworkChangeEvent(9*3600.) ;
			event.addLink( scenario.getNetwork().getLinks().get( accidentLinkId ) );
			ChangeValue change = new ChangeValue( ChangeType.FACTOR, 10. ) ;
			event.setFlowCapacityChange(change);
			ChangeValue lanesChange = new ChangeValue( ChangeType.FACTOR, 10. ) ;
			event.setLanesChange(lanesChange);
			events.add(event) ;
		}
		NetworkUtils.setNetworkChangeEvents( scenario.getNetwork(), events );
	}

	private static void preparePopulation(final Scenario scenario) {
		for ( Iterator<? extends Person> it = scenario.getPopulation().getPersons().values().iterator() ; it.hasNext() ; ) {
			Person person = it.next() ;
			boolean retain = false ;
			for ( Leg leg : TripStructureUtils.getLegs( person.getSelectedPlan() ) ) {
				if ( leg.getRoute() instanceof NetworkRoute ) {
					NetworkRoute route = (NetworkRoute) leg.getRoute() ;
					if ( route.getLinkIds().contains( accidentLinkId ) ) {
						retain = true ;
					}
				}
			}
			if ( !retain ) {
				it.remove();
			}
		}
	}

	private static class PreviousIterationCarTraveltime implements TravelTime {
		@Inject private Map<String,TravelTime> travelTimes ;
		@Override
		public double getLinkTravelTime( Link link, double time, Person person, Vehicle vehicle ){
			return travelTimes.get( TransportMode.car ).getLinkTravelTime( link, time, person, vehicle ) ;
		}
	}

	private static class MyOTFVisMobsimListener implements MobsimInitializedListener{
		// this is here so we can control in which iteration this is called. kai, apr'24
		private final int everyNIterations;
		@com.google.inject.Inject Scenario scenario ;
		@com.google.inject.Inject EventsManager events ;
//		@com.google.inject.Inject(optional=true) OnTheFlyServer.NonPlanAgentQueryHelper nonPlanAgentQueryHelper;
		@com.google.inject.Inject IterationCounter iterationCounter;
		 MyOTFVisMobsimListener( int everyNIterations ){
			this.everyNIterations = everyNIterations;
		}

		@Override public void notifyMobsimInitialized( MobsimInitializedEvent e ){
			log.warn( " !!!OTFVis only every 10th iteration!!!");
			if( (iterationCounter.getIterationNumber() + 1) % everyNIterations == 0 ) {
				QSim qsim = (QSim) e.getQueueSimulation();
				OnTheFlyServer server = OTFVis.startServerAndRegisterWithQSim( scenario.getConfig(), scenario, events, qsim );
				OTFClientLive.run( scenario.getConfig(), server );
			}
		}
	}

}
