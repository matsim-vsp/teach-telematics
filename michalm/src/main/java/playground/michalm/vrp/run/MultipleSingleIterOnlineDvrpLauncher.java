/* *********************************************************************** *
 * project: org.matsim.*
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

package playground.michalm.vrp.run;

import java.io.*;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.matsim.core.gbl.MatsimRandom;

import pl.poznan.put.vrp.dynamic.optimizer.taxi.*;
import pl.poznan.put.vrp.dynamic.optimizer.taxi.TaxiEvaluator.TaxiEvaluation;


/*package*/class MultipleSingleIterOnlineDvrpLauncher
{
    private static final int[] RANDOM_SEEDS = { 463, 467, 479, 487, 491, 499, 503, 509, 521, 523,
            541, 547, 557, 563, 569, 571, 577, 587, 593, 599, 601, 607, 613, 617, 619, 631, 641,
            643, 647, 653 };

    private final SingleIterOnlineDvrpLauncher launcher;
    private final TaxiDelaySpeedupStats delaySpeedupStats;


    /*package*/MultipleSingleIterOnlineDvrpLauncher(String paramFile)
    {
        launcher = new SingleIterOnlineDvrpLauncher(paramFile);
        delaySpeedupStats = new TaxiDelaySpeedupStats();
        launcher.delaySpeedupStats = delaySpeedupStats;
    }


    /*package*/void run(int configIdx, int runs, boolean destinationKnown,
            boolean onlineVehicleTracker, PrintWriter pw, PrintWriter pw2)
    {
        launcher.algorithmConfig = AlgorithmConfig.ALL[configIdx];
        launcher.destinationKnown = destinationKnown;
        launcher.onlineVehicleTracker = onlineVehicleTracker;

        // taxiPickupDriveTime
        // taxiDeliveryDriveTime
        // taxiServiceTime
        // taxiWaitTime
        // taxiOverTime
        // passengerWaitTime

        SummaryStatistics taxiPickupDriveTime = new SummaryStatistics();
        SummaryStatistics taxiDeliveryDriveTime = new SummaryStatistics();
        SummaryStatistics taxiServiceTime = new SummaryStatistics();
        SummaryStatistics taxiCruiseTime = new SummaryStatistics();
        SummaryStatistics taxiWaitTime = new SummaryStatistics();
        SummaryStatistics taxiOverTime = new SummaryStatistics();
        SummaryStatistics passengerWaitTime = new SummaryStatistics();
        SummaryStatistics maxPassengerWaitTime = new SummaryStatistics();

        switch (configIdx) {
            case 0:
            case 1:
            case 3:
            case 4:
            case 9:
            case 10:
            case 15:
            case 16:
                // run as many times as requested
                break;

            default:
                // do not run
                runs = 0;
        }

        for (int i = 0; i < runs; i++) {
            MatsimRandom.reset(RANDOM_SEEDS[i]);
            launcher.go();
            TaxiEvaluation evaluation = (TaxiEvaluation)new TaxiEvaluator()
                    .evaluateVrp(launcher.data.getVrpData());

            taxiPickupDriveTime.addValue(evaluation.getTaxiPickupDriveTime());
            taxiDeliveryDriveTime.addValue(evaluation.getTaxiDeliveryDriveTime());
            taxiServiceTime.addValue(evaluation.getTaxiServiceTime());
            taxiCruiseTime.addValue(evaluation.getTaxiCruiseTime());
            taxiWaitTime.addValue(evaluation.getTaxiWaitTime());
            taxiOverTime.addValue(evaluation.getTaxiOverTime());
            passengerWaitTime.addValue(evaluation.getPassengerWaitTime());
            maxPassengerWaitTime.addValue(evaluation.getMaxPassengerWaitTime());
        }

        pw.println(configIdx + "\t" + TaxiEvaluation.HEADER);

        pw.printf("Mean\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\n",//
                taxiPickupDriveTime.getMean(),//
                taxiDeliveryDriveTime.getMean(),//
                taxiServiceTime.getMean(),//
                taxiCruiseTime.getMean(),//
                taxiWaitTime.getMean(),//
                taxiOverTime.getMean(),//
                passengerWaitTime.getMean(),//
                maxPassengerWaitTime.getMean());
        pw.printf("Min\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n",//
                (int)taxiPickupDriveTime.getMin(),//
                (int)taxiDeliveryDriveTime.getMin(),//
                (int)taxiServiceTime.getMin(),//
                (int)taxiCruiseTime.getMin(),//
                (int)taxiWaitTime.getMin(),//
                (int)taxiOverTime.getMin(),//
                (int)passengerWaitTime.getMin(),//
                (int)passengerWaitTime.getMin());
        pw.printf("Max\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n",//
                (int)taxiPickupDriveTime.getMax(),//
                (int)taxiDeliveryDriveTime.getMax(),//
                (int)taxiServiceTime.getMax(),//
                (int)taxiCruiseTime.getMax(),//
                (int)taxiWaitTime.getMax(),//
                (int)taxiOverTime.getMax(),//
                (int)passengerWaitTime.getMax(),//
                (int)passengerWaitTime.getMax());
        pw.printf("StdDev\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\n",
                taxiPickupDriveTime.getStandardDeviation(),//
                taxiDeliveryDriveTime.getStandardDeviation(),//
                taxiServiceTime.getStandardDeviation(),//
                taxiCruiseTime.getStandardDeviation(),//
                taxiWaitTime.getStandardDeviation(),//
                taxiOverTime.getStandardDeviation(),//
                passengerWaitTime.getStandardDeviation(),//
                passengerWaitTime.getStandardDeviation());

        // the endTime of the simulation??? --- time of last served request

        pw.println();

        if (runs > 0) {
            delaySpeedupStats.printStats(pw2, configIdx + "");
            delaySpeedupStats.clearStats();
        }
    }


    private static void run(int configIdx, int runs, String paramFile, boolean destinationKnown,
            boolean onlineVehicleTracker)
        throws FileNotFoundException
    {
        MultipleSingleIterOnlineDvrpLauncher multiLauncher = new MultipleSingleIterOnlineDvrpLauncher(
                paramFile);

        String txt = (destinationKnown ? "D1" : "D0") + "_" + (onlineVehicleTracker ? "M1" : "M2");

        PrintWriter pw = new PrintWriter(multiLauncher.launcher.dirName + "stats_" + txt + ".out");
        PrintWriter pw2 = new PrintWriter(multiLauncher.launcher.dirName + "timeUpdates_" + txt
                + ".out");

        if (configIdx == -1) {
            for (int i = 0; i < AlgorithmConfig.ALL.length; i++) {
                multiLauncher.run(i, runs, destinationKnown, onlineVehicleTracker, pw, pw2);
            }
        }
        else {
            multiLauncher.run(configIdx, runs, destinationKnown, onlineVehicleTracker, pw, pw2);
        }

        pw.close();
        pw2.close();

    }


    // args: configIdx runs
    public static void main(String... args)
        throws FileNotFoundException
    {
        String paramFile;
        if (args.length == 2) {
            paramFile = null;
        }
        else if (args.length == 3) {
            paramFile = args[2];
        }
        else {
            throw new RuntimeException();
        }

        int configIdx = Integer.valueOf(args[0]);
        if (configIdx < -1 || configIdx >= AlgorithmConfig.ALL.length) {
            throw new RuntimeException();
        }

        int runs = Integer.valueOf(args[1]);
        if (runs < 1 || runs > RANDOM_SEEDS.length) {
            throw new RuntimeException();
        }

        run(configIdx, runs, paramFile, false, false);
        run(configIdx, runs, paramFile, false, true);
        run(configIdx, runs, paramFile, true, false);
        run(configIdx, runs, paramFile, true, true);
    }
}
