/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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

package playground.mmoyo.taste_variations;

import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.cadyts.pt.CadytsPtConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionAccumulator;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.functions.CharyparNagelActivityScoring;
import org.matsim.core.scoring.functions.CharyparNagelAgentStuckScoring;
import org.matsim.core.scoring.functions.CharyparNagelLegScoring;
import org.matsim.core.scoring.functions.CharyparNagelScoringParameters;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import playground.mmoyo.analysis.stopZoneOccupancyAnalysis.CtrlListener4configurableOcuppAnalysis;
import playground.mmoyo.utils.DataLoader;

public class SVD_CtrlLauncher {

	public SVD_CtrlLauncher( Scenario scn, final String svdSolutionsFile, boolean doZoneConversion){
		Population pop = scn.getPopulation();
		final Network net = scn.getNetwork();
		final TransitSchedule schedule = scn.getTransitSchedule();
		
		final Controler controler = new Controler(scn);
		controler.setOverwriteFiles(true);
		
		CadytsPtConfigGroup ccc = new CadytsPtConfigGroup() ;
		controler.getConfig().addModule(CadytsPtConfigGroup.GROUP_NAME, ccc) ;
		
		
		
	
		
//		Map <Id, SVDvalues> svdMap = new SVDValuesAsObjAttrReader(pop.getPersons().keySet()).readFile(svdSolutionsFile); 
//		controler.setScoringFunctionFactory(new SVDScoringfunctionFactory(svdMap, net, schedule));
	/////////set scoring functions/////////////////////////////// 
		
		//scoring 
		final Map <Id, SVDvalues> svdMap = new SVDValuesAsObjAttrReader(pop.getPersons().keySet()).readFile(svdSolutionsFile); 
		final CharyparNagelScoringParameters params = new CharyparNagelScoringParameters(scn.getConfig().planCalcScore()); //M
		controler.setScoringFunctionFactory(new ScoringFunctionFactory() {
			@Override
			public ScoringFunction createNewScoringFunction(Plan plan) {
			
				ScoringFunctionAccumulator scoringFunctionAccumulator = new ScoringFunctionAccumulator();
				scoringFunctionAccumulator.addScoringFunction(new CharyparNagelLegScoring(params, controler.getScenario().getNetwork()));
				scoringFunctionAccumulator.addScoringFunction(new CharyparNagelActivityScoring(params)) ;
				scoringFunctionAccumulator.addScoringFunction(new CharyparNagelAgentStuckScoring(params));
				
				//set SVD-Scoring function
				SVDvalues svdValues = svdMap.get(plan.getPerson().getId());
				SVDscoring svdScoring = new SVDscoring(plan, svdValues, net, schedule);
				scoringFunctionAccumulator.addScoringFunction(svdScoring);
 
				return scoringFunctionAccumulator;
			}
		}
		) ;
		////////////////////////////////////////////////////////////////////////////////////////////////
		
		
		
		
		//make sure plans have scores= null and that first plan is selected
		for (Person person : scn.getPopulation().getPersons().values()){
			((PersonImpl)person).setSelectedPlan(person.getPlans().get(0));
			for (Plan plan : person.getPlans()){
				plan.setScore(null);
			}
		}
		
		//add analyzer for specific bus line
		CtrlListener4configurableOcuppAnalysis ctrlListener4configurableOcuppAnalysis = new CtrlListener4configurableOcuppAnalysis(controler);
		ctrlListener4configurableOcuppAnalysis.setStopZoneConversion(doZoneConversion);
		controler.addControlerListener(ctrlListener4configurableOcuppAnalysis);			
		
		controler.run();	
	}
	
	
	public static void main(String[] args) {
		String configFile;
		String svdSolutionFile;
		String strDoZoneConversion;
		if (args.length>0){
			configFile = args[0];
			svdSolutionFile = args[1];
			strDoZoneConversion = args[2];
		}else{
			configFile = "../../";
			svdSolutionFile = "../../";
			strDoZoneConversion = "false";
		}
		
		//load data
		DataLoader dataLoader = new DataLoader();
		Scenario scn =dataLoader.loadScenario(configFile);
		boolean doZoneConversion = Boolean.parseBoolean(strDoZoneConversion);
		new SVD_CtrlLauncher(scn, svdSolutionFile, doZoneConversion );
	}
	
}