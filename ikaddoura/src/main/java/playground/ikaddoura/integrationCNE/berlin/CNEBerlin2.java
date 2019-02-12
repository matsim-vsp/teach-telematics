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

/**
 * 
 */
package playground.ikaddoura.integrationCNE.berlin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.decongestion.DecongestionConfigGroup;
import org.matsim.contrib.decongestion.DecongestionConfigGroup.DecongestionApproach;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.contrib.noise.NoiseConfigGroup;
import org.matsim.contrib.noise.data.NoiseAllocationApproach;
import org.matsim.contrib.noise.utils.MergeNoiseCSVFile;
import org.matsim.contrib.noise.utils.ProcessNoiseImmissions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;

import playground.ikaddoura.analysis.detailedPersonTripAnalysis.old.PersonTripCongestionNoiseAnalysisRun;
import playground.ikaddoura.integrationCNE.CNEIntegration;
import playground.ikaddoura.integrationCNE.CNEIntegration.CongestionTollingApproach;
import playground.ikaddoura.moneyTravelDisutility.data.BerlinAgentFilter;
import playground.vsp.airPollution.exposure.GridTools;
import playground.vsp.airPollution.exposure.ResponsibilityGridTools;

/**
 * run class for DZ's berlin scenario, 1 pct sample size
 * 
 * @author ikaddoura
 *
 */

public class CNEBerlin2 {
	private static final Logger log = Logger.getLogger(CNEBerlin2.class);
	
	private final double xMin = 4565039.;
	private final double xMax = 4632739.; 
	private final double yMin = 5801108.; 
	private final double yMax = 5845708.; 
	
	private final Double timeBinSize = 3600.;
	private final int noOfTimeBins = 30;

	private static String outputDirectory;
	private static String configFile;

	private static boolean congestionPricing;
	private static boolean noisePricing;
	private static boolean airPollutionPricing;
	
	private static double sigma;
	
	private static CongestionTollingApproach congestionTollingApproach;
	private static double kP;
		
	public static void main(String[] args) throws IOException {
		
		if (args.length > 0) {
			
			outputDirectory = args[0];
			log.info("Output directory: " + outputDirectory);
			
			configFile = args[1];
			log.info("Config file: " + configFile);
			
			congestionPricing = Boolean.parseBoolean(args[2]);
			log.info("Congestion Pricing: " + congestionPricing);
			
			noisePricing = Boolean.parseBoolean(args[3]);
			log.info("Noise Pricing: " + noisePricing);
			
			airPollutionPricing = Boolean.parseBoolean(args[4]);
			log.info("Air poullution Pricing: " + airPollutionPricing);
			
			sigma = Double.parseDouble(args[5]);
			log.info("Sigma: " + sigma);
			
			String congestionTollingApproachString = args[6];
			
			if (congestionTollingApproachString.equals(CongestionTollingApproach.QBPV3.toString())) {
				congestionTollingApproach = CongestionTollingApproach.QBPV3;
			} else if (congestionTollingApproachString.equals(CongestionTollingApproach.QBPV9.toString())) {
				congestionTollingApproach = CongestionTollingApproach.QBPV9;
			} else if (congestionTollingApproachString.equals(CongestionTollingApproach.DecongestionPID.toString())) {
				congestionTollingApproach = CongestionTollingApproach.DecongestionPID;
			} else if (congestionTollingApproachString.equals(CongestionTollingApproach.DecongestionBangBang.toString())) {
				congestionTollingApproach = CongestionTollingApproach.DecongestionBangBang;
			} else {
				throw new RuntimeException("Unknown congestion pricing approach. Aborting...");
			}
			log.info("Congestion Tolling Approach: " + congestionTollingApproach);
			
			kP = Double.parseDouble(args[7]);
			log.info("kP: " + kP);
			
		} else {
			
			outputDirectory = "../../../runs-svn/cne/berlin-dz-1pct/output/test/";
			configFile = "../../../runs-svn/cne/berlin-dz-1pct/input/config_m_r.xml";
			
			congestionPricing = true;
			noisePricing = true;
			airPollutionPricing = true;
			
			sigma = 0.;
			
			congestionTollingApproach = CongestionTollingApproach.DecongestionPID;
			kP = 2 * ( 10 / 3600. );			
		}
				
		CNEBerlin2 cnControler = new CNEBerlin2();
		cnControler.run();
	}

	public void run() {
						
		Config config = ConfigUtils.loadConfig(configFile, new EmissionsConfigGroup(), new NoiseConfigGroup(), new DecongestionConfigGroup());
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Controler controler = new Controler(scenario);
		
		if (outputDirectory != null) {
			controler.getScenario().getConfig().controler().setOutputDirectory(outputDirectory);
		}
		
		// air pollution Berlin settings
		
		int noOfXCells = 677;
		int noOfYCells = 446;
		GridTools gt = new GridTools(scenario.getNetwork().getLinks(), xMin, xMax, yMin, yMax, noOfXCells, noOfYCells);
		ResponsibilityGridTools rgt = new ResponsibilityGridTools(timeBinSize, noOfTimeBins, gt);

		EmissionsConfigGroup emissionsConfigGroup =  (EmissionsConfigGroup) controler.getConfig().getModules().get(EmissionsConfigGroup.GROUP_NAME);
		emissionsConfigGroup.setConsideringCO2Costs(true);
		
		// noise Berlin settings
		
		NoiseConfigGroup noiseParameters =  (NoiseConfigGroup) controler.getConfig().getModules().get(NoiseConfigGroup.GROUP_NAME);
		noiseParameters.setTimeBinSizeNoiseComputation(timeBinSize);
		
		noiseParameters.setReceiverPointGap(100.);
		
		String[] consideredActivitiesForReceiverPointGrid = {""};
		noiseParameters.setConsideredActivitiesForReceiverPointGridArray(consideredActivitiesForReceiverPointGrid);
		noiseParameters.setReceiverPointsGridMinX(xMin);
		noiseParameters.setReceiverPointsGridMaxX(xMax);
		noiseParameters.setReceiverPointsGridMinY(yMin);
		noiseParameters.setReceiverPointsGridMaxY(yMax);
			
		String[] consideredActivitiesForDamages = {"home", "work", "other"};
		noiseParameters.setConsideredActivitiesForDamageCalculationArray(consideredActivitiesForDamages);
		
		String[] hgvIdPrefixes = { "lkw" };
		noiseParameters.setHgvIdPrefixesArray(hgvIdPrefixes);
						
		noiseParameters.setNoiseAllocationApproach(NoiseAllocationApproach.MarginalCost);
				
		noiseParameters.setScaleFactor(100.);
		noiseParameters.setComputeAvgNoiseCostPerLinkAndTime(false);
		
		Set<Id<Link>> tunnelLinkIDs = new HashSet<Id<Link>>();
		tunnelLinkIDs.add(Id.create("42341", Link.class));
		tunnelLinkIDs.add(Id.create("42340", Link.class));
		
		tunnelLinkIDs.add(Id.create("43771", Link.class));
		tunnelLinkIDs.add(Id.create("43770", Link.class));
		
		tunnelLinkIDs.add(Id.create("85818", Link.class));
		tunnelLinkIDs.add(Id.create("85831", Link.class));
		
		tunnelLinkIDs.add(Id.create("84478", Link.class));
		tunnelLinkIDs.add(Id.create("46127", Link.class));
		tunnelLinkIDs.add(Id.create("97243", Link.class));
		tunnelLinkIDs.add(Id.create("88927", Link.class));
		tunnelLinkIDs.add(Id.create("106000", Link.class));
		tunnelLinkIDs.add(Id.create("34744", Link.class));
		tunnelLinkIDs.add(Id.create("84490", Link.class));
		tunnelLinkIDs.add(Id.create("84484", Link.class));
		
		tunnelLinkIDs.add(Id.create("128294", Link.class));
		tunnelLinkIDs.add(Id.create("128314", Link.class));
		
		tunnelLinkIDs.add(Id.create("87838", Link.class));
		tunnelLinkIDs.add(Id.create("104345", Link.class));
		tunnelLinkIDs.add(Id.create("104366", Link.class));
		tunnelLinkIDs.add(Id.create("87837", Link.class));
		
		tunnelLinkIDs.add(Id.create("107405", Link.class));
		tunnelLinkIDs.add(Id.create("93049", Link.class));
		tunnelLinkIDs.add(Id.create("90684", Link.class));
		tunnelLinkIDs.add(Id.create("93050", Link.class));
		tunnelLinkIDs.add(Id.create("98819", Link.class));
		tunnelLinkIDs.add(Id.create("107382", Link.class));
		tunnelLinkIDs.add(Id.create("98825", Link.class));
		tunnelLinkIDs.add(Id.create("107402", Link.class));
		tunnelLinkIDs.add(Id.create("8029", Link.class));
		tunnelLinkIDs.add(Id.create("60859", Link.class));
		tunnelLinkIDs.add(Id.create("60858", Link.class));
		tunnelLinkIDs.add(Id.create("107401", Link.class));
		tunnelLinkIDs.add(Id.create("90687", Link.class));
		tunnelLinkIDs.add(Id.create("8029", Link.class));
		
		tunnelLinkIDs.add(Id.create("72810", Link.class));
		tunnelLinkIDs.add(Id.create("80104", Link.class));
		tunnelLinkIDs.add(Id.create("72804", Link.class));
		tunnelLinkIDs.add(Id.create("73609", Link.class));
		tunnelLinkIDs.add(Id.create("13366", Link.class));
		tunnelLinkIDs.add(Id.create("80101", Link.class));
		tunnelLinkIDs.add(Id.create("80103", Link.class));
		tunnelLinkIDs.add(Id.create("38562", Link.class));
		tunnelLinkIDs.add(Id.create("38563", Link.class));
		tunnelLinkIDs.add(Id.create("80100", Link.class));
		tunnelLinkIDs.add(Id.create("13367", Link.class));
		tunnelLinkIDs.add(Id.create("72801", Link.class));
	
		tunnelLinkIDs.add(Id.create("131154", Link.class));
		tunnelLinkIDs.add(Id.create("131159", Link.class));
		
		tunnelLinkIDs.add(Id.create("123820", Link.class));
		tunnelLinkIDs.add(Id.create("123818", Link.class));
		
		tunnelLinkIDs.add(Id.create("123818", Link.class));
		tunnelLinkIDs.add(Id.create("76986", Link.class));
		
		tunnelLinkIDs.add(Id.create("15488", Link.class));
		tunnelLinkIDs.add(Id.create("96568", Link.class));
		tunnelLinkIDs.add(Id.create("15490", Link.class));
		
		noiseParameters.setTunnelLinkIDsSet(tunnelLinkIDs);
		
		// decongestion pricing Berlin settings
		
		final DecongestionConfigGroup decongestionSettings = (DecongestionConfigGroup) controler.getConfig().getModules().get(DecongestionConfigGroup.GROUP_NAME);
		
		if (congestionTollingApproach.toString().equals(CongestionTollingApproach.DecongestionPID.toString())) {
			
			decongestionSettings.setDecongestionApproach(DecongestionApproach.PID);
			decongestionSettings.setKp(kP);
			decongestionSettings.setKi(0.);
			decongestionSettings.setKd(0.);
			
			decongestionSettings.setMsa(true);
			
			decongestionSettings.setRunFinalAnalysis(false);
			decongestionSettings.setWriteLinkInfoCharts(false);
			decongestionSettings.setToleratedAverageDelaySec(30.);

		} else if (congestionTollingApproach.toString().equals(CongestionTollingApproach.DecongestionBangBang.toString())) {

			decongestionSettings.setDecongestionApproach(DecongestionApproach.BangBang);
			decongestionSettings.setInitialToll(0.01);
			decongestionSettings.setTollAdjustment(1.0);
			
			decongestionSettings.setMsa(false);
			
			decongestionSettings.setRunFinalAnalysis(false);
			decongestionSettings.setWriteLinkInfoCharts(false);
			decongestionSettings.setToleratedAverageDelaySec(30.);
			
		} else {
			// for V3, V9 and V10: no additional settings
		}
		
		// CNE Integration
		
		CNEIntegration cne = new CNEIntegration(controler, gt, rgt);
		cne.setCongestionPricing(congestionPricing);
		cne.setNoisePricing(noisePricing);
		cne.setAirPollutionPricing(airPollutionPricing);
		cne.setSigma(sigma);
		cne.setCongestionTollingApproach(congestionTollingApproach);
		cne.setAgentFilter(new BerlinAgentFilter());

		controler = cne.prepareControler();
				
		controler.getConfig().controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		controler.run();
		
		// analysis
		
		PersonTripCongestionNoiseAnalysisRun analysis = new PersonTripCongestionNoiseAnalysisRun(controler.getConfig().controler().getOutputDirectory());
		analysis.run();
		
		String immissionsDir = controler.getConfig().controler().getOutputDirectory() + "/ITERS/it." + controler.getConfig().controler().getLastIteration() + "/immissions/";
		String receiverPointsFile = controler.getConfig().controler().getOutputDirectory() + "/receiverPoints/receiverPoints.csv";
		
		ProcessNoiseImmissions processNoiseImmissions = new ProcessNoiseImmissions(immissionsDir, receiverPointsFile, noiseParameters.getReceiverPointGap());
		processNoiseImmissions.run();
		
		final String[] labels = { "immission", "consideredAgentUnits" , "damages_receiverPoint" };
		final String[] workingDirectories = { controler.getConfig().controler().getOutputDirectory() + "/ITERS/it." + controler.getConfig().controler().getLastIteration() + "/immissions/" , controler.getConfig().controler().getOutputDirectory() + "/ITERS/it." + controler.getConfig().controler().getLastIteration() + "/consideredAgentUnits/" , controler.getConfig().controler().getOutputDirectory() + "/ITERS/it." + controler.getConfig().controler().getLastIteration()  + "/damages_receiverPoint/" };

		MergeNoiseCSVFile merger = new MergeNoiseCSVFile() ;
		merger.setReceiverPointsFile(receiverPointsFile);
		merger.setOutputDirectory(controler.getConfig().controler().getOutputDirectory() + "/ITERS/it." + controler.getConfig().controler().getLastIteration() + "/");
		merger.setTimeBinSize(noiseParameters.getTimeBinSizeNoiseComputation());
		merger.setWorkingDirectory(workingDirectories);
		merger.setLabel(labels);
		merger.run();
		
		// delete unnecessary iterations folder here.
		int firstIt = controler.getConfig().controler().getFirstIteration();
		int lastIt = controler.getConfig().controler().getLastIteration();
		String OUTPUT_DIR = controler.getConfig().controler().getOutputDirectory();
		for (int index =firstIt+1; index <lastIt; index ++){
			String dirToDel = OUTPUT_DIR+"/ITERS/it."+index;
			log.info("Deleting the directory "+dirToDel);
			IOUtils.deleteDirectoryRecursively(new File(dirToDel).toPath());
		}
	}
	
}